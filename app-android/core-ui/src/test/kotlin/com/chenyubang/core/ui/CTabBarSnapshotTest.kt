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
 * 底部标签栏黄金快照（规范 §5.12 / §11.1 / §13）。四选中位 × 浅/深，
 * 每帧恰一选中（accent 指示条 + 加粗）余皆未选中。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 CTabBar 内部 isSystemInDarkTheme() 取色一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CTabBarSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun captureTabBar(
        selected: Int,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    CTabBar(
                        labels = listOf("成语", "真题", "错题", "我的"),
                        selected = selected,
                        onSelect = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun tabBarSelected0Light() = captureTabBar(selected = 0, night = false)

    @Test
    fun tabBarSelected0Dark() = captureTabBar(selected = 0, night = true)

    @Test
    fun tabBarSelected1Light() = captureTabBar(selected = 1, night = false)

    @Test
    fun tabBarSelected1Dark() = captureTabBar(selected = 1, night = true)

    @Test
    fun tabBarSelected2Light() = captureTabBar(selected = 2, night = false)

    @Test
    fun tabBarSelected2Dark() = captureTabBar(selected = 2, night = true)

    @Test
    fun tabBarSelected3Light() = captureTabBar(selected = 3, night = false)

    @Test
    fun tabBarSelected3Dark() = captureTabBar(selected = 3, night = true)
}
