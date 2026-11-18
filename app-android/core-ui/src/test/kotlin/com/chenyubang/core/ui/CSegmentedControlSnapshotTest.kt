package com.chenyubang.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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
}
