package com.chenyubang.feature.review

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.data.ReviewIdiom
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** P12 黄金快照：全局 / 单成语小结各浅深一张，0 题 E17 兜底态浅深各一张。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P12ReviewSummarySnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        summary: ReviewSummary,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P12ReviewSummaryContent(
                        summary = summary,
                        onBack = {},
                        onBackToSource = {},
                        onAgain = {},
                        onOpenIdiom = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun global() =
        ReviewSummary(
            questionCount = 5,
            answerCount = 5,
            correctCount = 3,
            idioms =
                listOf(
                    ReviewIdiom("i-1", "按部就班"),
                    ReviewIdiom("i-2", "别出心裁"),
                    ReviewIdiom("i-3", "墨守成规"),
                ),
        )

    private fun single() =
        ReviewSummary(
            scopeIdiomId = "i-1",
            scopeWord = "按部就班",
            questionCount = 2,
            answerCount = 2,
            correctCount = 1,
            idioms = listOf(ReviewIdiom("i-1", "按部就班")),
        )

    @Test
    fun globalLight() = capture(false, global())

    @Test
    fun globalDark() = capture(true, global())

    @Test
    fun singleLight() = capture(false, single())

    @Test
    fun singleDark() = capture(true, single())

    @Test
    fun emptyLight() =
        capture(
            night = false,
            summary = ReviewSummary(questionCount = 0, answerCount = 0, correctCount = 0, idioms = emptyList()),
        )

    @Test
    fun emptyDark() =
        capture(
            night = true,
            summary = ReviewSummary(questionCount = 0, answerCount = 0, correctCount = 0, idioms = emptyList()),
        )
}
