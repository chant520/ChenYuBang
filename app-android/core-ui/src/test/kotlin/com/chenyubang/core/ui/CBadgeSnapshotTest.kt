package com.chenyubang.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 徽标黄金快照（规范 §5.6 / §11.1 / §13）。四种别 × 浅/深，静态状态标识。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 CBadge 内部 isSystemInDarkTheme() 取色一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CBadgeSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun captureBadge(
        kind: CBadgeKind,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    CBadge(text = "标", kind = kind)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun badgeWarnLight() = captureBadge(CBadgeKind.Warn, night = false)

    @Test
    fun badgeWarnDark() = captureBadge(CBadgeKind.Warn, night = true)

    @Test
    fun badgeErrorLight() = captureBadge(CBadgeKind.Error, night = false)

    @Test
    fun badgeErrorDark() = captureBadge(CBadgeKind.Error, night = true)

    @Test
    fun badgeInfoLight() = captureBadge(CBadgeKind.Info, night = false)

    @Test
    fun badgeInfoDark() = captureBadge(CBadgeKind.Info, night = true)

    @Test
    fun badgeNeutralLight() = captureBadge(CBadgeKind.Neutral, night = false)

    @Test
    fun badgeNeutralDark() = captureBadge(CBadgeKind.Neutral, night = true)
}
