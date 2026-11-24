package com.chenyubang.feature.me

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.data.PackVersionState
import com.chenyubang.core.data.PackVersionStatus
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P16 六态专项快照 × 深浅：BUILTIN（真实内置包 59 成语 / 11 真题）、CHECKING、
 * DOWNLOADING 42%、UPDATE_OK、RECOVERABLE_FAIL、TEST_MOCK（3 成语 / 2 真题，
 * 来源 shared/fixtures/pack/manifest.correct.json）。TEST 与 BUILTIN 状态字段不混用。
 */
@Suppress("MagicNumber", "TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1280dp-xhdpi")
class P16PackSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        state: PackVersionState,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P16PackContent(state = state)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun builtin() =
        PackVersionState(
            status = PackVersionStatus.BUILTIN,
            version = "v1.0.0",
            idiomCount = 59,
            exampleCount = 11,
        )

    private fun checking() = builtin().copy(status = PackVersionStatus.CHECKING)

    private fun downloading() = builtin().copy(status = PackVersionStatus.DOWNLOADING, progress = 42)

    private fun updateOk() =
        PackVersionState(
            status = PackVersionStatus.UPDATE_OK,
            version = "v1.1.0",
            idiomCount = 60,
            exampleCount = 12,
        )

    private fun recoverable() = builtin().copy(status = PackVersionStatus.RECOVERABLE_FAIL, reason = "下载中断")

    private fun testMock() =
        PackVersionState(
            status = PackVersionStatus.TEST_MOCK,
            version = "测试数据（TEST）",
            isTest = true,
            mockIdiomCount = 3,
            mockExampleCount = 2,
            reason = "题库未获取成功",
        )

    @Test
    fun p16PackLight() = capture(night = false, state = builtin())

    @Test
    fun p16PackDark() = capture(night = true, state = builtin())

    @Test
    fun p16CheckingLight() = capture(night = false, state = checking())

    @Test
    fun p16CheckingDark() = capture(night = true, state = checking())

    @Test
    fun p16Downloading42Light() = capture(night = false, state = downloading())

    @Test
    fun p16Downloading42Dark() = capture(night = true, state = downloading())

    @Test
    fun p16UpdateOkLight() = capture(night = false, state = updateOk())

    @Test
    fun p16UpdateOkDark() = capture(night = true, state = updateOk())

    @Test
    fun p16RecoverableFailLight() = capture(night = false, state = recoverable())

    @Test
    fun p16RecoverableFailDark() = capture(night = true, state = recoverable())

    @Test
    fun p16TestMockLight() = capture(night = false, state = testMock())

    @Test
    fun p16TestMockDark() = capture(night = true, state = testMock())
}
