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
 * 空态黄金快照（规范 §5.8 / §11.1 / §13）。纯文案 / 带动作 × 浅/深。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与 CEmptyState 内部 isSystemInDarkTheme() 取色一致。
 * 带动作态内嵌 CButton Ghost（静态，无动画），直接截图。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CEmptyStateSnapshotTest {
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

    private fun captureEmptyState(
        withAction: Boolean,
        night: Boolean,
        withIcon: Boolean = false,
    ) {
        composeTestRule.setContent {
            Themed(night) {
                CEmptyState(
                    title = "还没有错题",
                    description = "拍照录入后，标错的题会出现在这里。",
                    actionText = if (withAction) "去录入" else null,
                    onAction = if (withAction) ({}) else null,
                    // 「带图标」一态（A8）：D 稿 .eic 插画位，P08 空态即此用法。
                    icon = if (withIcon) CChromeIcon.Camera else null,
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun emptyStatePlainLight() = captureEmptyState(withAction = false, night = false)

    @Test
    fun emptyStatePlainDark() = captureEmptyState(withAction = false, night = true)

    @Test
    fun emptyStateActionLight() = captureEmptyState(withAction = true, night = false)

    @Test
    fun emptyStateActionDark() = captureEmptyState(withAction = true, night = true)

    @Test
    fun emptyStateIconLight() = captureEmptyState(withAction = false, night = false, withIcon = true)

    @Test
    fun emptyStateIconDark() = captureEmptyState(withAction = false, night = true, withIcon = true)
}
