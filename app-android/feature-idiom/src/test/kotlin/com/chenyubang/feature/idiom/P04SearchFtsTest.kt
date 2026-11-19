package com.chenyubang.feature.idiom

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.IdiomDao
import com.chenyubang.core.data.IdiomEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P04 接的是 core-data 已有 FTS 只读查询；这里在 feature 模块实测四种输入命中，
 * 并把「查无结果」映射到页面空态判定，防止只靠上层纯函数测试掩盖 FTS 路径失效。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class P04SearchFtsTest {
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
    fun fullIdiomAndSingleCharHit() =
        runBlocking {
            assertEquals("按部就班", dao.search("按部就班").single().word)
            assertTrue(dao.search("按").any { it.word == "按部就班" })
        }

    @Test
    fun fullPinyinAndInitialsHit() =
        runBlocking {
            assertEquals("按部就班", dao.search("anbujiuban").single().word)
            assertEquals("按部就班", dao.search("abjb").single().word)
            assertEquals("别出心裁", dao.search("bcxc").single().word)
        }

    @Test
    fun noMatchMapsToExplicitEmptyState() =
        runBlocking {
            val rows = dao.search("zzzz")
            assertTrue(rows.isEmpty())
            assertTrue(IdiomSearchUi.showNoResult("zzzz", IdiomSearchUi.from(rows)))
            assertTrue(dao.search("").isEmpty())
            assertFalse(IdiomSearchUi.showNoResult("", IdiomSearchUi.from(dao.search(""))))
        }

    private fun sampleIdioms() =
        listOf(
            IdiomEntity(
                id = "11111111-1111-4111-8111-111111111111",
                word = "按部就班",
                pinyin = "an bu jiu ban",
                initials = "abjb",
                sense = "原指安排事务依照一定条理。后多指照老规矩办事。",
                color = "贬义",
                frequency = 5,
            ),
            IdiomEntity(
                id = "22222222-2222-4222-8222-222222222222",
                word = "别出心裁",
                pinyin = "bie chu xin cai",
                initials = "bcxc",
                sense = "想出的办法独创一格。",
                color = "褒义",
                frequency = 4,
            ),
        )
}
