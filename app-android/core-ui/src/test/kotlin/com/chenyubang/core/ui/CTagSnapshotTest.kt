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
 * 标签黄金快照（规范 §5.2 / §11.1 / §13）。四样式 × 浅/深，静态无交互态。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 CTag 内部 isSystemInDarkTheme() 取色一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CTagSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun captureTag(
        style: CTagStyle,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    CTag(text = "标签", style = style)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun tagSolidLight() = captureTag(CTagStyle.Solid, night = false)

    @Test
    fun tagSolidDark() = captureTag(CTagStyle.Solid, night = true)

    @Test
    fun tagGhostLight() = captureTag(CTagStyle.Ghost, night = false)

    @Test
    fun tagGhostDark() = captureTag(CTagStyle.Ghost, night = true)

    @Test
    fun tagSelectedLight() = captureTag(CTagStyle.Selected, night = false)

    @Test
    fun tagSelectedDark() = captureTag(CTagStyle.Selected, night = true)

    @Test
    fun tagDisabledLight() = captureTag(CTagStyle.Disabled, night = false)

    @Test
    fun tagDisabledDark() = captureTag(CTagStyle.Disabled, night = true)
}
