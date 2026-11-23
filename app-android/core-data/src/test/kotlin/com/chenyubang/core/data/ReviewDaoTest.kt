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
 * 阶段 3 状态机钉住（开发规范 v1.7 §8.2）：
 * 队列过滤 / createdAt 倒序 / 判分回写 / state 不变四类，外加单成语命中任一、事实源回退与无法比对。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ReviewDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.reviewDao()
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun queueFiltersOnlyPendingState() =
        runBlocking {
            val ids = dao.queue().map { it.mistakeId }
            // m-4 最新但已掌握，必不入队；未标错的 ue 无 mistake，亦不出现。
            assertEquals(listOf("m-2", "m-1", "m-5", "m-3"), ids)
            assertTrue("已掌握错题混入队列", !ids.contains("m-4"))
        }

    @Test
    fun queueSortsByCreatedAtDescending() =
        runBlocking {
            val rows = dao.queue()
            assertEquals(listOf("m-2", "m-1", "m-5", "m-3"), rows.map { it.mistakeId })
            assertEquals("事实源题干1", rows.first { it.mistakeId == "m-1" }.stem)
        }

    @Test
    fun recordWritesLogAndBumpsCountersInOneTransaction() =
        runBlocking {
            val at = "2026-11-23T10:00:00Z"
            dao.record("m-1", ReviewScoring.RESULT_CORRECT, at)
            dao.record("m-1", ReviewScoring.RESULT_WRONG, at)

            assertEquals(2, count("SELECT COUNT(*) FROM review_log WHERE mistakeId = 'm-1'"))
            val results = scalarList("SELECT result FROM review_log WHERE mistakeId = 'm-1' ORDER BY rowid")
            assertEquals(listOf("对", "错"), results)
            assertEquals(2, scalar("SELECT reviewCount FROM mistake WHERE id = 'm-1'").toInt())
            assertEquals(at, scalar("SELECT lastReviewedAt FROM mistake WHERE id = 'm-1'"))
        }

    @Test
    fun recordNeverChangesMistakeState() =
        runBlocking {
            dao.record("m-1", ReviewScoring.RESULT_CORRECT, "2026-11-23T10:00:00Z")
            assertEquals("待复习", dao.stateOf("m-1"))
        }

    @Test
    fun singleIdiomQueueHitsAnyLinkedIdiom() =
        runBlocking {
            // m-1 同时挂 anbu 与 biechu：两边的队列都要出现，moshou 只出 m-2。
            assertEquals(listOf("m-1", "m-5", "m-3"), dao.queue("idiom-anbu").map { it.mistakeId })
            assertEquals(listOf("m-1"), dao.queue("idiom-biechu").map { it.mistakeId })
            assertEquals(listOf("m-2"), dao.queue("idiom-moshou").map { it.mistakeId })
        }

    @Test
    fun queueReadsFactSourceAndFallsBackToSnapshot() =
        runBlocking {
            val rows = dao.queue()
            val fact = rows.first { it.mistakeId == "m-1" }
            assertEquals("事实源题干1", fact.stem)
            assertEquals(listOf("甲", "乙", "丙", "丁"), fact.options)
            assertEquals("B", fact.myAnswer)
            // m-5 历史无 userExampleId：按 mistake 快照展示、照常入队。
            val legacy = rows.first { it.mistakeId == "m-5" }
            assertEquals("历史快照题干", legacy.stem)
            assertEquals("A", legacy.myAnswer)
        }

    @Test
    fun scoringMapsDifferentToCorrectAndSameToWrong() {
        val options = listOf("甲", "乙", "丙", "丁")
        val different = ReviewScoring.judge("B", options, 2)
        assertEquals(ReviewScoring.RESULT_CORRECT, different.result)
        assertEquals("与首次作答不同：首次 B，本次 C", different.message)
        val same = ReviewScoring.judge("B", options, 1)
        assertEquals(ReviewScoring.RESULT_WRONG, same.result)
        assertEquals("与首次作答一致（B）", same.message)
    }

    @Test
    fun missingFirstAnswerIsNotComparable() {
        val options = listOf("甲", "乙", "丙", "丁")
        val judged = ReviewScoring.judge(null, options, 0)
        assertTrue(!judged.comparable)
        assertEquals(null, judged.result)
        assertEquals("本题未记录你的首次作答，无法比对（本次选了 A）", judged.message)
        assertTrue(!ReviewScoring.judge("B", listOf(null, null, null, null), 0).comparable)
    }

    private suspend fun seed() {
        db.idiomDao().upsertAll(
            listOf(
                idiom("idiom-anbu", "按部就班"),
                idiom("idiom-biechu", "别出心裁"),
                idiom("idiom-moshou", "墨守成规"),
            ),
        )
        // 快照与事实源故意不同，用来钉住「正文读 user_example 事实源」。
        db.userExampleDao().persistCapture(
            userExample =
                example("ue-1", "事实源题干1", "2026-11-14T09:20:00Z", myAnswer = "B", isMistake = true),
            idioms = listOf("idiom-anbu", "idiom-biechu"),
            mistake =
                mistake("m-1", "ue-1", "待复习", "2026-11-14T09:20:00Z")
                    .copy(stem = "快照题干1", myAnswer = "A"),
        )
        db.userExampleDao().persistCapture(
            userExample =
                example("ue-2", "事实源题干2", "2026-11-20T14:05:00Z", myAnswer = "C", isMistake = true),
            idioms = listOf("idiom-moshou"),
            mistake =
                mistake("m-2", "ue-2", "待复习", "2026-11-20T14:05:00Z")
                    .copy(stem = "快照题干2", myAnswer = "D"),
        )
        db.userExampleDao().persistCapture(
            userExample =
                example("ue-3", "事实源题干3", "2026-11-10T08:00:00Z", myAnswer = null, isMistake = true),
            idioms = listOf("idiom-anbu"),
            mistake =
                mistake("m-3", "ue-3", "待复习", "2026-11-10T08:00:00Z")
                    .copy(stem = "快照题干3"),
        )
        db.userExampleDao().persistCapture(
            userExample = example("ue-4", "已掌握的题", "2026-11-22T08:00:00Z", myAnswer = "A", isMistake = true),
            idioms = listOf("idiom-anbu"),
            mistake =
                mistake("m-4", "ue-4", "已掌握", "2026-11-22T08:00:00Z")
                    .copy(stem = "已掌握题干", myAnswer = "B"),
        )
        // 历史错题：无 userExampleId，快照照常入队。
        db.mistakeDao().upsert(
            mistake("m-5", null, "待复习", "2026-11-12T08:00:00Z")
                .copy(stem = "历史快照题干", myAnswer = "A"),
        )
        db.userExampleDao().insertMistakeIdioms(listOf(MistakeIdiomEntity("m-5", "idiom-anbu")))
    }

    private fun idiom(
        id: String,
        word: String,
    ) = IdiomEntity(
        id = id,
        word = word,
        pinyin = "test",
        initials = "t",
        sense = "测试释义。",
        color = "emerald",
        frequency = 5,
    )

    private fun example(
        id: String,
        stem: String,
        createdAt: String,
        myAnswer: String?,
        isMistake: Boolean,
    ) = UserExampleEntity(
        id = id,
        stem = stem,
        optionA = "甲",
        optionB = "乙",
        optionC = "丙",
        optionD = "丁",
        myAnswer = myAnswer,
        isMistake = isMistake,
        createdAt = createdAt,
    )

    private fun mistake(
        id: String,
        userExampleId: String?,
        state: String,
        createdAt: String,
    ) = MistakeEntity(
        id = id,
        userExampleId = userExampleId,
        stem = "快照题干 $id",
        optionA = "甲",
        optionB = "乙",
        optionC = "丙",
        optionD = "丁",
        state = state,
        createdAt = createdAt,
    )

    private fun count(sql: String): Int =
        db.openHelper.readableDatabase.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun scalar(sql: String): String =
        db.openHelper.readableDatabase.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

    private fun scalarList(sql: String): List<String> =
        db.openHelper.readableDatabase.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
}
