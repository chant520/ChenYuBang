package com.chenyubang.feature.review

import com.chenyubang.core.data.ReviewIdiom
import com.chenyubang.core.data.ReviewJudgement
import com.chenyubang.core.data.ReviewQuestion
import com.chenyubang.core.data.ReviewScoring
import java.io.Serializable

/** 一题的本次作答：所选下标 + 判分结果。 */
data class ReviewAnswer(
    val picked: Int,
    val verdict: ReviewJudgement,
)

data class ReviewUiState(
    val scopeIdiomId: String? = null,
    val scopeWord: String? = null,
    val questions: List<ReviewQuestion> = emptyList(),
    val index: Int = 0,
    val answers: Map<String, ReviewAnswer> = emptyMap(),
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    val total: Int get() = questions.size
    val current: ReviewQuestion? get() = questions.getOrNull(index)
    val currentAnswer: ReviewAnswer? get() = current?.let { answers[it.mistakeId] }
    val isLast: Boolean get() = questions.isNotEmpty() && index == questions.lastIndex
    val answerCount: Int get() = answers.size
    val correctCount: Int get() = answers.values.count { it.verdict.result == ReviewScoring.RESULT_CORRECT }
    val scopeLine: String get() = scopeWord?.let { "只看『$it』" } ?: "全部待复习"
}

/** P12 展示用的一轮结果；涉及成语按本轮范围收窄，单成语不扩大到全局。 */
data class ReviewSummary(
    val scopeIdiomId: String? = null,
    val scopeWord: String? = null,
    val questionCount: Int,
    val answerCount: Int,
    val correctCount: Int,
    val idioms: List<ReviewIdiom>,
) : Serializable {
    val scopeLine: String get() = scopeWord?.let { "只看『$it』" } ?: "全部待复习"

    companion object {
        private const val serialVersionUID = 1L
    }
}

/** P11 纯状态机：选完立即判分、判分区展示、下一题与小结派生，无 Compose 依赖。 */
object ReviewFlow {
    fun pick(
        state: ReviewUiState,
        index: Int,
    ): ReviewUiState {
        val question = state.current
        val canPick =
            question != null && index in question.options.indices && !state.answers.containsKey(question.mistakeId)
        return if (canPick && question != null) {
            val verdict = ReviewScoring.judge(question.myAnswer, question.options, index)
            state.copy(answers = state.answers + (question.mistakeId to ReviewAnswer(index, verdict)))
        } else {
            state
        }
    }

    fun next(state: ReviewUiState): ReviewUiState =
        if (state.index < state.total - 1) state.copy(index = state.index + 1) else state

    fun summary(state: ReviewUiState): ReviewSummary {
        val all = state.questions.flatMap { it.idioms }
        val scoped =
            if (state.scopeIdiomId == null) {
                all
            } else {
                all.filter { it.id == state.scopeIdiomId }
            }
        return ReviewSummary(
            scopeIdiomId = state.scopeIdiomId,
            scopeWord = state.scopeWord,
            questionCount = state.total,
            answerCount = state.answerCount,
            correctCount = state.correctCount,
            idioms = scoped.distinctBy { it.id },
        )
    }
}
