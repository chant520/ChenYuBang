package com.chenyubang.core.ui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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

/**
 * 骨架块黄金快照（规范 §5.10 / §11.1 / §13）。× 浅/深。
 * 构件用 rememberInfiniteTransition 横移高光：置系统动画尺度为 0 触发 rememberReduceMotion
 * 降级为静态 skel 底（无高光），并暂停主时钟（autoAdvance=false）避免无限动画挂起 waitForIdle，
 * 推进首帧后截图，保证像素确定。高度取令牌 imgphH。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CSkeletonBlockSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun captureSkeleton(night: Boolean) {
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
                    CSkeletonBlock(height = Tokens.Space.imgphH.dp)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(FIRST_FRAME_MS)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun skeletonBlockLight() = captureSkeleton(night = false)

    @Test
    fun skeletonBlockDark() = captureSkeleton(night = true)
}
