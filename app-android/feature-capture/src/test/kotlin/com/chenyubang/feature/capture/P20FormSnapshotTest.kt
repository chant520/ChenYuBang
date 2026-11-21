package com.chenyubang.feature.capture

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
 * P20 快照（规范 §11.1）：基线只增不改。
 * `twoSelectedHints*` 对应 iOS `P20MatchSnapshotTests.testFormMain*`（poolSize=3，!canCycle
 * 派生「没有更多候选」灰字，与 iOS disabledHints 同语义；前代 `twoSelected*` 留作派生前历史）；
 * `noMatchTagFixed*` 为候选空态修后基线（对应 iOS `testFormEmpty*`；前代 `noMatch*`、
 * `noMatchSingleEntry*` 留作修前历史）。
 * iOS 另有 `testFormSection*` 单独渲染表单分区：Android 侧该分区没有 testTag，整页基线已含
 * FormFieldsCard，故不单独镜像——对齐的是状态语义，不是文件名一一对应。
 *
 * 候选卡内灰字提示（至少一条 / 没有更多候选 / 候选最多 50 条）已落进 812dp 视口，整页快照可钉：
 * `poolFullHint*` 钉池满上限、`noMoreCandidatesDerived*` 钉池 ≤3 派生句。保存失败反馈条
 * 不做整页快照：构件外观由 `CBarSnapshotTest` 的 barError 基线钉住，
 * 「哪个状态出哪条」由 `P20FormViewModelTest` 的 info / saveError 断言钉住。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P20FormSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(
        state: P20CaptureState,
        night: Boolean,
    ) {
        composeTestRule.setContent {
            val base = LocalConfiguration.current
            val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val config = Configuration(base)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
            CompositionLocalProvider(LocalConfiguration provides config) {
                ChenYuBangTheme(darkTheme = night) {
                    P20FormContent(state = state, actions = P20CaptureActions())
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    /** 池=3（canCycle=false）：「没有更多候选」由 CandidateRows 按 !canCycle 静态派生，对齐 iOS testFormMain 基线。 */
    private fun twoSelectedState(): P20CaptureState =
        P20CaptureState(
            reviewText = "下列各句中，加点的成语使用不恰当的一项是……\nA．新厂长按部就班 B．做事标新立异 C．从不墨守成规",
            candidates =
                listOf(
                    MatchCandidate(ID_BIECHU, "别出心裁", 4, MatchTier.SameQuestion),
                    MatchCandidate(ID_MOSHOU, "墨守成规", 4, MatchTier.Semantic),
                    MatchCandidate(ID_ANBU, "按部就班", 5, MatchTier.Best),
                ),
            checked =
                listOf(
                    IdiomRef(ID_BIECHU, "别出心裁"),
                    IdiomRef(ID_MOSHOU, "墨守成规"),
                ),
            poolSize = 3,
            form =
                P20FormState(
                    isMistake = true,
                    answer = "A",
                    reason = "把贬义词当褒义用了",
                    tags = listOf("褒贬误用", "望文生义"),
                    examType = "省考",
                    yearText = "2025",
                ),
            loading = false,
        )

    /**
     * 候选空态：有识别文字但零命中，候选区出「手动挑成语」入口（iOS testFormEmpty* 同口径文案）。
     * 前代基线（`noMatch*` 双入口缺陷、`noMatchSingleEntry*` 标签行塌宽 + TAG_RULE 死码）按惯例
     * 不覆盖、留作修前历史；`noMatchTagFixed*` 钉住修后渲染：手选入口唯一 + 标签行铺满
     * （占位符单行、「加标签」收缩自适应）+ 编辑区下方 TAG_RULE 提示（followup 2026-11-21 §5.2/§5.3）。
     */
    private fun noMatchState(): P20CaptureState =
        P20CaptureState(
            reviewText = "今天天气不错，我们出去散步吧。",
            loading = false,
        )

    @Test
    fun twoSelectedHintsLight() = capture(twoSelectedState(), night = false)

    @Test
    fun twoSelectedHintsDark() = capture(twoSelectedState(), night = true)

    @Test
    fun noMatchTagFixedLight() = capture(noMatchState(), night = false)

    @Test
    fun noMatchTagFixedDark() = capture(noMatchState(), night = true)

    /**
     * 池满态（poolSize=50）：候选上限提示落进候选卡内灰字行（followup §5.4b），
     * 首次落在 812dp 视口内可被整页快照钉住（此前走页面底部 CBar，视口外拍不到）。
     */
    private fun poolFullState(): P20CaptureState =
        P20CaptureState(
            reviewText = "下列各句中，加点的成语使用不恰当的一项是……\nA．新厂长按部就班 B．做事标新立异",
            candidates =
                listOf(
                    MatchCandidate(ID_BIECHU, "别出心裁", 4, MatchTier.SameQuestion),
                    MatchCandidate(ID_MOSHOU, "墨守成规", 4, MatchTier.Semantic),
                    MatchCandidate(ID_ANBU, "按部就班", 5, MatchTier.Best),
                ),
            checked = listOf(IdiomRef(ID_BIECHU, "别出心裁")),
            poolSize = P20Matcher.MAX_CANDIDATES,
            candidateHint = P20Matcher.CANDIDATE_LIMIT,
            loading = false,
        )

    @Test
    fun poolFullHintLight() = capture(poolFullState(), night = false)

    @Test
    fun poolFullHintDark() = capture(poolFullState(), night = true)

    /**
     * 无更多候选态（池=K，canCycle=false，换一组禁用）：「没有更多候选」靠 CandidateRows
     * 按 !canCycle 静态派生（iOS P20MatchContent.swift:270 同口径），fixture 不再注入
     * candidateHint——前代 `noMoreCandidates*` 钉的注入态在 UI 上不可达，留作历史不覆盖。
     */
    private fun noMoreCandidatesState(): P20CaptureState =
        P20CaptureState(
            reviewText = "今天天气不错，我们出去散步吧。\nA．新厂长按部就班 B．做事标新立异 C．从不墨守成规",
            candidates =
                listOf(
                    MatchCandidate(ID_BIECHU, "别出心裁", 4, MatchTier.SameQuestion),
                    MatchCandidate(ID_MOSHOU, "墨守成规", 4, MatchTier.Semantic),
                    MatchCandidate(ID_ANBU, "按部就班", 5, MatchTier.Best),
                ),
            checked = listOf(IdiomRef(ID_ANBU, "按部就班")),
            poolSize = P20Matcher.WINDOW_SIZE,
            loading = false,
        )

    @Test
    fun noMoreCandidatesDerivedLight() = capture(noMoreCandidatesState(), night = false)

    @Test
    fun noMoreCandidatesDerivedDark() = capture(noMoreCandidatesState(), night = true)

    private companion object {
        const val ID_ANBU = "11111111-1111-4111-8111-111111111111"
        const val ID_BIECHU = "22222222-2222-4222-8222-222222222222"
        const val ID_MOSHOU = "33333333-3333-4333-8333-333333333333"
    }
}
