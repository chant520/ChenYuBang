package com.chenyubang.core.ui

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.ui.tokens.Tokens
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 主题一致性：显式 darkTheme=true 且系统为浅色时，构件取色源（LocalColorSet）
 * 必须与 MaterialTheme.colorScheme 同源，不回退到系统深浅（L2 回归）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class ThemeConsistencySnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun ColorProbe() {
        val c = LocalColorSet.current
        assertEquals(Tokens.Dark, c)
        assertEquals(true, LocalDarkTheme.current)
        assertEquals(c.page, MaterialTheme.colorScheme.background)
        assertEquals(c.surface, MaterialTheme.colorScheme.surface)
        assertEquals(c.accent, MaterialTheme.colorScheme.primary)
    }

    @Test
    fun explicitDarkThemeWithLightSystemUsesDarkColors() {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = true) {
                    ColorProbe()
                    CList {
                        CListRow(
                            name = "成语",
                            pinyin = "chéng yǔ",
                            onClick = {},
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
