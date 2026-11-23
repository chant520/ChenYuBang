package com.chenyubang.feature.review

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chenyubang.core.data.ReviewIdiom
import com.chenyubang.core.data.ReviewQuestion
import com.chenyubang.core.data.ReviewScoring
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** P11 黄金快照：判分区「相同 / 不同」两态 + 两种空态，各浅/深一张。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P11ReviewQueueSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        state: ReviewUiState,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P11ReviewQueueContent(state = state, onPick = {}, onNext = {}, onClose = {}, onBackToSource = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private val options = listOf("按部就班", "墨守成规", "别出心裁", "标新立异")

    private fun question() =
        ReviewQuestion(
            mistakeId = "m-1",
            stem = "依次填入划横线部分最恰当的一项是：做事不可______，应当有新办法。",
            options = options,
            myAnswer = "B",
            idioms = listOf(ReviewIdiom("i-1", "按部就班")),
        )

    /** 首次作答 B：选 B＝相同（答错），选 C＝不同（答对）。 */
    private fun answered(picked: Int) = ReviewAnswer(picked, ReviewScoring.judge("B", options, picked))

    private fun loaded(answers: Map<String, ReviewAnswer>) =
        ReviewUiState(questions = listOf(question()), answers = answers, loading = false)

    @Test
    fun verdictSameLight() = capture(false, loaded(mapOf("m-1" to answered(1))))

    @Test
    fun verdictSameDark() = capture(true, loaded(mapOf("m-1" to answered(1))))

    @Test
    fun verdictDifferentLight() = capture(false, loaded(mapOf("m-1" to answered(2))))

    @Test
    fun verdictDifferentDark() = capture(true, loaded(mapOf("m-1" to answered(2))))

    @Test
    fun emptyGlobalLight() = capture(false, ReviewUiState(loading = false))

    @Test
    fun emptyGlobalDark() = capture(true, ReviewUiState(loading = false))

    @Test
    fun emptySingleLight() = capture(false, ReviewUiState(scopeIdiomId = "i-1", scopeWord = "按部就班", loading = false))

    @Test
    fun emptySingleDark() = capture(true, ReviewUiState(scopeIdiomId = "i-1", scopeWord = "按部就班", loading = false))
}
