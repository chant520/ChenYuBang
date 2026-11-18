package com.chenyubang.core.ui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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

/**
 * 加载转圈黄金快照（规范 §5.0 / §6 / §11.1 / §13）。× 浅/深。
 * 构件用 rememberInfiniteTransition 匀速旋转：置系统动画尺度为 0 触发 rememberReduceMotion
 * 把角度钉在 0°，并暂停主时钟（autoAdvance=false）避免无限动画导致 waitForIdle 挂起，
 * 推进首帧后截图，保证像素确定。颜色取主题 primary（令牌 accent），不硬编码。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CSpinnerSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun captureSpinner(night: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    CSpinner(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(FIRST_FRAME_MS)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun spinnerLight() = captureSpinner(night = false)

    @Test
    fun spinnerDark() = captureSpinner(night = true)
}
