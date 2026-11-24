package com.chenyubang.feature.me

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.data.BackupCounts
import com.chenyubang.core.data.BackupImportResult
import com.chenyubang.core.data.ImportMode
import com.chenyubang.core.data.OrphanReport
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P15 结果态专项快照：主态 / 导出成功 / 导出失败 / 导入中 / 导入成功 / 备份无法读取 × 深浅。
 * 用 internal P15BackupContent 注入稳定 banner state；不改视觉、文案、业务逻辑。
 * HardError.detail 为底层诊断，注入空串使副文案不落进基线（诊断只留日志）。
 */
@Suppress("MagicNumber", "TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1280dp-xhdpi")
class P15BackupSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        banner: P15Banner? = null,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P15BackupContent(state = P15BackupUiState(banner = banner))
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun exportOk(): P15Banner.Exported =
        P15Banner.Exported(BackupCounts(12, 7, 5, 30, 2), "backup-20261124.zip")

    private fun importOk(): P15Banner.Imported =
        P15Banner.Imported(
            BackupImportResult(ImportMode.MERGE, BackupCounts(10, 6, 4, 28, 2), OrphanReport()),
        )

    @Test
    fun p15BackupLight() = capture(night = false)

    @Test
    fun p15BackupDark() = capture(night = true)

    @Test
    fun p15ExportOkLight() = capture(night = false, banner = exportOk())

    @Test
    fun p15ExportOkDark() = capture(night = true, banner = exportOk())

    @Test
    fun p15ExportFailLight() = capture(night = false, banner = P15Banner.HardError(P15_EXPORT_FAILURE_TITLE, ""))

    @Test
    fun p15ExportFailDark() = capture(night = true, banner = P15Banner.HardError(P15_EXPORT_FAILURE_TITLE, ""))

    @Test
    fun p15ImportingLight() = capture(night = false, banner = P15Banner.Importing)

    @Test
    fun p15ImportingDark() = capture(night = true, banner = P15Banner.Importing)

    @Test
    fun p15ImportOkLight() = capture(night = false, banner = importOk())

    @Test
    fun p15ImportOkDark() = capture(night = true, banner = importOk())

    @Test
    fun p15BackupUnreadableLight() = capture(night = false, banner = P15Banner.HardError(P15_REJECT_TITLE, ""))

    @Test
    fun p15BackupUnreadableDark() = capture(night = true, banner = P15Banner.HardError(P15_REJECT_TITLE, ""))
}
