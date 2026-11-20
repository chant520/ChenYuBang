package com.chenyubang.feature.capture

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

/** P21 保存成功快照（规范 §11.1）：单归属 / 多归属各浅深两态，新增基线不覆盖旧图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P21DoneSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        multi: Boolean,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P21DoneScreen(
                        idioms = if (multi) MULTI else SINGLE,
                        onOpenIdiom = {},
                        onOpenMistakes = {},
                        onContinue = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun singleAttributionLight() = capture(night = false, multi = false)

    @Test
    fun singleAttributionDark() = capture(night = true, multi = false)

    @Test
    fun multiAttributionLight() = capture(night = false, multi = true)

    @Test
    fun multiAttributionDark() = capture(night = true, multi = true)

    private companion object {
        val SINGLE = listOf(IdiomRef("id-anbu", "按部就班"))
        val MULTI =
            listOf(
                IdiomRef("id-biechu", "别出心裁"),
                IdiomRef("id-moshou", "墨守成规"),
                IdiomRef("id-anbu", "按部就班"),
            )
    }
}
