package com.chenyubang.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 卡片黄金快照（规范 §5.7 / §11.1 / §13）。默认 / soft × 浅/深。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 CCard 内部 isSystemInDarkTheme() 取色一致。
 * 卡片 fillMaxWidth，宿主给满屏宽以呈现真实页内宽度。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CCardSnapshotTest {
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

    private fun captureCard(
        soft: Boolean,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            Themed(night) {
                CCard(soft = soft) {
                    CLabel(text = "生效范围")
                    Text(
                        text = "国考 + 省考（全部省份）",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = Tokens.Font.n15.sp,
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun cardDefaultLight() = captureCard(soft = false, night = false)

    @Test
    fun cardDefaultDark() = captureCard(soft = false, night = true)

    @Test
    fun cardSoftLight() = captureCard(soft = true, night = false)

    @Test
    fun cardSoftDark() = captureCard(soft = true, night = true)
}
