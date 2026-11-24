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

/** P22 导入方式底部 sheet 主态黄金快照：覆盖红徽标 + 合并绿标签 × 浅/深。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1280dp-xhdpi")
class P22ImportModeSnapshotTest {
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
                    P22ImportModeSheetContent()
                }
            }
        }
        // COverlay 用 Compose Dialog，语义树有两个 root；第二个是全屏遮罩+sheet，取它作快照主体。
        composeTestRule.onAllNodes(isRoot())[1].captureRoboImage()
    }

    @Test
    fun p22ImportModeLight() = capture(night = false)

    // Dialog 内部组合会重置 LocalConfiguration；深色用例额外用 Robolectric night 限定符保证卡片走深令牌。
    @Config(sdk = [34], qualifiers = "w360dp-h1280dp-night-xhdpi")
    @Test
    fun p22ImportModeDark() = capture(night = true)
}
