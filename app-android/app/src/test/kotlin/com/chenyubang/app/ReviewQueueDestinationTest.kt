package com.chenyubang.app

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chenyubang.core.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** P11 ViewModel 生命周期：离开组合 / 换轮释放，且不往宿主 Activity store 累积。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class ReviewQueueDestinationTest {
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
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun injectedStoreIsClearedWhenQueueLeavesComposition() =
        runComposeUiTest {
            val store = ViewModelStore()
            val owner =
                object : ViewModelStoreOwner {
                    override val viewModelStore = store
                }
            val visible = mutableStateOf(true)
            setContent {
                if (visible.value) {
                    ReviewQueueDestination(
                        scopeIdiomId = null,
                        instance = 1L,
                        database = database,
                        actions = ReviewQueueActions({}, {}, {}),
                        storeOwner = owner,
                    )
                }
            }
            waitForIdle()
            assertTrue(store.keys().isNotEmpty())
            runOnIdle { visible.value = false }
            waitForIdle()
            assertTrue(store.keys().isEmpty())
        }

    @Test
    fun reviewRoundsDoNotAccumulateViewModelsInHostStore() =
        runComposeUiTest {
            val hostStore = ViewModelStore()
            val hostOwner =
                object : ViewModelStoreOwner {
                    override val viewModelStore = hostStore
                }
            val instance = mutableLongStateOf(1L)
            setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides hostOwner) {
                    ReviewQueueDestination(
                        scopeIdiomId = null,
                        instance = instance.value,
                        database = database,
                        actions = ReviewQueueActions({}, {}, {}),
                    )
                }
            }
            waitForIdle()
            assertTrue(hostStore.keys().isEmpty())
            runOnIdle { instance.value++ }
            waitForIdle()
            assertTrue(hostStore.keys().isEmpty())
        }
}
