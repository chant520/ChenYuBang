package com.chenyubang.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 暂停主时钟后推进的首帧时长，确保完成一次组合/布局/绘制再截图
private const val FIRST_FRAME_MS = 16L

// 按下后推进的时长，让 collectIsPressedAsState 重组并绘出 pressDim（亮度 ×0.9，无动画瞬时生效）
private const val PRESS_HOLD_MS = 100L

/**
 * 悬浮拍照按钮黄金快照（规范 §5.8 / §11.1 / §13）。默认 / 禁用 / 带徽标 / 按下 × 浅/深。
 * 按下态 pressDim 瞬时生效（collectIsPressedAsState 驱动）：暂停主时钟，对唯一可点节点
 * performTouchInput down() 后推进一帧再截，截图应较默认态整体变暗（×0.9）。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 CFab 内部 isSystemInDarkTheme() 取色一致。
 * `*Cam*` 对应相机图标改设计规范 v1.3 三段值（followup p08-p09 A9，前代 `fab*` 留作修前历史）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CFabSnapshotTest {
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

    private fun captureFab(
        night: Boolean,
        enabled: Boolean = true,
        badgeText: String? = null,
    ) {
        composeTestRule.setContent {
            Themed(night) {
                CFab(onClick = {}, enabled = enabled, badgeText = badgeText)
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun captureFabPressed(night: Boolean) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Themed(night) {
                CFab(onClick = {})
            }
        }
        composeTestRule.mainClock.advanceTimeBy(FIRST_FRAME_MS)
        composeTestRule.onNode(hasClickAction()).performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(PRESS_HOLD_MS)
        composeTestRule.onRoot().captureRoboImage()
        composeTestRule.onNode(hasClickAction()).performTouchInput { up() }
    }

    @Test
    fun fabDefaultCamLight() = captureFab(night = false)

    @Test
    fun fabDefaultCamDark() = captureFab(night = true)

    @Test
    fun fabDisabledCamLight() = captureFab(night = false, enabled = false)

    @Test
    fun fabDisabledCamDark() = captureFab(night = true, enabled = false)

    @Test
    fun fabBadgeCamLight() = captureFab(night = false, badgeText = "3")

    @Test
    fun fabBadgeCamDark() = captureFab(night = true, badgeText = "3")

    @Test
    fun fabPressedCamLight() = captureFabPressed(night = false)

    @Test
    fun fabPressedCamDark() = captureFabPressed(night = true)
}
