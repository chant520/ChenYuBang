package com.chenyubang.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** P25 每轮只读题库并保留内存进度；重试读取新数据，返回我的由宿主销毁本轮。 */
class SelfTestViewModel(
    private val loadData: suspend () -> Pair<List<SelfTestCandidate>, List<SelfTestCandidate>>,
    private val random: Random = Random.Default,
) : ViewModel() {
    private val _state = MutableStateFlow<SelfTestUiState>(SelfTestUiState.Loading)
    val state: StateFlow<SelfTestUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var questions: List<SelfTestQuestion> = emptyList()
    private var questionIndex = 0
    private var correctCount = 0
    private var retryToken = 0

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        questionIndex = 0
        correctCount = 0
        questions = emptyList()
        retryToken++
        _state.value = SelfTestUiState.Loading
        loadJob =
            viewModelScope.launch {
                try {
                    val (scoped, library) = loadData()
                    when (SelfTestPreparation.from(scoped, library)) {
                        SelfTestPreparation.EmptyScope -> _state.value = SelfTestUiState.EmptyScope
                        SelfTestPreparation.InsufficientCandidates ->
                            _state.value = SelfTestUiState.InsufficientCandidates
                        SelfTestPreparation.Ready -> {
                            val nextQuestions = SelfTestEngine.createRound(scoped, library, random)
                            questions = nextQuestions
                            _state.value = SelfTestUiState.Question(nextQuestions.first())
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _state.value = SelfTestUiState.Error(retryToken)
                }
            }
    }

    fun pick(optionId: String) {
        val current = _state.value as? SelfTestUiState.Question ?: return
        val updated = SelfTestFlow.pick(current, optionId)
        if (updated !== current) _state.value = updated
    }

    fun next() {
        val current = _state.value as? SelfTestUiState.Question ?: return
        if (current.answer == null) return
        if (current.answer.isCorrect) correctCount++
        if (current.questionNumber == SelfTestEngine.QUESTION_COUNT) {
            _state.value = SelfTestUiState.Summary(SelfTestSummary(SelfTestEngine.QUESTION_COUNT, correctCount))
        } else {
            questionIndex++
            _state.value = SelfTestUiState.Question(questions[questionIndex])
        }
    }

    fun retry() = load()
}
