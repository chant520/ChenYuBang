package com.chenyubang.core.data

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * idiom_fts（Room FTS4）搜索四输入 + 重建 + 空结果 + 用户表不受影响（规范 §4.2 / §4.6）。
 * JVM + Robolectric 提供 Android SQLite 运行时，本机与 CI 的 `test` 任务均可执行。
 * 样例数据对齐 shared/fixtures/idioms.sample.json。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class IdiomDaoFtsTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: IdiomDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.idiomDao()
        runBlocking {
            dao.upsertAll(sampleIdioms())
            dao.rebuildIdiomFts()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun searchFullIdiom() =
        runBlocking {
            val result = dao.search("按部就班")
            assertEquals(1, result.size)
            assertEquals("按部就班", result[0].word)
        }

    @Test
    fun searchSingleChar() =
        runBlocking {
            val result = dao.search("按")
            assertTrue(result.any { it.word == "按部就班" })
        }

    @Test
    fun singleCharFtsWorksWithoutLike() =
        runBlocking {
            assertEquals("按部就班", dao.hanFtsSearch("\"按*\"", "\"按\"", IdiomDao.SEARCH_LIMIT).single().word)
        }

    @Test
    fun searchPinyinPrefix() =
        runBlocking {
            assertEquals("按部就班", dao.search("anbu").single().word)
        }

    @Test
    fun searchSpecialCharacters() =
        runBlocking {
            for (query in listOf("\u0000", "a\u0000b", "\"", "*", "OR", "a\"b")) {
                assertTrue(dao.search(query).isEmpty())
            }
        }

    @Test
    fun rebuildRollsBackWithContentTransaction() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                db.withTransaction {
                    dao.upsertAll(listOf(sampleIdioms().first().copy(word = "临时词条")))
                    dao.rebuildIdiomFts()
                    error("rollback")
                }
            }
        }
        runBlocking {
            assertEquals("按部就班", dao.search("按部就班").single().word)
            assertTrue(dao.search("临时词条").isEmpty())
        }
    }

    @Test
    fun searchFullPinyin() =
        runBlocking {
            val result = dao.search("anbujiuban")
            assertEquals(1, result.size)
            assertEquals("按部就班", result[0].word)
        }

    @Test
    fun searchFullPinyinWithSpaces() =
        runBlocking {
            val result = dao.search("an bu jiu ban")
            assertEquals(1, result.size)
            assertEquals("按部就班", result[0].word)
        }

    @Test
    fun searchInitials() =
        runBlocking {
            assertEquals("按部就班", dao.search("abjb").single().word)
            assertEquals("别出心裁", dao.search("bcxc").single().word)
        }

    @Test
    fun searchInitialsPrefix() =
        runBlocking {
            val result = dao.search("ab")
            assertTrue(result.any { it.word == "按部就班" })
        }

    @Test
    fun searchReturnsEmptyForNoMatch() =
        runBlocking {
            assertTrue(dao.search("zzzz").isEmpty())
            assertTrue(dao.search("").isEmpty())
            assertTrue(dao.search("不存在").isEmpty())
        }

    @Test
    fun rebuildPopulatesFts() {
        assertEquals(3, count("idiom_fts"))
        assertEquals(3, count("idiom"))
    }

    @Test
    fun rebuildIsIdempotent() =
        runBlocking {
            dao.rebuildIdiomFts()
            assertEquals(3, count("idiom_fts"))
            assertEquals("按部就班", dao.search("abjb").single().word)
        }

    @Test
    fun rebuildLeavesUserTablesUntouched() {
        val writable = db.openHelper.writableDatabase
        writable.execSQL("INSERT INTO settings(key, value) VALUES('theme', 'light')")
        writable.execSQL(
            "INSERT INTO idiom_state(idiomId, status, favorite) " +
                "VALUES('11111111-1111-4111-8111-111111111111', '学过', 0)",
        )
        runBlocking {
            db.mistakeDao().upsert(
                MistakeEntity(
                    id = "m1",
                    stem = "s",
                    optionA = "a",
                    optionB = "b",
                    optionC = "c",
                    optionD = "d",
                    tagNames = listOf("对象误用"),
                    state = "待复习",
                    createdAt = "2026-01-01T00:00:00Z",
                    reviewCount = 2,
                ),
            )
        }
        val before = runBlocking { db.mistakeDao().byId("m1") }
        runBlocking { dao.rebuildIdiomFts() }
        assertEquals(before, runBlocking { db.mistakeDao().byId("m1") })
        assertEquals(1, count("settings"))
        assertEquals(1, count("idiom_state"))
        assertEquals(1, count("mistake"))
        assertEquals(3, count("idiom_fts"))
    }

    private fun count(table: String): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun sampleIdioms() =
        listOf(
            IdiomEntity(
                id = "11111111-1111-4111-8111-111111111111",
                word = "按部就班",
                pinyin = "an bu jiu ban",
                initials = "abjb",
                sense = "按：依照；部、班：门类与次序。原指安排事务依照一定条理，后多指照老规矩办事，缺乏变通。",
                color = "贬义",
                frequency = 5,
            ),
            IdiomEntity(
                id = "22222222-2222-4222-8222-222222222222",
                word = "别出心裁",
                pinyin = "bie chu xin cai",
                initials = "bcxc",
                sense = "心裁：出于自心的创造与裁断。指想出的办法独创一格，与众不同。",
                color = "褒义",
                frequency = 4,
            ),
            IdiomEntity(
                id = "33333333-3333-4333-8333-333333333333",
                word = "墨守成规",
                pinyin = "mo shou cheng gui",
                initials = "mscg",
                sense = "墨守：战国时墨翟善于守城，后指固执旧法；成规：既定的规则。指固执旧法，一成不变。",
                color = "贬义",
                frequency = 4,
            ),
        )
}
