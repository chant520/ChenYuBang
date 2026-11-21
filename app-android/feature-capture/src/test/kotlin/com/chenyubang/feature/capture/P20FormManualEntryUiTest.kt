package com.chenyubang.feature.capture

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.chenyubang.core.ui.ChenYuBangTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「手动挑成语」入口唯一性（followup 2026-11-21 §5.1）：空态入口由候选区 `CEmptyState` 承担，
 * 非空态入口由候选卡底部 Ghost 按钮承担，任何状态下同名节点数必须恰好为 1。
 * 变异验证口径：去掉 CandidateCard 底部按钮的 `isNotEmpty` 条件后，空态用例必须变红（2 个节点）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class P20FormManualEntryUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun manualActionAppearsExactlyOnceInEmptyState() {
        setContentFor(P20CaptureState(reviewText = "今天天气不错，我们出去散步吧。", loading = false))
        assertEquals(1, manualActionNodes())
    }

    @Test
    fun manualActionAppearsExactlyOnceInNonEmptyState() {
        val candidate = MatchCandidate(ID_ANBU, "按部就班", 5, MatchTier.Best)
        setContentFor(
            P20CaptureState(
                reviewText = "下列各句中，加点的成语使用不恰当的一项是……",
                candidates = listOf(candidate),
                checked = listOf(IdiomRef(ID_ANBU, "按部就班")),
                poolSize = 1,
                loading = false,
            ),
        )
        assertEquals(1, manualActionNodes())
    }

    private fun setContentFor(state: P20CaptureState) {
        composeTestRule.setContent {
            ChenYuBangTheme(darkTheme = false) {
                P20FormContent(state = state, actions = P20CaptureActions())
            }
        }
    }

    private fun manualActionNodes() =
        composeTestRule
            .onAllNodesWithText(P20Matcher.MANUAL_ACTION)
            .fetchSemanticsNodes()
            .size

    private companion object {
        const val ID_ANBU = "11111111-1111-4111-8111-111111111111"
    }
}
