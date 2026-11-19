package com.chenyubang.feature.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.ExampleEntity
import com.chenyubang.core.data.IdiomWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** P07 页面级状态：详情 / 加载 / 找不到该题 / 读库失败，四态互斥。 */
data class P07ExampleDetailUiState(
    val detail: ExampleDetailView? = null,
    val loading: Boolean = true,
    val missing: Boolean = false,
    val failed: Boolean = false,
)

/**
 * P07 例题详情 ViewModel：例题与关联成语来自 core-data 只读查询（exampleDao + idiomDao.allWords）。
 *
 * [relatedIdiomIds] 是本题关联的全部成语 id（B3 一题多成语，验收 6），交 UI 渲染「关联成语区」；
 * [wordIndex] 是成语 id → 词的全量词表，交 [IdiomHighlight.match] 做题干/选项/解析高亮（验收 5）。
 * 关联成语为空时列表为空（UI 显示「暂未关联成语」，题干与解析照常显示，不给按钮，§5.4）。
 */
class P07ExampleDetailViewModel(
    private val loadExample: suspend (String) -> ExampleEntity?,
    private val loadLinks: suspend (String) -> List<com.chenyubang.core.data.ExampleIdiomEntity>,
    private val loadIdiomWords: suspend () -> List<IdiomWord>,
) : ViewModel() {
    private val _state = MutableStateFlow(P07ExampleDetailUiState())
    val state: StateFlow<P07ExampleDetailUiState> = _state.asStateFlow()

    private val _relatedIdiomIds = MutableStateFlow<List<String>>(emptyList())
    val relatedIdiomIds: StateFlow<List<String>> = _relatedIdiomIds.asStateFlow()

    private val _wordIndex = MutableStateFlow<Map<String, String>>(emptyMap())
    val wordIndex: StateFlow<Map<String, String>> = _wordIndex.asStateFlow()

    fun load(id: String) {
        _state.value = P07ExampleDetailUiState(loading = true)
        _relatedIdiomIds.value = emptyList()
        viewModelScope.launch {
            try {
                val entity = loadExample(id)
                if (entity == null) {
                    _state.value = P07ExampleDetailUiState(loading = false, missing = true)
                    return@launch
                }
                _state.value = P07ExampleDetailUiState(detail = ExampleDetail.viewOf(entity), loading = false)
                _relatedIdiomIds.value = loadLinks(id).map { it.idiomId }
                _wordIndex.value = loadIdiomWords().associate { it.id to it.word }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = P07ExampleDetailUiState(loading = false, failed = true)
            }
        }
    }
}
