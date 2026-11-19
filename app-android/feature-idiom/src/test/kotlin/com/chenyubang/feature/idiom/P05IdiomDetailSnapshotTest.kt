package com.chenyubang.feature.idiom

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.data.IdiomEntity
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** P05 成语详情黄金快照（规范 §11.1）：浅/深各一张，五项齐全 + 相关区与录入题占位。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P05IdiomDetailSnapshotTest {
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
                    P05IdiomDetailContent(state = state())
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun state(): P05IdiomDetailUiState =
        P05IdiomDetailUiState(
            detail =
                IdiomDetail.viewOf(
                    IdiomEntity(
                        id = "11111111-1111-4111-8111-111111111111",
                        word = "按部就班",
                        pinyin = "àn bù jiù bān",
                        initials = "abjb",
                        sense = "按：依照；部、班：门类与次序。原指安排事务依照一定条理，后多指照老规矩办事。",
                        origin = "清·李宝嘉《官场现形记》第五十三回",
                        color = "贬义",
                        pitfalls = "[\"望文生义：常被误解为有序完成\",\"对象误用：只形容做事方式\"]",
                        synonyms = "[\"墨守成规\",\"循规蹈矩\"]",
                        antonyms = "[\"别出心裁\",\"标新立异\"]",
                        frequency = 5,
                    ),
                ),
            loading = false,
        )

    @Test
    fun detailLight() = capture(night = false)

    @Test
    fun detailDark() = capture(night = true)
}
