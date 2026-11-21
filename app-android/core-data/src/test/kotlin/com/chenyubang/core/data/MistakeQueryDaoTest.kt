package com.chenyubang.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
 * 工作包 2.6 数据口径：P08 总览 / 按成语分组 / 时间列表，P09 成语下全部录入题。
 *
 * fixtures 全在内存库现造，不引题库内容：
 * - 按部就班：2 道标错（一题同时归属别出心裁）+ 1 道未标错录入题；
 * - 别出心裁：1 道标错（与按部就班共用同一题）；
 * - 墨守成规：1 道标错。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MistakeQueryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MistakeQueryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.mistakeQueryDao()
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun overviewCountsAllMistakesAndOnlyPendingState() =
        runBlocking {
            val overview = dao.overview().first()
            assertEquals(3, overview.total)
            assertEquals(2, overview.pending)
        }

    @Test
    fun groupsCountOnlyMarkedMistakesAndSortByCountThenWord() =
        runBlocking {
            val groups = dao.groupsByIdiom().first()
            assertEquals(listOf("按部就班" to 2, "别出心裁" to 1, "墨守成规" to 1), groups.map { it.word to it.mistakeCount })
            // 未标错的 ue-3 只挂在 user_example_idiom 上，绝不能混进分组计数。
            assertEquals("idiom-anbu", groups.first().idiomId)
        }

    @Test
    fun mistakesByTimeSortsDescendingAndCarriesIdioms() =
        runBlocking {
            val rows = dao.mistakesByTime().first()
            assertEquals(listOf("m-1", "m-2", "m-3"), rows.map { it.mistake.id })
            assertEquals(listOf("按部就班", "别出心裁"), rows[1].idioms.map { it.word })
            assertEquals(listOf("望文生义"), rows.first().mistake.tagNames)
        }

    @Test
    fun userExamplesByIdiomKeepsUnmarkedExamplesAndMistakeState() =
        runBlocking {
            val rows = dao.userExamplesByIdiom("idiom-anbu").first()
            assertEquals(listOf("ue-1", "ue-2", "ue-3"), rows.map { it.userExample.id })
            // 标错的按错题展示：待复习 / 已掌握 + 错因标签。
            assertEquals(listOf("待复习"), rows[0].mistakes.map { it.state })
            assertEquals(listOf("对象误用"), rows[1].mistakes.single().tagNames)
            // 核心语义：未标错的录入题也在 P09 列表里，只是没有错题标记。
            assertTrue(rows[2].mistakes.isEmpty())
        }

    @Test
    fun userExamplesByIdiomDoesNotLeakOtherIdiomsAndSharesOneQuestion() =
        runBlocking {
            val other = dao.userExamplesByIdiom("idiom-moshou").first()
            assertEquals(listOf("ue-4"), other.map { it.userExample.id })
            // 一题多成语：ue-2 同时归属按部就班与别出心裁，两边的 P09 都要出现。
            val shared = dao.userExamplesByIdiom("idiom-biechu").first()
            assertEquals(listOf("ue-2"), shared.map { it.userExample.id })
        }

    private suspend fun seed() {
        db.idiomDao().upsertAll(
            listOf(
                idiom("idiom-anbu", "按部就班"),
                idiom("idiom-biechu", "别出心裁"),
                idiom("idiom-moshou", "墨守成规"),
            ),
        )
        db.userExampleDao().persistCapture(
            userExample = example("ue-1", "填入最恰当的一项。", "2026-11-14T09:20:00Z", examType = "国考", isMistake = true),
            idioms = listOf("idiom-anbu"),
            mistake =
                mistake("m-1", "ue-1", "待复习", "2026-11-14T09:20:00Z", "国考")
                    .copy(tagNames = listOf("望文生义")),
        )
        db.userExampleDao().persistCapture(
            userExample = example("ue-2", "句中成语使用恰当的是。", "2026-11-09T14:05:00Z", examType = "省考", isMistake = true),
            idioms = listOf("idiom-anbu", "idiom-biechu"),
            mistake =
                mistake("m-2", "ue-2", "已掌握", "2026-11-09T14:05:00Z", "省考")
                    .copy(tagNames = listOf("对象误用")),
        )
        db.userExampleDao().persistCapture(
            userExample = example("ue-3", "下面句子中加点成语使用不正确的是。", "2026-10-28T08:00:00Z", isMistake = false),
            idioms = listOf("idiom-anbu"),
        )
        db.userExampleDao().persistCapture(
            userExample = example("ue-4", "依次填入划横线部分最恰当的一项是。", "2026-11-01T10:30:00Z", isMistake = true),
            idioms = listOf("idiom-moshou"),
            mistake =
                mistake("m-3", "ue-4", "待复习", "2026-11-01T10:30:00Z", null)
                    .copy(tagNames = listOf("望文生义", "对象误用")),
        )
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
        examType: String? = null,
        isMistake: Boolean,
    ) = UserExampleEntity(
        id = id,
        stem = stem,
        examType = examType,
        isMistake = isMistake,
        createdAt = createdAt,
    )

    private fun mistake(
        id: String,
        userExampleId: String,
        state: String,
        createdAt: String,
        examType: String?,
    ) = MistakeEntity(
        id = id,
        userExampleId = userExampleId,
        stem = "题干 $id",
        optionA = "A",
        optionB = "B",
        optionC = "C",
        optionD = "D",
        examType = examType,
        state = state,
        createdAt = createdAt,
    )
}
