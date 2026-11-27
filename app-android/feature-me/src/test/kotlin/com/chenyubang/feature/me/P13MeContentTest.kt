package com.chenyubang.feature.me

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.ui.ChenYuBangTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P13MeContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pageContentDoesNotRepeatShellTitle() {
        composeTestRule.setContent {
            ChenYuBangTheme {
                P13MeContent(scope = ExamScopeSelection(), actions = MeActions({}, {}, {}, {}, {}, {}))
            }
        }

        composeTestRule.onAllNodesWithText("我的").assertCountEquals(0)
        composeTestRule.onNodeWithText("考试范围").assertIsDisplayed()
        composeTestRule.onNodeWithText("成语自测").assertIsDisplayed()
        composeTestRule.onNodeWithText("备份与恢复").assertIsDisplayed()
    }
}
