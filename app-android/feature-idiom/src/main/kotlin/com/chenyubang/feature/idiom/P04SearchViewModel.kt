package com.chenyubang.feature.idiom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.IdiomEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** P04 页面级状态：输入、结果、加载、读库失败。 */
data class P04SearchUiState(
    val query: String = "",
    val results: List<SearchIdiom> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
) {
    val inputKind: SearchInputKind get() = IdiomSearchUi.classify(query)
    val showNoResult: Boolean
        get() = IdiomSearchUi.showNoResult(query, results) && !loading && !failed
}

/**
 * P04 搜索 ViewModel：边输边出结果（每次输入取消上一次在途查询，避免旧结果覆盖新结果）。
 *
 * 查询走 core-data 已有 FTS 只读方法 `IdiomDao.search(q)`，支持完整成语 / 单字 / 全拼 / 首字母。
 * 查无结果给明确空态而非空白页；已输入内容不清空，改字即时出新结果（D-v2 稿口径）。
 */
class P04SearchViewModel(
    private val search: suspend (String) -> List<IdiomEntity>,
) : ViewModel() {
    private val _state = MutableStateFlow(P04SearchUiState())
    val state: StateFlow<P04SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        searchJob?.cancel()
        _state.value = P04SearchUiState(query = query, loading = query.isNotBlank())
        if (query.isBlank()) return
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                try {
                    val rows = search(query)
                    _state.value =
                        _state.value.copy(
                            results = IdiomSearchUi.from(rows),
                            loading = false,
                            failed = false,
                        )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _state.value = _state.value.copy(results = emptyList(), loading = false, failed = true)
                }
            }
    }

    fun clear() = onQueryChange("")

    private companion object {
        /** 仅用于防抖的调度参数，不属于视觉数值；保证「边输边出」又不逐字抖动。 */
        const val SEARCH_DEBOUNCE_MS = 120L
    }
}
