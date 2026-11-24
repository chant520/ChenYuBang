package com.chenyubang.feature.me

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P22 导入方式专项快照 × 深浅：合并 / 覆盖两卡同屏（p22ImportMode*），
 * 加导入执行失败红硬错误条（importFail*，实现落 P15 结果区、逐字见视觉契约 8.1）。
 */
@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1280dp-xhdpi")
class P22ImportModeSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun captureSheet(night: Boolean) {
        composeTestRule.setContent {
            val config = nightConfig(night)
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P22ImportModeSheetContent()
                }
            }
        }
        // COverlay 用 Compose Dialog，语义树有两个 root；第二个是全屏遮罩+sheet，取它作快照主体。
        composeTestRule.onAllNodes(isRoot())[1].captureRoboImage()
    }

    private fun captureImportFail(night: Boolean) {
        composeTestRule.setContent {
            val config = nightConfig(night)
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P15BackupContent(
                        state =
                            P15BackupUiState(
                                banner = P15Banner.HardError(P15_IMPORT_FAILURE_TITLE, ""),
                            ),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Composable
    private fun nightConfig(night: Boolean): Configuration {
        val base = LocalConfiguration.current
        val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val config = Configuration(base)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
        return config
    }

    @Test
    fun p22ImportModeLight() = captureSheet(night = false)

    // Dialog 内部组合会重置 LocalConfiguration；深色用例额外用 Robolectric night 限定符保证卡片走深令牌。
    @Config(sdk = [34], qualifiers = "w360dp-h1280dp-night-xhdpi")
    @Test
    fun p22ImportModeDark() = captureSheet(night = true)

    @Test
    fun importFailLight() = captureImportFail(night = false)

    @Test
    fun importFailDark() = captureImportFail(night = true)
}
