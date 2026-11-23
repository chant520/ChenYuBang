package com.chenyubang.feature.mistake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.ExampleEditInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * P10 装配：只读详情 + 原图可读判断 + 2.8 编辑/删除动作。
 * 所有写库口子由 app 层注入（DAO / filesDir），页面与快照测试都不依赖 Android 数据库。
 */
@Suppress("TooManyFunctions")
class P10UserExampleViewModel(
    private val loadDetail: suspend (String) -> P10UserExampleDetail?,
    private val isImageReadable: suspend (String) -> Boolean,
    private val loadIdiomCandidates: suspend () -> List<P10IdiomRef> = { emptyList() },
    private val saveEdit: suspend (ExampleEditInput) -> Unit = {},
    private val deleteExample: suspend (String, P10DeleteTarget) -> Unit = { _, _ -> },
    private val onDataChanged: () -> Unit = {},
    private val onExit: () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(P10UserExampleUiState())
    val state: StateFlow<P10UserExampleUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun load(id: String) {
        job?.cancel()
        _state.value = P10UserExampleUiState()
        job =
            viewModelScope.launch {
                try {
                    val detail = loadDetail(id)
                    if (detail == null) {
                        _state.value = P10UserExampleUiState(loading = false, missing = true)
                        return@launch
                    }
                    val unreadable = detail.imagePath?.let { !isImageReadable(it) } ?: false
                    _state.value = P10UserExampleUiState(loading = false, detail = detail, imageUnreadable = unreadable)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _state.value = P10UserExampleUiState(loading = false, failed = true)
                }
            }
    }

    fun toggleAnswer() {
        if (_state.value.edit.editing) return
        _state.value = P10Transitions.toggleAnswer(_state.value)
    }

    fun startReroll() {
        if (_state.value.edit.editing) return
        _state.value = P10Transitions.startReroll(_state.value)
    }

    fun pick(index: Int) {
        _state.value = P10Transitions.pick(_state.value, index)
    }

    fun check() {
        _state.value = P10Transitions.check(_state.value)
    }

    fun exitReroll() {
        _state.value = P10Transitions.exitReroll(_state.value)
    }

    fun startEdit() {
        val current = _state.value
        if (!current.canEdit || current.detail == null) return
        _state.value =
            current.copy(
                rerolling = false,
                picked = null,
                checked = false,
                edit =
                    P10EditState(
                        editing = true,
                        draft = P10EditTransitions.draftOf(current.detail!!),
                    ),
            )
        viewModelScope.launch {
            val candidates =
                try {
                    loadIdiomCandidates()
                } catch (_: Exception) {
                    emptyList()
                }
            _state.value = _state.value.copy(edit = _state.value.edit.copy(idiomCandidates = candidates))
        }
    }

    fun cancelEdit() {
        val edit = _state.value.edit
        _state.value =
            _state.value.copy(
                edit =
                    edit.copy(
                        editing = false,
                        draft = null,
                        errors = P10EditFieldErrors(),
                        saving = false,
                    ),
            )
    }

    fun changeDraft(edit: (P10EditDraft) -> P10EditDraft) {
        val next = P10EditTransitions.change(_state.value.edit, edit)
        _state.value = _state.value.copy(edit = next)
    }

    fun setOption(
        index: Int,
        value: String,
    ) = changeDraft { draft ->
        draft.copy(options = draft.options.toMutableList().also { if (index in it.indices) it[index] = value })
    }

    fun addTag() {
        _state.value = _state.value.copy(edit = P10EditTransitions.addTag(_state.value.edit))
    }

    fun addIdiom(id: String) = changeDraft { P10EditTransitions.addIdiom(it, id) }

    fun addNextIdiom() = changeDraft { P10EditTransitions.nextIdiom(it, _state.value.edit.idiomCandidates) }

    fun setIdiomQuery(query: String) {
        _state.value = _state.value.copy(edit = _state.value.edit.copy(idiomQuery = query))
    }

    fun requestDelete(target: P10DeleteTarget) {
        if (!_state.value.canDelete) return
        _state.value = _state.value.copy(edit = _state.value.edit.copy(deleteConfirm = target))
    }

    fun dismissDelete() {
        _state.value = _state.value.copy(edit = _state.value.edit.copy(deleteConfirm = null))
    }

    /** 系统返回：确认弹层优先收起，其次等同「取消」编辑；只读态返回 false 交给路由返回。 */
    fun handleBack(): Boolean {
        val edit = _state.value.edit
        when {
            edit.deleteConfirm != null -> dismissDelete()
            edit.editing -> cancelEdit()
            else -> return false
        }
        return true
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(edit = _state.value.edit.copy(pageMessage = null, errorMessage = null))
    }

    @Suppress("ReturnCount")
    fun save() {
        val current = _state.value
        val draft = current.edit.draft ?: return
        if (current.edit.errors.hasInvalid || current.edit.saving) return
        val message = P10EditTransitions.pageMessage(draft)
        if (message != null) {
            _state.value = current.copy(edit = current.edit.copy(pageMessage = message))
            return
        }
        val id = current.detail?.id ?: return
        val input = P10EditTransitions.toInput(id, draft)
        _state.value = current.copy(edit = current.edit.copy(saving = true, pageMessage = null, errorMessage = null))
        viewModelScope.launch {
            try {
                saveEdit(input)
                onDataChanged()
                load(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        edit = _state.value.edit.copy(saving = false, errorMessage = P10EditTransitions.SAVE_FAILED),
                    )
            }
        }
    }

    fun confirmDelete() {
        val target = _state.value.edit.deleteConfirm ?: return
        val id = _state.value.detail?.id ?: return
        viewModelScope.launch {
            try {
                deleteExample(id, target)
                onDataChanged()
                onExit()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        edit =
                            _state.value.edit.copy(
                                deleteConfirm = null,
                                errorMessage = P10EditTransitions.DELETE_FAILED,
                            ),
                    )
            }
        }
    }
}
