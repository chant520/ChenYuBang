package com.chenyubang.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
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
 * 列表行黄金快照（规范 §5.13 / §11.1 / §13）。默认 / 选中 / 禁用 / 带徽标 × 浅/深，
 * 外套 CList 以铺 surface 底 + 描边 + 圆角裁切。
 * 浅/深通过 LocalConfiguration.uiMode 控制，与构件内部 isSystemInDarkTheme() 取色一致。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class CListRowSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun captureListRow(
        night: Boolean,
        selected: Boolean = false,
        enabled: Boolean = true,
        meta: (@Composable () -> Unit)? = null,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    CList {
                        CListRow(
                            name = "成语",
                            pinyin = "chéng yǔ",
                            selected = selected,
                            enabled = enabled,
                            meta = meta,
                            onClick = {},
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun listRowDefaultLight() = captureListRow(night = false)

    @Test
    fun listRowDefaultDark() = captureListRow(night = true)

    @Test
    fun listRowSelectedLight() = captureListRow(night = false, selected = true)

    @Test
    fun listRowSelectedDark() = captureListRow(night = true, selected = true)

    @Test
    fun listRowDisabledLight() = captureListRow(night = false, enabled = false)

    @Test
    fun listRowDisabledDark() = captureListRow(night = true, enabled = false)

    @Test
    fun listRowWithBadgeLight() = captureListRow(night = false, meta = { CBadge(text = "标", kind = CBadgeKind.Info) })

    @Test
    fun listRowWithBadgeDark() = captureListRow(night = true, meta = { CBadge(text = "标", kind = CBadgeKind.Info) })
}
