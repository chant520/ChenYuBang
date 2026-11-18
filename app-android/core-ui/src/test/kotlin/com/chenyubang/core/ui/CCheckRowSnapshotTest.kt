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
 * 勾选行黄金快照（规范 §5.15 / §11.1 / §13）。选中 / 未选 / 禁用 / 带警示 × 浅/深。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 CCheckRow 内部 isSystemInDarkTheme() 取色一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CCheckRowSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun captureCheckRow(
        night: Boolean,
        checked: Boolean,
        enabled: Boolean = true,
        warning: String? = null,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    CCheckRow(
                        label = "选项",
                        checked = checked,
                        onCheckedChange = {},
                        enabled = enabled,
                        warning = warning,
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun checkRowCheckedLight() = captureCheckRow(night = false, checked = true)

    @Test
    fun checkRowCheckedDark() = captureCheckRow(night = true, checked = true)

    @Test
    fun checkRowUncheckedLight() = captureCheckRow(night = false, checked = false)

    @Test
    fun checkRowUncheckedDark() = captureCheckRow(night = true, checked = false)

    @Test
    fun checkRowDisabledLight() = captureCheckRow(night = false, checked = true, enabled = false)

    @Test
    fun checkRowDisabledDark() = captureCheckRow(night = true, checked = true, enabled = false)

    @Test
    fun checkRowWarningLight() = captureCheckRow(night = false, checked = false, warning = "警示")

    @Test
    fun checkRowWarningDark() = captureCheckRow(night = true, checked = false, warning = "警示")
}
