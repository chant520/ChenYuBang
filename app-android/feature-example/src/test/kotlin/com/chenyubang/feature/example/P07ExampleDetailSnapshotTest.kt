package com.chenyubang.feature.example

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** P07 例题详情黄金快照（规范 §11.1）：浅/深各一张，题干高亮 + 关联成语区。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P07ExampleDetailSnapshotTest {
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
                    P07ExampleDetailContent(
                        state = state(),
                        relatedIdiomIds = listOf(ID_IDIOM_ANBU, ID_IDIOM_MOSHOU),
                        wordIndex = WORD_INDEX,
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun state(): P07ExampleDetailUiState =
        P07ExampleDetailUiState(
            detail =
                ExampleDetailView(
                    id = ID_EXAMPLE,
                    stem = "依次填入划横线部分最恰当的一项是：做事不可______，应当______。",
                    optionA = "墨守成规",
                    optionB = "别出心裁",
                    optionC = "按部就班",
                    optionD = "循序渐进",
                    answer = "A",
                    explain = "语境强调不知变通，用「墨守成规」最贴切。",
                    sourceLine = "2023 · 国考 · 中央 · 言语理解",
                ),
            loading = false,
        )

    @Test
    fun exampleDetailLight() = capture(night = false)

    @Test
    fun exampleDetailDark() = capture(night = true)

    private companion object {
        const val ID_EXAMPLE = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val ID_IDIOM_ANBU = "11111111-1111-4111-8111-111111111111"
        const val ID_IDIOM_MOSHOU = "33333333-3333-4333-8333-333333333333"

        val WORD_INDEX =
            mapOf(
                ID_IDIOM_ANBU to "按部就班",
                ID_IDIOM_MOSHOU to "墨守成规",
                "22222222-2222-4222-8222-222222222222" to "别出心裁",
                "44444444-4444-4444-8444-444444444444" to "循序渐进",
            )
    }
}
