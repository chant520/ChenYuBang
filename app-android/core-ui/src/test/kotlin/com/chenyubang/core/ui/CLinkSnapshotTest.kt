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
 * 文字链接黄金快照（规范 §5.7 / §11.1 / §13）× 浅/深。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 CLink 内部 isSystemInDarkTheme() 取色一致。
 * 按下态 pressDim 需要交互注入，链接为页内次要跳转，本组只覆盖静态外观。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CLinkSnapshotTest {
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

    private fun captureLink(night: Boolean) {
        composeTestRule.setContent {
            Themed(night) { CLink(text = "调整省份", onClick = {}) }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun linkLight() = captureLink(night = false)

    @Test
    fun linkDark() = captureLink(night = true)
}
