package com.chenyubang.core.ui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
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
 * 构件黄金快照（规范 §11.1 / §13）。基线由 recordRoborazziDebug 录制入库，
 * verifyRoborazziDebug 在 CI 比对。浅/深通过 LocalConfiguration.uiMode 控制，
 * 与 CButton 内部 isSystemInDarkTheme() 取色一致。
 *
 * 全矩阵：5 变体 × {默认 / 禁用 / 加载 / 按下} × {浅 / 深}。
 * 加载态内嵌 CSpinner（rememberInfiniteTransition）：置系统动画尺度为 0 触发 rememberReduceMotion
 * 钉住角度，并暂停主时钟推进首帧后截图。按下态 pressDim 瞬时生效：暂停主时钟，对唯一可点节点
 * down() 后推进一帧再截，截图应较默认态整体变暗（×0.9）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CButtonSnapshotTest {
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

    private fun captureButton(
        variant: CButtonVariant,
        enabled: Boolean,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            Themed(night) {
                CButton(text = "按钮", onClick = {}, variant = variant, enabled = enabled)
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun captureButtonLoading(
        variant: CButtonVariant,
        night: Boolean,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Themed(night) {
                CButton(text = "按钮", onClick = {}, variant = variant, loading = true)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(FIRST_FRAME_MS)
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun captureButtonPressed(
        variant: CButtonVariant,
        night: Boolean,
    ) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Themed(night) {
                CButton(text = "按钮", onClick = {}, variant = variant)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(FIRST_FRAME_MS)
        composeTestRule.onNode(hasClickAction()).performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(PRESS_HOLD_MS)
        composeTestRule.onRoot().captureRoboImage()
        composeTestRule.onNode(hasClickAction()).performTouchInput { up() }
    }

    @Test
    fun primaryDefaultLight() = captureButton(CButtonVariant.Primary, enabled = true, night = false)

    @Test
    fun primaryDefaultDark() = captureButton(CButtonVariant.Primary, enabled = true, night = true)

    @Test
    fun primaryDisabledLight() = captureButton(CButtonVariant.Primary, enabled = false, night = false)

    @Test
    fun primaryDisabledDark() = captureButton(CButtonVariant.Primary, enabled = false, night = true)

    @Test
    fun primaryLoadingLight() = captureButtonLoading(CButtonVariant.Primary, night = false)

    @Test
    fun primaryLoadingDark() = captureButtonLoading(CButtonVariant.Primary, night = true)

    @Test
    fun primaryPressedLight() = captureButtonPressed(CButtonVariant.Primary, night = false)

    @Test
    fun primaryPressedDark() = captureButtonPressed(CButtonVariant.Primary, night = true)

    @Test
    fun dangerFilledDefaultLight() = captureButton(CButtonVariant.DangerFilled, enabled = true, night = false)

    @Test
    fun dangerFilledDefaultDark() = captureButton(CButtonVariant.DangerFilled, enabled = true, night = true)

    @Test
    fun dangerFilledDisabledLight() = captureButton(CButtonVariant.DangerFilled, enabled = false, night = false)

    @Test
    fun dangerFilledDisabledDark() = captureButton(CButtonVariant.DangerFilled, enabled = false, night = true)

    @Test
    fun dangerFilledLoadingLight() = captureButtonLoading(CButtonVariant.DangerFilled, night = false)

    @Test
    fun dangerFilledLoadingDark() = captureButtonLoading(CButtonVariant.DangerFilled, night = true)

    @Test
    fun dangerFilledPressedLight() = captureButtonPressed(CButtonVariant.DangerFilled, night = false)

    @Test
    fun dangerFilledPressedDark() = captureButtonPressed(CButtonVariant.DangerFilled, night = true)

    @Test
    fun dangerOutlineDefaultLight() = captureButton(CButtonVariant.DangerOutline, enabled = true, night = false)

    @Test
    fun dangerOutlineDefaultDark() = captureButton(CButtonVariant.DangerOutline, enabled = true, night = true)

    @Test
    fun dangerOutlineDisabledLight() = captureButton(CButtonVariant.DangerOutline, enabled = false, night = false)

    @Test
    fun dangerOutlineDisabledDark() = captureButton(CButtonVariant.DangerOutline, enabled = false, night = true)

    @Test
    fun dangerOutlineLoadingLight() = captureButtonLoading(CButtonVariant.DangerOutline, night = false)

    @Test
    fun dangerOutlineLoadingDark() = captureButtonLoading(CButtonVariant.DangerOutline, night = true)

    @Test
    fun dangerOutlinePressedLight() = captureButtonPressed(CButtonVariant.DangerOutline, night = false)

    @Test
    fun dangerOutlinePressedDark() = captureButtonPressed(CButtonVariant.DangerOutline, night = true)

    @Test
    fun ghostDefaultLight() = captureButton(CButtonVariant.Ghost, enabled = true, night = false)

    @Test
    fun ghostDefaultDark() = captureButton(CButtonVariant.Ghost, enabled = true, night = true)

    @Test
    fun ghostDisabledLight() = captureButton(CButtonVariant.Ghost, enabled = false, night = false)

    @Test
    fun ghostDisabledDark() = captureButton(CButtonVariant.Ghost, enabled = false, night = true)

    @Test
    fun ghostLoadingLight() = captureButtonLoading(CButtonVariant.Ghost, night = false)

    @Test
    fun ghostLoadingDark() = captureButtonLoading(CButtonVariant.Ghost, night = true)

    @Test
    fun ghostPressedLight() = captureButtonPressed(CButtonVariant.Ghost, night = false)

    @Test
    fun ghostPressedDark() = captureButtonPressed(CButtonVariant.Ghost, night = true)

    @Test
    fun topDefaultLight() = captureButton(CButtonVariant.Top, enabled = true, night = false)

    @Test
    fun topDefaultDark() = captureButton(CButtonVariant.Top, enabled = true, night = true)

    @Test
    fun topDisabledLight() = captureButton(CButtonVariant.Top, enabled = false, night = false)

    @Test
    fun topDisabledDark() = captureButton(CButtonVariant.Top, enabled = false, night = true)

    @Test
    fun topLoadingLight() = captureButtonLoading(CButtonVariant.Top, night = false)

    @Test
    fun topLoadingDark() = captureButtonLoading(CButtonVariant.Top, night = true)

    @Test
    fun topPressedLight() = captureButtonPressed(CButtonVariant.Top, night = false)

    @Test
    fun topPressedDark() = captureButtonPressed(CButtonVariant.Top, night = true)
}
