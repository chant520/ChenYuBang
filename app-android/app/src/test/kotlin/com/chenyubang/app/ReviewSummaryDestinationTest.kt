package com.chenyubang.app

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.MistakeEntity
import com.chenyubang.core.data.MistakeIdiomEntity
import com.chenyubang.core.data.MistakeReviewState
import com.chenyubang.feature.review.ReviewSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** P12 再复习：展示计数随 revision 更新，点击按当前范围重查，0 题不重开队列。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class ReviewSummaryDestinationTest {
    private lateinit var database: AppDatabase
    private val summary =
        ReviewSummary(
            questionCount = 1,
            answerCount = 1,
            correctCount = 1,
            idioms = emptyList(),
        )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        shadowOf(context.packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = context.packageName
                name = ComponentActivity::class.java.name
                exported = true
                applicationInfo = context.applicationInfo
            },
        )
        Dispatchers.setMain(Dispatchers.Unconfined)
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).build()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun singleScopeCountRefreshesWithRevisionAndAgainUsesCurrentCount() =
        runComposeUiTest {
            addMistake("m-1", "idiom-a")
            addMistake("m-2", "idiom-b")
            var revision by mutableLongStateOf(0)
            var started: Int? = null
            var queries = 0
            setContent {
                ReviewSummaryDestination(
                    summary = summary,
                    scopeIdiomId = "idiom-a",
                    database = database,
                    revision = revision,
                    actions = ReviewSummaryActions({}, {}, { started = it }, {}),
                    loadPendingCount = {
                        queries++
                        database.reviewDao().pendingCount(it)
                    },
                )
            }
            addMistake("m-3", "idiom-a")
            runOnIdle { revision++ }
            waitForIdle()
            onNodeWithText("再复习一轮").performClick()
            waitUntil(timeoutMillis = 5_000) { started != null && queries >= 3 }

            assertEquals(2, started)
        }

    @Test
    fun againAfterMasteryReturnsZeroAndDoesNotRestart() =
        runComposeUiTest {
            addMistake("m-1", null)
            var started: Int? = null
            var queries = 0
            val completedQueries = mutableListOf<Int>()
            setContent {
                ReviewSummaryDestination(
                    summary = summary,
                    scopeIdiomId = null,
                    database = database,
                    actions = ReviewSummaryActions({}, {}, { started = it }, {}),
                    loadPendingCount = {
                        queries++
                        database.reviewDao().pendingCount(it).also { count -> completedQueries += count }
                    },
                )
            }
            waitUntil(timeoutMillis = 5_000) { completedQueries.isNotEmpty() }
            runBlocking { database.reviewDao().setState("m-1", MistakeReviewState.MASTERED) }
            onNodeWithText("再复习一轮").performClick()
            waitUntil(timeoutMillis = 5_000) { completedQueries.size >= 2 }

            assertEquals(listOf(1, 0), completedQueries)
            assertNull(started)
            onNodeWithText("当前范围已没有待复习的错题").assertExists()
        }

    @Test
    fun queueReadFailureShowsMessageAndDoesNotRestart() =
        runComposeUiTest {
            var fail = false
            var started: Int? = null
            var queries = 0
            setContent {
                ReviewSummaryDestination(
                    summary = summary,
                    scopeIdiomId = null,
                    database = database,
                    actions = ReviewSummaryActions({}, {}, { started = it }, {}),
                    loadPendingCount = {
                        queries++
                        if (fail) error("read failed")
                        1
                    },
                )
            }
            waitUntil(timeoutMillis = 5_000) { queries == 1 }
            fail = true
            onNodeWithText("再复习一轮").performClick()
            waitUntil(timeoutMillis = 5_000) { queries == 2 }

            assertNull(started)
            onNodeWithText("待复习队列读取失败，请重试").assertExists()
        }

    private fun addMistake(
        id: String,
        idiomId: String?,
    ) {
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
            if (idiomId != null) {
                database.userExampleDao().insertMistakeIdioms(listOf(MistakeIdiomEntity(id, idiomId)))
            }
        }
    }
}
