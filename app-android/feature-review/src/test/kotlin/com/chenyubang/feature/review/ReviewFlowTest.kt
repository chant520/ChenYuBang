package com.chenyubang.feature.review

import com.chenyubang.core.data.ReviewIdiom
import com.chenyubang.core.data.ReviewQuestion
import org.junit.Assert.assertEquals
import org.junit.Test

/** P11 纯状态机钉住：next 边界、summary 三数、单成语收窄、不可比对题只计题量。 */
class ReviewFlowTest {
    private val options = listOf("按部就班", "墨守成规", "别出心裁", "标新立异")

    private fun question(
        id: String,
        myAnswer: String?,
        idioms: List<ReviewIdiom> = listOf(ReviewIdiom("i-$id", "词$id")),
    ) = ReviewQuestion(mistakeId = id, stem = "题干 $id", options = options, myAnswer = myAnswer, idioms = idioms)

    @Test
    fun nextAdvancesThenStopsAtLastQuestion() {
        val start = ReviewUiState(questions = listOf(question("m-1", "B"), question("m-2", "C")), loading = false)
        assertEquals(1, ReviewFlow.next(start).index)
        assertEquals(1, ReviewFlow.next(ReviewFlow.next(start)).index)
    }

    @Test
    fun summaryCountsQuestionsCorrectWrongAndExcludesUncomparable() {
        val anbu = ReviewIdiom("i-anbu", "按部就班")
        val biechu = ReviewIdiom("i-biechu", "别出心裁")
        var state =
            ReviewUiState(
                questions =
                    listOf(
                        question("m-1", "B", listOf(anbu, biechu)),
                        question("m-2", "C", listOf(biechu)),
                        question("m-3", null, listOf(anbu)),
                    ),
                loading = false,
            )
        state = ReviewFlow.pick(state, 2) // m-1：首次 B、本次 C，不同＝对
        state = ReviewFlow.next(state)
        state = ReviewFlow.pick(state, 2) // m-2：首次 C、本次 C，相同＝错
        state = ReviewFlow.next(state)
        state = ReviewFlow.pick(state, 0) // m-3：无首次作答，不可比对

        val summary = ReviewFlow.summary(state)
        assertEquals(3, summary.questionCount)
        assertEquals(3, summary.answerCount)
        assertEquals(1, summary.correctCount)
        assertEquals(1, summary.wrongCount)
        assertEquals(
            listOf("对", "错", null),
            listOf(
                state.answers.getValue("m-1").verdict.result,
                state.answers.getValue("m-2").verdict.result,
                state.answers.getValue("m-3").verdict.result,
            ),
        )
    }

    @Test
    fun summaryNarrowsIdiomsToSingleScopeWithoutExpanding() {
        val anbu = ReviewIdiom("i-anbu", "按部就班")
        val biechu = ReviewIdiom("i-biechu", "别出心裁")
        val state =
            ReviewUiState(
                scopeIdiomId = "i-anbu",
                questions =
                    listOf(
                        question("m-1", "B", listOf(anbu, biechu)),
                        question("m-2", "C", listOf(biechu)),
                    ),
                loading = false,
            )
        assertEquals(listOf("i-anbu"), ReviewFlow.summary(state).idioms.map { it.id })
    }
}
