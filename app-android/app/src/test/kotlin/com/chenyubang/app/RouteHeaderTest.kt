package com.chenyubang.app

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import com.chenyubang.core.ui.ChenYuBangTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class RouteHeaderTest {
    @Before
    fun registerComposeActivity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        shadowOf(context.packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = context.packageName
                name = ComponentActivity::class.java.name
                exported = true
                applicationInfo = context.applicationInfo
            },
        )
    }

    @Test
    fun secondaryPageUsesAccessibleBackIconWithoutTextButton() =
        runComposeUiTest {
            var backCount = 0
            setContent {
                ChenYuBangTheme {
                    RouteHeader(router = RouterState().push(Route.Scope), back = { backCount++ })
                }
            }

            onNodeWithContentDescription("返回").assertHasClickAction().performClick()
            onNodeWithText("返回").assertDoesNotExist()
            onNodeWithText("考试范围设置").assertExists()
            assertEquals(1, backCount)
        }

    @Test
    fun rootPageKeepsSingleTitleAndSearchAction() =
        runComposeUiTest {
            var searchCount = 0
            setContent {
                ChenYuBangTheme {
                    RouteHeader(router = RouterState(), back = {}, onSearch = { searchCount++ })
                }
            }

            onNodeWithText("推荐").assertExists()
            onNodeWithContentDescription("返回").assertDoesNotExist()
            onNodeWithContentDescription("搜索成语").assertHasClickAction().performClick()
            assertEquals(1, searchCount)
        }
}
