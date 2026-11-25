package com.chenyubang.app

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.feature.mistake.P08MistakeActions
import kotlinx.coroutines.Dispatchers
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

@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class MistakeActionsTest {
    private lateinit var database: AppDatabase

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
    fun startReviewUsesCurrentCountInsteadOfDisplayedCount() =
        runComposeUiTest {
            var currentCount = 0
            var queries = 0
            var routed: RouterState? = null
            lateinit var actions: P08MistakeActions
            setContent {
                actions =
                    mistakeActions(
                        router = RouterState(tab = AppTab.Mistake),
                        database = database,
                        onRoute = { routed = it },
                        loadPendingCount = {
                            queries++
                            currentCount
                        },
                    )
            }
            waitForIdle()

            actions.onStartReview(9)
            waitUntil(timeoutMillis = 5_000) { actions.pageMessage != null }

            assertEquals(1, queries)
            assertNull(routed)
            assertEquals("今天没有待复习的错题", actions.pageMessage)

            currentCount = 2
            actions.onStartReview(9)
            waitUntil(timeoutMillis = 5_000) { routed != null }
            waitForIdle()

            assertEquals(2, queries)
            assertEquals(Route.Queue, routed?.current?.route)
            assertNull(actions.pageMessage)
        }

    @Test
    fun startReviewReadFailureShowsMessageAndDoesNotRoute() =
        runComposeUiTest {
            var routed: RouterState? = null
            lateinit var actions: P08MistakeActions
            setContent {
                actions =
                    mistakeActions(
                        router = RouterState(tab = AppTab.Mistake),
                        database = database,
                        onRoute = { routed = it },
                        loadPendingCount = { throw IllegalStateException("read failed") },
                    )
            }
            waitForIdle()

            actions.onStartReview(1)
            waitUntil(timeoutMillis = 5_000) { actions.pageMessage != null }

            assertNull(routed)
            assertEquals("待复习队列读取失败，请重试", actions.pageMessage)
        }
}
