package com.chenyubang.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 阶段6包1 E2 / A7 只读聚合查询：三计数口径 + favoriteIdioms 同源过滤 + statesForIds 批量投影。
 * 全部走真实内存库，不建表 / 不加字段 / 不迁移（AppDatabase version 仍 3）。
 */
@Suppress("MagicNumber")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdiomStateCountTest {
    private lateinit var db: AppDatabase
    private lateinit var states: IdiomStateDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        states = db.idiomStateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun emptyStateAllCountsAreZero() =
        runBlocking {
            assertEquals(0, states.countLearned())
            assertEquals(0, states.countMastered())
            assertEquals(0, states.countFavorite())
            assertEquals(emptyList<FavoriteIdiom>(), states.favoriteIdioms())
        }

    @Test
    fun threeCountsFollowExactStoredValueContract() =
        runBlocking {
            seedIdioms()
            // 未学：落行但既不计已学也不计已掌握。
            states.setStatus("idiom-unlearned", IdiomLearningStatus.UNLEARNED)
            // 学过：计已学，不计已掌握。
            states.setStatus("idiom-learned", IdiomLearningStatus.LEARNED)
            // 已掌握：同时计已学与已掌握。
            states.setStatus("idiom-mastered", IdiomLearningStatus.MASTERED)
            // 只收藏、不设状态：计收藏，不计已学。
            states.setFavorite("idiom-fav-only", true)

            assertEquals(2, states.countLearned())
            assertEquals(1, states.countMastered())
            assertEquals(1, states.countFavorite())
        }

    @Test
    fun favoriteCountAndListShareSameFilter() =
        runBlocking {
            seedIdioms()
            states.setFavorite("idiom-a", true)
            states.setStatus("idiom-a", IdiomLearningStatus.MASTERED)
            states.setStatus("idiom-b", IdiomLearningStatus.LEARNED)
            states.setFavorite("idiom-c", true)

            assertEquals(2, states.countFavorite())
            val rows = states.favoriteIdioms()
            assertEquals(listOf("idiom-a", "idiom-c"), rows.map { it.idiomId }.sorted())
            assertTrue(rows.none { it.idiomId == "idiom-b" })
            assertEquals("已掌握", rows.first { it.idiomId == "idiom-a" }.status)
        }

    @Test
    fun unfavoriteRemovesFromCountAndList() =
        runBlocking {
            seedIdioms()
            states.setFavorite("idiom-a", true)
            states.setFavorite("idiom-b", true)
            assertEquals(2, states.countFavorite())

            states.setFavorite("idiom-a", false)
            assertEquals(1, states.countFavorite())
            assertEquals(listOf("idiom-b"), states.favoriteIdioms().map { it.idiomId })
        }

    @Test
    fun statesForIdsReturnsOnlyStoredRows() =
        runBlocking {
            seedIdioms()
            states.setStatus("idiom-a", IdiomLearningStatus.LEARNED)
            states.setFavorite("idiom-c", true)

            val rows = states.statesForIds(listOf("idiom-a", "idiom-b", "idiom-c"))
            // idiom-b 缺行不返回，由上层投影补未学。
            assertEquals(listOf("idiom-a", "idiom-c"), rows.map { it.idiomId }.sorted())
            assertEquals("学过", rows.first { it.idiomId == "idiom-a" }.status)
            assertEquals(true, rows.first { it.idiomId == "idiom-c" }.favorite)
        }

    private suspend fun seedIdioms() {
        db.idiomDao().upsertAll(
            listOf(
                "idiom-a",
                "idiom-b",
                "idiom-c",
                "idiom-unlearned",
                "idiom-learned",
                "idiom-mastered",
                "idiom-fav-only",
            ).map { id -> idiom(id) },
        )
    }

    private fun idiom(id: String) =
        IdiomEntity(
            id = id,
            word = "成语$id",
            pinyin = "cheng yu",
            initials = "cy",
            sense = "测试释义",
            color = "red",
            frequency = 1,
        )
}
