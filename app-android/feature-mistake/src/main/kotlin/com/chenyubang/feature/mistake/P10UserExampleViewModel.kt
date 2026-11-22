package com.chenyubang.feature.mistake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * P10 只读装配：加载用户录入题详情（含关联成语与最新错题记录）+ 原图可读判断。
 * loader 由 app 层注入（DAO / filesDir），页面与快照测试都不依赖 Android 数据库。
 */
class P10UserExampleViewModel(
    private val loadDetail: suspend (String) -> P10UserExampleDetail?,
    private val isImageReadable: suspend (String) -> Boolean,
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
        _state.value = P10Transitions.toggleAnswer(_state.value)
    }

    fun startReroll() {
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
}
