package com.chenyubang.feature.idiom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.IdiomEntity
import com.chenyubang.core.data.MistakeEntity
import com.chenyubang.core.data.MistakeIdiomEntity
import com.chenyubang.core.data.MistakeReviewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P05 入口联动：revision 重查展示数，点击再查当前数，已掌握不进入单成语队列。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P05IdiomDetailReviewEntryTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private val idiomId = "idiom-anbu"

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
                    IdiomEntity(
                        id = idiomId,
                        word = "按部就班",
                        pinyin = "àn bù jiù bān",
                        initials = "abjb",
                        sense = "依照一定条理办事",
                        color = "贬义",
                        frequency = 5,
                    ),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun revisionRefreshesEntranceAndClickUsesLatestPendingCount() {
        addMistake("m-1")
        var revision by mutableLongStateOf(0)
        var started: Int? = null
        composeTestRule.setContent {
            P05IdiomDetailRoute(
                idiomId = idiomId,
                database = database,
                onBack = {},
                onOpenMistakes = {},
                onOpenExample = {},
                onReviewIdiom = { started = it },
                revision = revision,
            )
        }
        awaitText("复习这个成语")
        composeTestRule.onAllNodesWithText("复习这个成语")[0].performScrollTo().assertIsEnabled()

        addMistake("m-2")
        composeTestRule.runOnIdle { revision++ }
        awaitText("复习这个成语")
        composeTestRule.onAllNodesWithText("复习这个成语")[0].performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { started != null }

        assertEquals(2, started)
    }

    @Test
    fun clickAfterMasteryDoesNotReusePreviouslyVisibleCount() {
        addMistake("m-1")
        var started: Int? = null
        composeTestRule.setContent {
            P05IdiomDetailRoute(
                idiomId = idiomId,
                database = database,
                onBack = {},
                onOpenMistakes = {},
                onOpenExample = {},
                onReviewIdiom = { started = it },
            )
        }
        awaitText("复习这个成语")
        composeTestRule.onAllNodesWithText("复习这个成语")[0].performScrollTo().assertIsEnabled()
        runBlocking { database.reviewDao().setState("m-1", MistakeReviewState.MASTERED) }

        composeTestRule.onAllNodesWithText("复习这个成语")[0].performClick()
        awaitText("这个成语下还没有待复习的错题")

        assertNull(started)
    }

    /** 稿面 v2.4 双入口：标签区上方与页尾按钮都可点，且共用同一复习路由回调。 */
    @Test
    fun bothReviewEntriesAreClickableAndStartReview() {
        addMistake("m-1")
        var starts = 0
        composeTestRule.setContent {
            P05IdiomDetailRoute(
                idiomId = idiomId,
                database = database,
                onBack = {},
                onOpenMistakes = {},
                onOpenExample = {},
                onReviewIdiom = { starts++ },
            )
        }
        awaitText("复习这个成语")
        val entries = composeTestRule.onAllNodesWithText("复习这个成语")
        entries.assertCountEquals(2)
        entries[0].performScrollTo().performClick()
        entries[1].performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { starts == 2 }

        assertEquals(2, starts)
    }

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun addMistake(id: String) {
        runBlocking {
            database.mistakeDao().upsert(
                MistakeEntity(
                    id = id,
                    stem = "题干 $id",
                    optionA = "甲",
                    optionB = "乙",
                    optionC = "丙",
                    optionD = "丁",
                    state = MistakeReviewState.PENDING.storedValue,
                    createdAt = "2026-11-22T00:00:00Z",
                ),
            )
            database.userExampleDao().insertMistakeIdioms(listOf(MistakeIdiomEntity(id, idiomId)))
        }
    }
}
