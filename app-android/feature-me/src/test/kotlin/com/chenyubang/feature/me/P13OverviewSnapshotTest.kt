package com.chenyubang.feature.me

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
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

/** P13 E2 概览黄金快照（4+1 数字 + 我的收藏入口）：浅/深 + 全空各一张。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P13OverviewSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        stats: P13OverviewStats = SAMPLE,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P13OverviewContent(stats = stats)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun overviewLight() = capture(night = false)

    @Test
    fun overviewDark() = capture(night = true)

    @Test
    fun overviewEmptyLight() = capture(night = false, stats = P13OverviewStats())

    private companion object {
        val SAMPLE = P13OverviewStats(learned = 18, favorites = 4, mistakes = 12, pending = 7, mastered = 6)
    }
}
