package com.chenyubang.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.chenyubang.core.ui.tokens.Tokens
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 暂停主时钟后推进的首帧时长，确保完成一次组合/布局/绘制再截图
private const val FIRST_FRAME_MS = 16L

// 点击请求焦点后推进的时长，让 onFocusChanged 重组并绘出聚焦光环；光标闪烁起始帧恒可见，像素确定
private const val FOCUS_HOLD_MS = 100L

/**
 * 输入框黄金快照（规范 §5.3 / §11.1 / §13）。默认 / 校验错误 / 禁用 / 聚焦 × 浅/深。
 * 聚焦态焦点由构件内部 onFocusChanged 驱动：暂停主时钟（autoAdvance=false，避免光标无限闪烁
 * 挂起 waitForIdle），点击文本域请求焦点后推进一帧再截，聚焦光环（tintbg 描边）应可见。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 CInput 内部 isSystemInDarkTheme() 取色一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CInputSnapshotTest {
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

    private fun captureInput(
        night: Boolean,
        enabled: Boolean = true,
        error: Boolean = false,
    ) {
        composeTestRule.setContent {
            Themed(night) {
                CInput(
                    value = "输入",
                    onValueChange = {},
                    placeholder = "占位",
                    enabled = enabled,
                    error = error,
                    errorHint = if (error) "错误提示" else null,
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun captureInputFocused(
        night: Boolean,
        withSlots: Boolean = false,
    ) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Themed(night) {
                val colors = if (night) Tokens.Dark else Tokens.Light
                val leadingIcon: (@Composable () -> Unit)? =
                    if (withSlots) {
                        {
                            CChromeGlyph(CChromeIcon.Search, colors.text3, Modifier.size(Tokens.Icon.n15.dp))
                        }
                    } else {
                        null
                    }
                val trailing: (@Composable () -> Unit)? =
                    if (withSlots) {
                        {
                            Box(Modifier.size(Tokens.Space.hitMin.dp), contentAlignment = Alignment.Center) {
                                CChromeGlyph(CChromeIcon.Close, colors.text3, Modifier.size(Tokens.Icon.n18.dp))
                            }
                        }
                    } else {
                        null
                    }
                // 留出外绘焦点光环的空间；只截此容器，不匹配光标手柄的 Popup 根节点。
                Box(Modifier.testTag("focusedInput").padding(4.dp)) {
                    CInput(
                        value = "输入",
                        onValueChange = {},
                        placeholder = "占位",
                        leadingIcon = leadingIcon,
                        trailing = trailing,
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(FIRST_FRAME_MS)
        composeTestRule.onNode(hasSetTextAction()).performClick()
        composeTestRule.mainClock.advanceTimeBy(FOCUS_HOLD_MS)
        composeTestRule.onNode(hasSetTextAction()).assertIsFocused()
        composeTestRule.onNodeWithTag("focusedInput").captureRoboImage()
    }

    @Test
    fun inputDefaultLight() = captureInput(night = false)

    @Test
    fun inputDefaultDark() = captureInput(night = true)

    @Test
    fun inputErrorLight() = captureInput(night = false, error = true)

    @Test
    fun inputErrorDark() = captureInput(night = true, error = true)

    @Test
    fun inputDisabledLight() = captureInput(night = false, enabled = false)

    @Test
    fun inputDisabledDark() = captureInput(night = true, enabled = false)

    @Test
    fun inputFocusedLight() = captureInputFocused(night = false)

    @Test
    fun inputFocusedDark() = captureInputFocused(night = true)

    @Test
    fun inputFocusedWithSlotsLight() = captureInputFocused(night = false, withSlots = true)

    @Test
    fun inputFocusedWithSlotsDark() = captureInputFocused(night = true, withSlots = true)
}
