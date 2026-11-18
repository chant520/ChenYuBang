package com.chenyubang.core.ui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.runtime.Composable
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
 * 图片占位黄金快照（规范 §5.7 / §11.1 / §13）。中性 / 原图丢失 / 骨架 / 长页截断 × 浅/深。
 * 骨架态委托 CSkeletonBlock（rememberInfiniteTransition）：置系统动画尺度为 0 触发
 * rememberReduceMotion 降级静态，并暂停主时钟推进首帧后截图；其余三态静态直接截。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与构件内部 isSystemInDarkTheme() 取色一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CImagePlaceholderSnapshotTest {
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

    private fun capturePlaceholder(
        kind: CPlaceholderKind,
        night: Boolean,
    ) {
        if (kind == CPlaceholderKind.Skeleton) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            composeTestRule.mainClock.autoAdvance = false
        }
        composeTestRule.setContent {
            Themed(night) {
                CImagePlaceholder(
                    kind = kind,
                    text = if (kind == CPlaceholderKind.Warn) "原图丢失" else "暂无图片",
                    subtitle = if (kind == CPlaceholderKind.Warn) "请重新上传" else "稍后再试",
                    cutLabel = "以下省略",
                )
            }
        }
        if (kind == CPlaceholderKind.Skeleton) {
            composeTestRule.mainClock.advanceTimeBy(FIRST_FRAME_MS)
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun placeholderNeutralLight() = capturePlaceholder(CPlaceholderKind.Neutral, night = false)

    @Test
    fun placeholderNeutralDark() = capturePlaceholder(CPlaceholderKind.Neutral, night = true)

    @Test
    fun placeholderWarnLight() = capturePlaceholder(CPlaceholderKind.Warn, night = false)

    @Test
    fun placeholderWarnDark() = capturePlaceholder(CPlaceholderKind.Warn, night = true)

    @Test
    fun placeholderSkeletonLight() = capturePlaceholder(CPlaceholderKind.Skeleton, night = false)

    @Test
    fun placeholderSkeletonDark() = capturePlaceholder(CPlaceholderKind.Skeleton, night = true)

    @Test
    fun placeholderTruncatedLight() = capturePlaceholder(CPlaceholderKind.Truncated, night = false)

    @Test
    fun placeholderTruncatedDark() = capturePlaceholder(CPlaceholderKind.Truncated, night = true)
}
