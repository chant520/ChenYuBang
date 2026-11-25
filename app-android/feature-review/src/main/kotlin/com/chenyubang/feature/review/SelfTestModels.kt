package com.chenyubang.feature.review

import kotlin.random.Random

/** P24 题库快照只含出题必需字段，不持有 Room / Android 类型。 */
data class SelfTestCandidate(
    val id: String,
    val word: String,
    val sense: String,
)

enum class SelfTestQuestionType(val label: String) {
    SenseToIdiom("给释义选成语"),
    IdiomToSense("给成语选释义"),
}

data class SelfTestOption(
    val idiomId: String,
    val text: String,
)

data class SelfTestQuestion(
    val number: Int,
    val type: SelfTestQuestionType,
    val prompt: String,
    val targetId: String,
    val options: List<SelfTestOption>,
)

data class SelfTestAnswer(
    val selectedIdiomId: String,
    val isCorrect: Boolean,
)

data class SelfTestSummary(
    val questionCount: Int,
    val correctCount: Int,
) {
    val wrongCount: Int get() = questionCount - correctCount
}

sealed interface SelfTestUiState {
    data object Loading : SelfTestUiState

    data object EmptyScope : SelfTestUiState

    data object InsufficientCandidates : SelfTestUiState

    data class Question(
        val current: SelfTestQuestion,
        val answer: SelfTestAnswer? = null,
    ) : SelfTestUiState {
        val questionNumber: Int get() = current.number
        val nextLabel: String get() = if (questionNumber == SelfTestEngine.QUESTION_COUNT) "查看结果" else "下一题"
    }

    data class Summary(val result: SelfTestSummary) : SelfTestUiState

    data class Error(val retryToken: Int = 0) : SelfTestUiState
}

/**
 * D2 纯出题状态机：范围内目标 + 全库干扰项，随机源可注入，结果只存在内存。
 * 先按全库判定词与释义的唯一性，避免范围子集把全库歧义记录误当成可出题目标。
 */
object SelfTestEngine {
    const val QUESTION_COUNT = 10
    const val OPTION_COUNT = 4

    fun createRound(
        scoped: List<SelfTestCandidate>,
        library: List<SelfTestCandidate>,
        random: Random = Random.Default,
    ): List<SelfTestQuestion> {
        val eligible = eligible(library)
        val scopedIds = scoped.map { it.id.trim() }.toSet()
        val targets = eligible.filter { it.id in scopedIds }
        require(targets.isNotEmpty()) { "EmptyScope" }
        require(eligible.size >= OPTION_COUNT) { "InsufficientCandidates" }
        val targetSequence =
            targets.shuffled(random).let { shuffled ->
                List(QUESTION_COUNT) { index -> shuffled[index % shuffled.size] }
            }
        return targetSequence.mapIndexed { index, target ->
            val distractors = randomDistractors(eligible, target.id, random)
            val options = (distractors + target).shuffled(random)
            val type = if (index % 2 == 0) SelfTestQuestionType.SenseToIdiom else SelfTestQuestionType.IdiomToSense
            SelfTestQuestion(
                number = index + 1,
                type = type,
                prompt = if (type == SelfTestQuestionType.SenseToIdiom) target.sense else target.word,
                targetId = target.id,
                options =
                    options.map { idiom ->
                        SelfTestOption(
                            idiomId = idiom.id,
                            text = if (type == SelfTestQuestionType.SenseToIdiom) idiom.word else idiom.sense,
                        )
                    },
            )
        }
    }

    fun eligible(library: List<SelfTestCandidate>): List<SelfTestCandidate> {
        val valid =
            library.mapNotNull { candidate ->
                val id = candidate.id.trim()
                val word = candidate.word.trim()
                val sense = candidate.sense.trim()
                if (id.isEmpty() || word.isEmpty() || sense.isEmpty()) {
                    null
                } else {
                    candidate.copy(id = id, word = word, sense = sense)
                }
            }
        val idCounts = valid.groupingBy { it.id }.eachCount()
        val wordCounts = valid.groupingBy { it.word }.eachCount()
        val senseCounts = valid.groupingBy { it.sense }.eachCount()
        return valid.filter {
            idCounts.getValue(it.id) == 1 && wordCounts.getValue(it.word) == 1 && senseCounts.getValue(it.sense) == 1
        }
    }

    private fun randomDistractors(
        eligible: List<SelfTestCandidate>,
        targetId: String,
        random: Random,
    ): List<SelfTestCandidate> = eligible.filter { it.id != targetId }.shuffled(random).take(OPTION_COUNT - 1)
}

/** 当前题的内存状态迁移；重复选择与未判分前进都保持原状态。 */
object SelfTestFlow {
    fun pick(
        state: SelfTestUiState.Question,
        optionId: String,
    ): SelfTestUiState.Question {
        if (state.answer != null || state.current.options.none { it.idiomId == optionId }) return state
        return state.copy(answer = SelfTestAnswer(optionId, optionId == state.current.targetId))
    }
}
