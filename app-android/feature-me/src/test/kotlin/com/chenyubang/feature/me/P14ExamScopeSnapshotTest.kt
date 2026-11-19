package com.chenyubang.feature.me

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P14 考试范围页黄金快照（规范 §11.1）：P01 引导态 × 浅/深。
 * 固定「省考已选且省份默认全选」，确保省考行说明、省份勾选与禁用行都进快照。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1280dp-xhdpi")
class P14ExamScopeSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(night: Boolean) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P14ExamScopeContent(
                        state =
                            P14ExamScopeUiState(
                                initial = ExamScopeSelection(provinces = PROVINCES.toSet()),
                                draft = ExamScopeDraft(provinces = PROVINCES.toSet()),
                                availableProvinces = PROVINCES,
                            ),
                        mode = ExamScopeMode.Onboarding,
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private companion object {
        val PROVINCES = listOf("北京", "河南", "贵州", "广东")
    }

    @Test
    fun examScopeLight() = capture(night = false)

    @Test
    fun examScopeDark() = capture(night = true)
}
