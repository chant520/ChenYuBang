package com.chenyubang.feature.idiom

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.IdiomEntity
import com.chenyubang.core.data.MistakeEntity
import com.chenyubang.core.data.MistakeReviewState
import com.chenyubang.core.data.UserExampleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A5 联动：真实计数渲染、N>0 数字进 P09、N=0 空态与「去错题本」P08 入口互不混用。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P05IdiomDetailA5EntryTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).build()
        runBlocking {
            database.idiomDao().upsertAll(
                listOf(
                    idiom("idiom-anbu", "按部就班"),
                    idiom("idiom-empty", "墨守成规"),
                ),
            )
            // 两道标错 + 一道未标错，均归到按部就班；空成语下不造数据。
            persist("ue-1", "idiom-anbu", isMistake = true, mistakeId = "m-1")
            persist("ue-2", "idiom-anbu", isMistake = true, mistakeId = "m-2")
            persist("ue-3", "idiom-anbu", isMistake = false, mistakeId = null)
        }
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun countOpensP09AndKeepsMistakeBookEntryIndependent() {
        var p09Opens = 0
        var p08Opens = 0
        render("idiom-anbu", onP09 = { p09Opens++ }, onP08 = { p08Opens++ })

        awaitText("3 道题")
        composeTestRule.onNodeWithText("3 道题", substring = true).performScrollTo().performClick()
        assertEquals(1, p09Opens)
        assertEquals(0, p08Opens)

        composeTestRule.onNodeWithText("去错题本").performScrollTo().performClick()
        assertEquals(1, p09Opens)
        assertEquals(1, p08Opens)
    }

    @Test
    fun zeroCountKeepsEmptyState() {
        var p09Opens = 0
        render("idiom-empty", onP09 = { p09Opens++ })

        awaitText("还没在这个成语下录入题")
        composeTestRule.onNodeWithText("0 道题", substring = true).performScrollTo()
        assertEquals(0, p09Opens)
    }

    private fun render(
        idiomId: String,
        onP09: () -> Unit,
        onP08: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            P05IdiomDetailRoute(
                idiomId = idiomId,
                database = database,
                onBack = {},
                onOpenMistakes = onP09,
                onOpenMistakeBook = onP08,
                onOpenExample = {},
            )
        }
    }

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private suspend fun persist(
        id: String,
        idiomId: String,
        isMistake: Boolean,
        mistakeId: String?,
    ) {
        database.userExampleDao().persistCapture(
            userExample =
                UserExampleEntity(
                    id = id,
                    stem = "题干 $id",
                    isMistake = isMistake,
                    createdAt = "2026-11-14T09:20:00Z",
                ),
            idioms = listOf(idiomId),
            mistake =
                mistakeId?.let {
                    MistakeEntity(
                        id = it,
                        userExampleId = id,
                        stem = "题干 $id",
                        optionA = "A",
                        optionB = "B",
                        optionC = "C",
                        optionD = "D",
                        state = MistakeReviewState.PENDING.storedValue,
                        createdAt = "2026-11-14T09:20:00Z",
                    )
                },
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
}
