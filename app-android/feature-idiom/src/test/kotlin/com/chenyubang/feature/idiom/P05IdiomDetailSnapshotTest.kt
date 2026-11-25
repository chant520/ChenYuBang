package com.chenyubang.feature.idiom

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.chenyubang.core.data.ExampleEntity
import com.chenyubang.core.data.IdiomEntity
import com.chenyubang.core.data.IdiomLearningState
import com.chenyubang.core.data.IdiomLearningStatus
import com.chenyubang.core.ui.ChenYuBangTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** P05 成语详情黄金快照（规范 §11.1）：浅/深各一张，五项齐全 + 系统真题列表 + 录入题占位。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P05IdiomDetailSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        night: Boolean,
        scrollToText: String? = null,
        canReview: Boolean = false,
        learningState: IdiomLearningState? = null,
        openStatusPicker: Boolean = false,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P05IdiomDetailContent(
                        state =
                            learningState?.let { state().copy(state = it) }
                                ?: if (openStatusPicker) {
                                    state().copy(
                                        state = IdiomLearningState("detail", IdiomLearningStatus.UNLEARNED, false),
                                    )
                                } else {
                                    state()
                                },
                        canReview = canReview,
                    )
                }
            }
        }
        if (scrollToText != null) {
            composeTestRule.onNodeWithText(scrollToText).performScrollTo()
            composeTestRule.waitForIdle()
        }
        if (openStatusPicker) {
            composeTestRule.onNodeWithText("学习状态：未学").performClick()
            composeTestRule.waitForIdle()
            // COverlay 弹层在独立 Dialog 窗口：用 isDialog 精确取节点，避开 onRoot 多根歧义。
            composeTestRule.onNode(isDialog()).captureRoboImage()
            return
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
            examples =
                listOf(
                    IdiomExamples.viewOf(example(2023, "国考", "言语理解", "逻辑填空")),
                    IdiomExamples.viewOf(example(2021, "省考", "判断推理", "成语辨析")),
                ),
        )

    private fun example(
        year: Int,
        region: String,
        examType: String,
        questionType: String,
    ) = ExampleEntity(
        id = "example-$year-$questionType",
        stem = "依次填入划横线部分最恰当的一项是：做事不可______，应当______。",
        optionA = "按部就班",
        optionB = "墨守成规",
        optionC = "别出心裁",
        optionD = "标新立异",
        answer = "B",
        explain = "横线处与后文「应当」构成对比，选含贬义的墨守成规。",
        examType = examType,
        year = year,
        region = region,
        questionType = questionType,
    )

    @Test
    fun detailLight() = capture(night = false)

    @Test
    fun detailDark() = capture(night = true)

    /** 滚动到「相关题目」区：两条系统真题行 + 我录入的题占位（1.6 验收点）。 */
    @Test
    fun detailRelatedLight() = capture(night = false, scrollToText = "我录入的题（标错才进错题本）")

    /** 阶段 3 补帧：有待复习错题时「复习这个成语」由置灰转可点，且不显示置灰原因句。 */
    @Test
    fun reviewEnabledLight() = capture(night = false, scrollToText = "复习这个成语", canReview = true)

    @Test
    fun reviewEnabledDark() = capture(night = true, scrollToText = "复习这个成语", canReview = true)

    /** A7 收藏两态 + A6 三态标签：已收藏实心、学过浅绿。 */
    @Test
    fun favoriteAndStatusLight() =
        capture(
            night = false,
            learningState = IdiomLearningState("detail", IdiomLearningStatus.LEARNED, true),
        )

    /** A6 自评三选一弹层：复用 COverlay + CCheckRow 单选。 */
    @Test
    fun statusPickerLight() = capture(night = false, openStatusPicker = true)

    @Test
    fun statusPickerDark() = capture(night = true, openStatusPicker = true)
}
