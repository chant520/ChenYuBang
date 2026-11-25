package com.chenyubang.feature.idiom

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.data.IdiomLearningStatus
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** P24 我的收藏黄金快照：列表三态 + 空态，浅/深各一组。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P24FavoriteSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        state: P24FavoriteUiState,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P24FavoriteContent(state = state)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun listState() =
        P24FavoriteUiState(
            items =
                listOf(
                    FavoriteIdiomView("a", "高屋建瓴", "gāo wū jiàn líng", 5, IdiomLearningStatus.UNLEARNED),
                    FavoriteIdiomView("b", "汗牛充栋", "hàn niú chōng dòng", 3, IdiomLearningStatus.LEARNED),
                    FavoriteIdiomView("c", "见微知著", "jiàn wēi zhī zhù", 4, IdiomLearningStatus.MASTERED),
                ),
            loading = false,
        )

    @Test
    fun favoriteListLight() = capture(night = false, state = listState())

    @Test
    fun favoriteListDark() = capture(night = true, state = listState())

    @Test
    fun favoriteEmptyLight() = capture(night = false, state = P24FavoriteUiState(loading = false))

    @Test
    fun favoriteEmptyDark() = capture(night = true, state = P24FavoriteUiState(loading = false))
}
