package com.chenyubang.feature.example

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.data.ExampleEntity
import com.chenyubang.core.data.ExampleListItem
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P06ExamplesSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        updateFailed: Boolean,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P06ExamplesContent(state = state(updateFailed))
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun state(updateFailed: Boolean) =
        P06ExamplesUiState(
            items =
                listOf(
                    ExampleListItem(
                        example =
                            ExampleEntity(
                                id = "e-1",
                                stem = "做事不可墨守成规，应当别出心裁。",
                                optionA = "墨守成规",
                                optionB = "别出心裁",
                                optionC = "按部就班",
                                optionD = "循序渐进",
                                answer = "A",
                                explain = "语境强调应当创新。",
                                examType = "国考",
                                year = 2026,
                                region = "中央",
                                questionType = "言语理解",
                            ),
                        idiomCount = 3,
                    ),
                ),
            loading = false,
            updateFailed = updateFailed,
        )

    @Test
    fun examplesLight() = capture(night = false, updateFailed = false)

    @Test
    fun examplesDark() = capture(night = true, updateFailed = false)

    @Test
    fun examplesUpdateFailedLight() = capture(night = false, updateFailed = true)

    @Test
    fun examplesUpdateFailedDark() = capture(night = true, updateFailed = true)
}
