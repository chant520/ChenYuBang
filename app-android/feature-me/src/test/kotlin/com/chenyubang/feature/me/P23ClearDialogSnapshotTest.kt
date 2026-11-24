package com.chenyubang.feature.me

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P23 清空对话框专项快照 × 深浅：主态（含真实录入题条数 userExamples=7）、
 * 清空中（clearing）、失败（failed）、成功（cleared）。确认清空红填充为本屏唯一红。
 */
@Suppress("MagicNumber", "TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1280dp-xhdpi")
class P23ClearDialogSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        state: P23ClearUiState,
    ) {
        composeTestRule.setContent {
            val config = nightConfig(night)
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P23ClearDialogContent(state = state)
                }
            }
        }
        // COverlay 用 Compose Dialog，语义树有两个 root；第二个是全屏遮罩+对话框，取它作快照主体。
        composeTestRule.onAllNodes(isRoot())[1].captureRoboImage()
    }

    @Composable
    private fun nightConfig(night: Boolean): Configuration {
        val base = LocalConfiguration.current
        val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val config = Configuration(base)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
        return config
    }

    private fun main() = P23ClearUiState(mistakes = 12, userExamples = 7, favorites = 4)

    @Test
    fun p23ClearDialogLight() = capture(night = false, state = main())

    // Dialog 内部组合会重置 LocalConfiguration；深色用例额外用 Robolectric night 限定符保证卡片走深令牌。
    @Config(sdk = [34], qualifiers = "w360dp-h1280dp-night-xhdpi")
    @Test
    fun p23ClearDialogDark() = capture(night = true, state = main())

    @Test
    fun clearingLight() = capture(night = false, state = main().copy(clearing = true))

    @Config(sdk = [34], qualifiers = "w360dp-h1280dp-night-xhdpi")
    @Test
    fun clearingDark() = capture(night = true, state = main().copy(clearing = true))

    @Test
    fun failedLight() = capture(night = false, state = main().copy(failed = true))

    @Config(sdk = [34], qualifiers = "w360dp-h1280dp-night-xhdpi")
    @Test
    fun failedDark() = capture(night = true, state = main().copy(failed = true))

    @Test
    fun successLight() = capture(night = false, state = main().copy(cleared = true))

    @Config(sdk = [34], qualifiers = "w360dp-h1280dp-night-xhdpi")
    @Test
    fun successDark() = capture(night = true, state = main().copy(cleared = true))
}
