package com.chenyubang.feature.idiom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.ExamScopeSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** P03 页面级状态（信息架构 v1.2 P03 ②–⑦）。 */
data class P03LibraryUiState(
    val scope: ExamScopeSelection = ExamScopeSelection(),
    val dimension: LibraryDimension = LibraryDimension.Scope,
    val selectedLetter: String? = null,
    val letters: List<String> = emptyList(),
    val page: LibraryPage = LibraryPage(emptyList()),
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    val scopeEmpty: Boolean get() = scope.isEmpty
    val conditionCleared: Boolean get() = dimension == LibraryDimension.Scope && selectedLetter == null
}

/**
 * P03 成语库 ViewModel：进页拉取触发点 + 只读列表装配。
 *
 * - 进页/范围变更时调用 [load]：先走 [LibraryRefreshTrigger]（阶段 1 为空操作），再从库读全量。
 * - 维度与字母只在前端做筛选/排序/分组，不再查库（§4.2 一次性进内存）。
 * - 读库失败给 [P03LibraryUiState.failed]，页面按橙＝可恢复给重试（§9.1）。
 */
class P03LibraryViewModel(
    private val loadIdioms: suspend () -> List<LibraryIdiom>,
    private val refreshTrigger: LibraryRefreshTrigger = NoLibraryRefreshTrigger,
) : ViewModel() {
    private val _state = MutableStateFlow(P03LibraryUiState())
    val state: StateFlow<P03LibraryUiState> = _state.asStateFlow()

    private var items: List<LibraryIdiom> = emptyList()

    /** 进入本页时调用；[scope] 变化时同样重取（范围保存后 `idiom_scope` 已重建）。 */
    fun load(scope: ExamScopeSelection) {
        _state.value = _state.value.copy(scope = scope, loading = true, failed = false)
        viewModelScope.launch {
            try {
                refreshTrigger.onEnterLibrary()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 拉取失败只影响题库版本，回落内置包；浏览不阻断（§4.4），重试入口在 P16。
            }
            try {
                items = loadIdioms()
                _state.value =
                    _state.value.copy(
                        letters = LibraryBrowse.letters(items),
                        page = LibraryBrowse.page(items, _state.value.dimension, _state.value.selectedLetter),
                        loading = false,
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                items = emptyList()
                _state.value = _state.value.copy(loading = false, failed = true, page = LibraryPage(emptyList()))
            }
        }
    }

    fun selectDimension(dimension: LibraryDimension) {
        _state.value = _state.value.copy(dimension = dimension)
        applyPage()
    }

    /** 点索引字母：重复点同一字母取消该字母条件。 */
    fun selectLetter(letter: String) {
        val next = if (_state.value.selectedLetter == letter) null else letter
        _state.value = _state.value.copy(selectedLetter = next)
        applyPage()
    }

    /** 空态的「清除条件，看全部」。 */
    fun clearConditions() {
        _state.value = _state.value.copy(dimension = LibraryDimension.Scope, selectedLetter = null)
        applyPage()
    }

    fun retry() = load(_state.value.scope)

    private fun applyPage() {
        val current = _state.value
        _state.value = current.copy(page = LibraryBrowse.page(items, current.dimension, current.selectedLetter))
    }
}
