package com.chenyubang.feature.mistake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.ExampleEditInput
import com.chenyubang.core.data.MistakeReviewState
import com.chenyubang.core.data.MistakeStateUpdateResult
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
    private val setReviewState: suspend (String, MistakeReviewState) -> MistakeStateUpdateResult =
        { _, _ -> MistakeStateUpdateResult.NOT_FOUND },
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
        if (!_state.value.canEdit) return
        _state.value = P10Transitions.toggleAnswer(_state.value)
    }

    fun startReroll() {
        if (!_state.value.canEdit) return
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
        val current = _state.value
        if (current.edit.saving || current.edit.deleting) return
        val edit = current.edit
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
        if (_state.value.edit.saving) return
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
        if (_state.value.edit.saving) return
        _state.value = _state.value.copy(edit = P10EditTransitions.addTag(_state.value.edit))
    }

    fun addIdiom(id: String) = changeDraft { P10EditTransitions.addIdiom(it, id) }

    fun addNextIdiom() = changeDraft { P10EditTransitions.nextIdiom(it, _state.value.edit.idiomCandidates) }

    fun setIdiomQuery(query: String) {
        if (_state.value.edit.saving) return
        _state.value = _state.value.copy(edit = _state.value.edit.copy(idiomQuery = query))
    }

    fun requestDelete(target: P10DeleteTarget) {
        if (!_state.value.canDelete) return
        _state.value = _state.value.copy(edit = _state.value.edit.copy(deleteConfirm = target))
    }

    fun dismissDelete() {
        if (_state.value.edit.deleting) return
        _state.value = _state.value.copy(edit = _state.value.edit.copy(deleteConfirm = null))
    }

    /** 系统返回：确认弹层优先收起，其次等同「取消」编辑；只读态返回 false 交给路由返回。 */
    fun handleBack(): Boolean {
        val edit = _state.value.edit
        return when {
            edit.deleteConfirm != null -> {
                dismissDelete()
                true
            }
            edit.editing && !edit.saving && !edit.deleting -> {
                cancelEdit()
                true
            }
            edit.saving || edit.deleting || _state.value.updatingReviewState -> true
            else -> false
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(edit = _state.value.edit.copy(pageMessage = null, errorMessage = null))
    }

    @Suppress("ReturnCount")
    fun save() {
        val current = _state.value
        val draft = current.edit.draft ?: return
        if (!current.edit.editing || current.edit.errors.hasInvalid) return
        if (current.edit.saving || current.edit.deleting || current.updatingReviewState) return
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
        val current = _state.value
        val target = current.edit.deleteConfirm
        val id = current.detail?.id
        if (target == null || id == null) return
        if (current.edit.deleting || current.edit.saving || current.updatingReviewState) return
        _state.value = current.copy(edit = current.edit.copy(deleting = true))
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
                                deleting = false,
                                deleteConfirm = null,
                                errorMessage = P10EditTransitions.DELETE_FAILED,
                            ),
                    )
            }
        }
    }

    /** C9 手动两态：写前锁门，成功后重读最新 mistake 并通知来源页；失败不猜改本地状态。 */
    fun changeReviewState(state: MistakeReviewState) {
        val current = _state.value
        val detail = current.detail
        val mistakeId = detail?.mistakeId
        if (detail == null || mistakeId == null) return
        if (!current.canChangeReviewState || detail.reviewState == state) return
        _state.value =
            current.copy(
                updatingReviewState = true,
                edit = current.edit.copy(errorMessage = null, pageMessage = null),
            )
        viewModelScope.launch {
            try {
                when (setReviewState(mistakeId, state)) {
                    MistakeStateUpdateResult.NOT_FOUND ->
                        _state.value =
                            _state.value.copy(
                                updatingReviewState = false,
                                edit = _state.value.edit.copy(errorMessage = P10EditTransitions.MISTAKE_NOT_FOUND),
                            )
                    MistakeStateUpdateResult.UPDATED -> {
                        onDataChanged()
                        load(detail.id)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value =
                    _state.value.copy(
                        updatingReviewState = false,
                        edit = _state.value.edit.copy(errorMessage = P10EditTransitions.REVIEW_STATE_FAILED),
                    )
            }
        }
    }
}
