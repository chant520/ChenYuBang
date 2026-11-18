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
 * 构件黄金快照（规范 §11.1 / §13）。基线由 recordRoborazziDebug 录制入库，
 * verifyRoborazziDebug 在 CI 比对。浅/深通过 LocalConfiguration.uiMode 控制，
 * 与 CButton 内部 isSystemInDarkTheme() 取色一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CButtonSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun captureButton(
        variant: CButtonVariant,
        enabled: Boolean,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    CButton(text = "按钮", onClick = {}, variant = variant, enabled = enabled)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun primaryDefaultLight() = captureButton(CButtonVariant.Primary, enabled = true, night = false)

    @Test
    fun primaryDefaultDark() = captureButton(CButtonVariant.Primary, enabled = true, night = true)

    @Test
    fun primaryDisabledLight() = captureButton(CButtonVariant.Primary, enabled = false, night = false)

    @Test
    fun primaryDisabledDark() = captureButton(CButtonVariant.Primary, enabled = false, night = true)

    @Test
    fun ghostDefaultLight() = captureButton(CButtonVariant.Ghost, enabled = true, night = false)

    @Test
    fun ghostDefaultDark() = captureButton(CButtonVariant.Ghost, enabled = true, night = true)

    @Test
    fun ghostDisabledLight() = captureButton(CButtonVariant.Ghost, enabled = false, night = false)

    @Test
    fun ghostDisabledDark() = captureButton(CButtonVariant.Ghost, enabled = false, night = true)
}
