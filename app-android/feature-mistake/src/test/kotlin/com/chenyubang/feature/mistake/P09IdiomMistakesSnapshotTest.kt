package com.chenyubang.feature.mistake

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

/**
 * P09 成语下录入题黄金快照：有数据（含未标错）/ 空态，各浅/深一张。基线只增不改。
 *
 * `emptyPurged*` 对应空态文案改 IA v1.4 行 549 现值（followup p08-p09 A5：标题「这个成语下还没有录入题」、
 * 副句「之前录的题已经删掉或已归到别的成语下。」，前代 `empty*` 留作修前历史）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P09IdiomMistakesSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        state: P09IdiomMistakesUiState,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P09IdiomMistakesContent(
                        state = state,
                        onBack = {},
                        onOpenIdiom = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun loaded(): P09IdiomMistakesUiState =
        P09IdiomMistakesUiState(
            idiomId = "idiom-anbu",
            word = "按部就班",
            senseLine = "贬义。原指安排事务依照一定条理，后多指照老规矩办事。",
            rows =
                listOf(
                    IdiomUserExampleView(
                        id = "ue-1",
                        stem = "做事不可一味______，应当根据实际情况调整方法。",
                        recordedAt = "2026-11-14 录入",
                        mistake = MistakeMarkView("待复习", listOf("望文生义")),
                    ),
                    IdiomUserExampleView(
                        id = "ue-2",
                        stem = "下列句子中加点成语使用不恰当的一项是：他照老规矩办事，可谓按部就班。",
                        recordedAt = "2026-11-12 录入",
                        mistake = MistakeMarkView("已掌握", listOf("对象误用")),
                    ),
                    IdiomUserExampleView(
                        id = "ue-3",
                        stem = "填空：这项改革要______地推进，不能急于求成。",
                        recordedAt = "2026-11-09 录入",
                        mistake = null,
                    ),
                ),
            loading = false,
        )

    private fun empty(): P09IdiomMistakesUiState =
        P09IdiomMistakesUiState(
            idiomId = "idiom-anbu",
            word = "按部就班",
            senseLine = "贬义。原指安排事务依照一定条理，后多指照老规矩办事。",
            loading = false,
        )

    @Test
    fun withDataLight() = capture(night = false, state = loaded())

    @Test
    fun withDataDark() = capture(night = true, state = loaded())

    @Test
    fun emptyPurgedLight() = capture(night = false, state = empty())

    @Test
    fun emptyPurgedDark() = capture(night = true, state = empty())
}
