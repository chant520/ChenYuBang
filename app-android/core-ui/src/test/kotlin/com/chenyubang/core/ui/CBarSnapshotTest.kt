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
 * 提示条与行内警示/错误黄金快照（规范 §5.4 / §5.5 / §11.1 / §13）。
 * 提示条三种别 × 浅/深（带副文案 + 操作链接以铺满布局）；行内警示/错误各 × 浅/深。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与构件内部 isSystemInDarkTheme() 取色一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CBarSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun captureBar(
        kind: CBarKind,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    CBar(
                        kind = kind,
                        title = "提示",
                        subtitle = "副文案",
                        actionText = "操作",
                        onAction = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun captureInline(
        error: Boolean,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    if (error) CInlineError(text = "错误") else CInlineWarning(text = "警示")
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun barInfoLight() = captureBar(CBarKind.Info, night = false)

    @Test
    fun barInfoDark() = captureBar(CBarKind.Info, night = true)

    @Test
    fun barWarnLight() = captureBar(CBarKind.Warn, night = false)

    @Test
    fun barWarnDark() = captureBar(CBarKind.Warn, night = true)

    @Test
    fun barErrorLight() = captureBar(CBarKind.Error, night = false)

    @Test
    fun barErrorDark() = captureBar(CBarKind.Error, night = true)

    @Test
    fun inlineWarningLight() = captureInline(error = false, night = false)

    @Test
    fun inlineWarningDark() = captureInline(error = false, night = true)

    @Test
    fun inlineErrorLight() = captureInline(error = true, night = false)

    @Test
    fun inlineErrorDark() = captureInline(error = true, night = true)
}
