package com.chenyubang.core.ui

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import com.chenyubang.core.ui.tokens.Tokens
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 浮层黄金快照（规范 §5.9 / §11.1 / §13）。居中 dialog / 底部 sheet × 浅/深。
 * COverlay 渲染在独立 Dialog 窗口：用 isDialog() 精确选择 Compose 弹窗节点，
 * 避免 onRoot() 多根歧义及 View 扩展的 Espresso 默认窗口查找；
 * 截取真实布局，包含遮罩（modal）+ 面板（surface）+ 标题。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 COverlay 内部 isSystemInDarkTheme() 取色一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class COverlaySnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun Themed(
        night: Boolean,
        content: @Composable () -> Unit,
    ) {
        val base = LocalConfiguration.current
        val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val config = Configuration(base)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
        CompositionLocalProvider(LocalConfiguration provides config) {
            ChenYuBangTheme(darkTheme = night) { content() }
        }
    }

    private fun captureOverlay(
        centered: Boolean,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            Themed(night) {
                COverlay(title = "标题", centered = centered, onCancel = {}) {
                    Text("内容", color = if (night) Tokens.Dark.text else Tokens.Light.text)
                }
            }
        }
        composeTestRule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun overlayDialogLight() = captureOverlay(centered = true, night = false)

    @Test
    fun overlayDialogDark() = captureOverlay(centered = true, night = true)

    @Test
    fun overlaySheetLight() = captureOverlay(centered = false, night = false)

    @Test
    fun overlaySheetDark() = captureOverlay(centered = false, night = true)
}
