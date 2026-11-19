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

/** P04 搜索黄金快照（规范 §11.1）：浅/深各一张，覆盖搜索框 + 结果列表。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P04SearchSnapshotTest {
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
                    P04SearchContent(state = state())
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun state(): P04SearchUiState =
        P04SearchUiState(
            query = "按",
            results =
                IdiomSearchUi.from(
                    listOf(
                        entity("1", "按部就班", "àn bù jiù bān", "按：依照；部、班：门类与次序，后多指照老规矩办事。"),
                        entity("2", "按图索骥", "àn tú suǒ jì", "按照画像寻找好马，比喻依照线索办事。"),
                        entity("3", "按兵不动", "àn bīng bù dòng", "军队暂不行动，后泛指暂不开展行动。"),
                    ),
                ),
            loading = false,
        )

    private fun entity(
        id: String,
        word: String,
        pinyin: String,
        sense: String,
    ) = IdiomEntity(
        id = id,
        word = word,
        pinyin = pinyin,
        initials = "AB",
        sense = sense,
        color = "中性",
        frequency = 3,
    )

    @Test
    fun searchLight() = capture(night = false)

    @Test
    fun searchDark() = capture(night = true)
}
