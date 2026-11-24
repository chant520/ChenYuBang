package com.chenyubang.feature.me

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.data.PackVersionState
import com.chenyubang.core.data.PackVersionStatus
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** P16 题库版本主态黄金快照：BUILTIN 内置包态（非 TEST）× 浅/深。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1280dp-xhdpi")
class P16PackSnapshotTest {
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
                    P16PackContent(
                        state =
                            PackVersionState(
                                status = PackVersionStatus.BUILTIN,
                                version = "v1.0.0",
                                idiomCount = 59,
                                exampleCount = 11,
                            ),
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun p16PackLight() = capture(night = false)

    @Test
    fun p16PackDark() = capture(night = true)
}
