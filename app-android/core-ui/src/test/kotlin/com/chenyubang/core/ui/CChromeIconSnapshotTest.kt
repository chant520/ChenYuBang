package com.chenyubang.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.ui.tokens.Tokens
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 导航图标黄金快照（规范 §6 / §11.1 / §13）。10 语义 × 浅/深。
 * 浅/深通过 LocalConfiguration.uiMode 控制；颜色取主题 onSurface（与文字同色）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CChromeIconSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun Themed(
        night: Boolean,
        content: @Composable () -> Unit,
    ) {
        val base = LocalConfiguration.current
        val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val config = Configuration(base)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
        CompositionLocalProvider(LocalConfiguration provides config) {
            ChenYuBangTheme(darkTheme = night) {
                val page = if (night) Tokens.Dark.page else Tokens.Light.page
                Box(modifier = Modifier.fillMaxWidth().background(page)) { content() }
            }
        }
    }

    private fun captureGlyph(
        kind: CChromeIcon,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            Themed(night) {
                CChromeGlyph(kind = kind, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun chromeBookLight() = captureGlyph(CChromeIcon.Book, night = false)

    @Test
    fun chromeBookDark() = captureGlyph(CChromeIcon.Book, night = true)

    @Test
    fun chromeExampleLight() = captureGlyph(CChromeIcon.Example, night = false)

    @Test
    fun chromeExampleDark() = captureGlyph(CChromeIcon.Example, night = true)

    @Test
    fun chromeMistakeLight() = captureGlyph(CChromeIcon.Mistake, night = false)

    @Test
    fun chromeMistakeDark() = captureGlyph(CChromeIcon.Mistake, night = true)

    @Test
    fun chromeMeLight() = captureGlyph(CChromeIcon.Me, night = false)

    @Test
    fun chromeMeDark() = captureGlyph(CChromeIcon.Me, night = true)

    @Test
    fun chromeCameraLight() = captureGlyph(CChromeIcon.Camera, night = false)

    @Test
    fun chromeCameraDark() = captureGlyph(CChromeIcon.Camera, night = true)

    @Test
    fun chromeSearchLight() = captureGlyph(CChromeIcon.Search, night = false)

    @Test
    fun chromeSearchDark() = captureGlyph(CChromeIcon.Search, night = true)

    @Test
    fun chromeChevronLight() = captureGlyph(CChromeIcon.Chevron, night = false)

    @Test
    fun chromeChevronDark() = captureGlyph(CChromeIcon.Chevron, night = true)

    @Test
    fun chromeBackLight() = captureGlyph(CChromeIcon.Back, night = false)

    @Test
    fun chromeBackDark() = captureGlyph(CChromeIcon.Back, night = true)

    @Test
    fun chromeCloseLight() = captureGlyph(CChromeIcon.Close, night = false)

    @Test
    fun chromeCloseDark() = captureGlyph(CChromeIcon.Close, night = true)

    @Test
    fun chromeSoundLight() = captureGlyph(CChromeIcon.Sound, night = false)

    @Test
    fun chromeSoundDark() = captureGlyph(CChromeIcon.Sound, night = true)
}
