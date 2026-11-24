package com.chenyubang.feature.me

import android.content.res.Configuration
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

/** P23 清空数据确认对话框主态黄金快照：稿面示意条数 12/4、无失败提示 × 浅/深。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1280dp-xhdpi")
class P23ClearDialogSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(night: Boolean) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P23ClearDialogContent(state = P23ClearUiState(mistakes = 12, favorites = 4))
                }
            }
        }
        // COverlay 用 Compose Dialog，语义树有两个 root；第二个是全屏遮罩+对话框，取它作快照主体。
        composeTestRule.onAllNodes(isRoot())[1].captureRoboImage()
    }

    @Test
    fun p23ClearDialogLight() = capture(night = false)

    // Dialog 内部组合会重置 LocalConfiguration；深色用例额外用 Robolectric night 限定符保证卡片走深令牌。
    @Config(sdk = [34], qualifiers = "w360dp-h1280dp-night-xhdpi")
    @Test
    fun p23ClearDialogDark() = capture(night = true)
}
