package com.chenyubang.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
 * 字母索引条黄金快照（规范 §5.9 / §11.1 / §13）。无选中 / 选中 × 浅/深。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 CLetterIndex 内部 isSystemInDarkTheme() 取色一致。
 * 选中态走真实 selected 参数（accent + 700 + tintbg），非模拟按压。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CLetterIndexSnapshotTest {
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

    private fun captureLetterIndex(
        selected: String?,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            Themed(night) {
                CLetterIndex(letters = listOf("A", "B", "C", "D"), selected = selected, onSelect = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun letterIndexUnselectedLight() = captureLetterIndex(selected = null, night = false)

    @Test
    fun letterIndexUnselectedDark() = captureLetterIndex(selected = null, night = true)

    @Test
    fun letterIndexSelectedLight() = captureLetterIndex(selected = "B", night = false)

    @Test
    fun letterIndexSelectedDark() = captureLetterIndex(selected = "B", night = true)
}
