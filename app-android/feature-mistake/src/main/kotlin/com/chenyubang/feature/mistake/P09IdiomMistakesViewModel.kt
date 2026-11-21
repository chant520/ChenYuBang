package com.chenyubang.feature.mistake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.IdiomEntity
import com.chenyubang.core.data.UserExampleWithMistake
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** P09 行尾的错题标记（未标错为 null）。 */
data class MistakeMarkView(
    val state: String,
    val tagNames: List<String>,
)

/** P09 一行录入题。 */
data class IdiomUserExampleView(
    val id: String,
    val stem: String,
    val recordedAt: String,
    val mistake: MistakeMarkView?,
)

data class P09IdiomMistakesUiState(
    val idiomId: String = "",
    val word: String = "",
    val senseLine: String = "",
    val rows: List<IdiomUserExampleView> = emptyList(),
    val loading: Boolean = true,
    val missing: Boolean = false,
    val failed: Boolean = false,
)

/**
 * P09 只读装配：先取成语本身（页名 + 释义），再订阅「该成语下全部录入题」。
 *
 * 列表含未标错的录入题，标错的才多一段错题标记；成语不存在时给 missing 兜底，不闪假数据。
 */
class P09IdiomMistakesViewModel(
    private val loadIdiom: suspend (String) -> IdiomEntity?,
    private val loadExamples: (String) -> Flow<List<UserExampleWithMistake>>,
) : ViewModel() {
    private val _state = MutableStateFlow(P09IdiomMistakesUiState())
    val state: StateFlow<P09IdiomMistakesUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun load(idiomId: String) {
        job?.cancel()
        _state.value = P09IdiomMistakesUiState(idiomId = idiomId)
        job =
            viewModelScope.launch {
                try {
                    val idiom = loadIdiom(idiomId)
                    if (idiom == null) {
                        _state.value = P09IdiomMistakesUiState(idiomId = idiomId, loading = false, missing = true)
                        return@launch
                    }
                    _state.value =
                        P09IdiomMistakesUiState(
                            idiomId = idiomId,
                            word = idiom.word,
                            senseLine = idiomSenseLine(idiom.color, idiom.sense),
                            loading = true,
                        )
                    loadExamples(idiomId).collect { rows ->
                        _state.value =
                            _state.value.copy(rows = rows.map { it.toView() }, loading = false, failed = false)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _state.value = _state.value.copy(loading = false, failed = true)
                }
            }
    }
}

private fun UserExampleWithMistake.toView(): IdiomUserExampleView {
    val mark = mistakes.firstOrNull()
    return IdiomUserExampleView(
        id = userExample.id,
        stem = userExample.stem.trim(),
        recordedAt = formatRecordedAt(userExample.createdAt),
        mistake = mark?.let { MistakeMarkView(state = it.state, tagNames = it.tagNames) },
    )
}
