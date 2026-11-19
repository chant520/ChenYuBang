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
import androidx.compose.ui.unit.dp
import com.chenyubang.core.ui.tokens.Tokens
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 图标描边按规范 §6 尺寸-描边表：26 档取 1.7
private const val ICON_STROKE = 1.7f

/**
 * 内联图标黄金快照（规范 §6 / §11.1 / §13）。5 语义 × 浅/深。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与页面取色一致；颜色取主题 onSurface（与文字同色）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CIconSnapshotTest {
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

    private fun captureIcon(
        kind: CIconKind,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            Themed(night) {
                CIcon(
                    kind = kind,
                    size = Tokens.Icon.n26.dp,
                    stroke = ICON_STROKE,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun iconPlusLight() = captureIcon(CIconKind.Plus, night = false)

    @Test
    fun iconPlusDark() = captureIcon(CIconKind.Plus, night = true)

    @Test
    fun iconCheckLight() = captureIcon(CIconKind.Check, night = false)

    @Test
    fun iconCheckDark() = captureIcon(CIconKind.Check, night = true)

    @Test
    fun iconWarnLight() = captureIcon(CIconKind.Warn, night = false)

    @Test
    fun iconWarnDark() = captureIcon(CIconKind.Warn, night = true)

    @Test
    fun iconErrorLight() = captureIcon(CIconKind.Error, night = false)

    @Test
    fun iconErrorDark() = captureIcon(CIconKind.Error, night = true)

    @Test
    fun iconInfoLight() = captureIcon(CIconKind.Info, night = false)

    @Test
    fun iconInfoDark() = captureIcon(CIconKind.Info, night = true)
}
