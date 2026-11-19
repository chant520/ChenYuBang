package com.chenyubang.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.ExamScopeSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** P14 页面状态：草稿 + 可选省份 + 保存进度。 */
data class P14ExamScopeUiState(
    val initial: ExamScopeSelection = ExamScopeSelection(),
    val draft: ExamScopeDraft = ExamScopeDraft(),
    val availableProvinces: List<String> = emptyList(),
    val saving: Boolean = false,
    val failed: Boolean = false,
) {
    val dirty: Boolean get() = draft.toSelection() != initial
}

/**
 * P14 考试范围设置 ViewModel（E1）。
 *
 * 只维护草稿与保存动作，不直接碰库：写入由 [ExamScopeSaver] 注入（app 层写 `exam_scope`
 * 并重建 `idiom_scope`），避免 feature 模块承担编排。
 */
class P14ExamScopeViewModel(
    private val saver: ExamScopeSaver,
) : ViewModel() {
    private val _state = MutableStateFlow(P14ExamScopeUiState())
    val state: StateFlow<P14ExamScopeUiState> = _state.asStateFlow()

    /** 每次进入页面重置草稿；未保存改动因此不会跨次保留（IA 5.2）。 */
    fun reset(
        initial: ExamScopeSelection,
        availableProvinces: List<String>,
    ) {
        _state.value =
            P14ExamScopeUiState(
                initial = initial,
                draft = ExamScopeDraft.from(initial),
                availableProvinces = availableProvinces,
            )
    }

    fun toggleType(type: ExamScopeType) {
        if (_state.value.saving) return
        val current = _state.value
        _state.value =
            current.copy(draft = current.draft.toggle(type, current.availableProvinces), failed = false)
    }

    fun toggleProvince(province: String) {
        if (_state.value.saving) return
        val current = _state.value
        _state.value = current.copy(draft = current.draft.toggleProvince(province), failed = false)
    }

    /**
     * 保存并回调 [onSaved]；[complete] 用于 P01 首启（写 `firstLaunch.complete`）。
     * 保存失败保留当前草稿并置 [P14ExamScopeUiState.failed]，由页面给橙提示重试。
     */
    fun save(
        complete: Boolean,
        onSaved: (ExamScopeSelection) -> Unit,
    ) {
        val current = _state.value
        if (current.saving) return
        _state.value = current.copy(saving = true, failed = false)
        viewModelScope.launch {
            try {
                val saved = current.draft.toSelection()
                saver.save(saved, complete)
                _state.value = _state.value.copy(saving = false, initial = saved)
                onSaved(saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(saving = false, failed = true)
            }
        }
    }
}
