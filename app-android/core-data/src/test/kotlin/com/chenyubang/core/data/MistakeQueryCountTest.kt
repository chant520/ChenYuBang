package com.chenyubang.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A5 计数口径：按 user_example_idiom 统计该成语下全部录入题，含未标错，不看 mistake。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MistakeQueryCountTest {
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
    fun countsMarkedAndUnmarkedExamplesUnderSameIdiom() =
        runBlocking {
            // 按部就班：2 道标错 + 1 道未标错，全部计入 A5。
            assertEquals(3, dao.countUserExamplesByIdiom("idiom-anbu"))
            assertEquals(1, dao.countUserExamplesByIdiom("idiom-moshou"))
            assertEquals(0, dao.countUserExamplesByIdiom("idiom-unknown"))
        }

    @Test
    fun countsSharedExampleInEveryAttributedIdiom() =
        runBlocking {
            // ue-2 同属两个成语，两边各计一次；别出心裁只有这一道。
            assertEquals(1, dao.countUserExamplesByIdiom("idiom-biechu"))
            assertEquals(3, dao.countUserExamplesByIdiom("idiom-anbu"))
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
            userExample = example("ue-1", isMistake = true),
            idioms = listOf("idiom-anbu"),
            mistake = mistake("m-1", "ue-1"),
        )
        db.userExampleDao().persistCapture(
            userExample = example("ue-2", isMistake = true),
            idioms = listOf("idiom-anbu", "idiom-biechu"),
            mistake = mistake("m-2", "ue-2"),
        )
        db.userExampleDao().persistCapture(
            userExample = example("ue-3", isMistake = false),
            idioms = listOf("idiom-anbu"),
        )
        db.userExampleDao().persistCapture(
            userExample = example("ue-4", isMistake = true),
            idioms = listOf("idiom-moshou"),
            mistake = mistake("m-3", "ue-4"),
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
        isMistake: Boolean,
    ) = UserExampleEntity(
        id = id,
        stem = "题干 $id",
        isMistake = isMistake,
        createdAt = "2026-11-14T09:20:00Z",
    )

    private fun mistake(
        id: String,
        userExampleId: String,
    ) = MistakeEntity(
        id = id,
        userExampleId = userExampleId,
        stem = "题干 $id",
        optionA = "A",
        optionB = "B",
        optionC = "C",
        optionD = "D",
        state = MistakeReviewState.PENDING.storedValue,
        createdAt = "2026-11-14T09:20:00Z",
    )
}
