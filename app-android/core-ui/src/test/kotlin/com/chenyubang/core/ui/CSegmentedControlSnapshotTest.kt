package com.chenyubang.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 分段控件黄金快照（规范 §5.14 / §11.1 / §13）。默认选中 / 切换选中 / 含禁用项 / 两项变体 × 浅/深。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 CSegmentedControl 内部 isSystemInDarkTheme() 取色一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CSegmentedControlSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun captureSegmented(
        night: Boolean,
        items: List<String> = listOf("甲", "乙", "丙"),
        selectedIndex: Int = 0,
        disabledIndices: Set<Int> = emptySet(),
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    CSegmentedControl(
                        items = items,
                        selectedIndex = selectedIndex,
                        onSelect = {},
                        disabledIndices = disabledIndices,
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun segmentedDefaultLight() = captureSegmented(night = false)

    @Test
    fun segmentedDefaultDark() = captureSegmented(night = true)

    @Test
    fun segmentedChangedLight() = captureSegmented(night = false, selectedIndex = 1)

    @Test
    fun segmentedChangedDark() = captureSegmented(night = true, selectedIndex = 1)

    @Test
    fun segmentedDisabledItemLight() = captureSegmented(night = false, disabledIndices = setOf(2))

    @Test
    fun segmentedDisabledItemDark() = captureSegmented(night = true, disabledIndices = setOf(2))

    @Test
    fun segmentedTwoItemsLight() = captureSegmented(night = false, items = listOf("甲", "乙"))

    @Test
    fun segmentedTwoItemsDark() = captureSegmented(night = true, items = listOf("甲", "乙"))

    /**
     * L3 重排回归：按下「甲」后把列表改为 [乙, 甲, 丙]。key(label) 应让按压态随 label 走，
     * 即「甲」仍为按下态、「乙」不误承按压态；key(index) 会把按压态留在原位置而误落到「乙」。
     */
    @Test
    fun segmentedItemsReorderKeepsPressedStateOnLabel() {
        composeTestRule.mainClock.autoAdvance = false
        val items = mutableStateListOf("甲", "乙", "丙")
        composeTestRule.setContent {
            ChenYuBangTheme(darkTheme = false) {
                CSegmentedControl(items = items, selectedIndex = 0, onSelect = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.onNodeWithText("甲").performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.runOnUiThread {
            items.removeAt(0)
            items.add(1, "甲")
        }
        composeTestRule.mainClock.advanceTimeBy(16L)
        assertTrue(composeTestRule.onAllNodesWithText("乙").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("甲").fetchSemanticsNodes().isNotEmpty())
        composeTestRule.onRoot().captureRoboImage()
        composeTestRule.onNodeWithText("甲").performTouchInput { up() }
    }
}
