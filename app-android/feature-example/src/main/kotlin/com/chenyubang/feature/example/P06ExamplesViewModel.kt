package com.chenyubang.feature.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.ExampleListItem
import com.chenyubang.core.data.PackCheckOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun interface ExampleRefreshTrigger {
    suspend fun onEnterExamples(): PackCheckOutcome
}

val NoExampleRefreshTrigger: ExampleRefreshTrigger = ExampleRefreshTrigger { PackCheckOutcome.UNCHANGED }

data class P06ExamplesUiState(
    val items: List<ExampleListItem> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val updateFailed: Boolean = false,
)

/** P06 本机列表先显示，再检查共享更新；只有成功安装才重读。 */
class P06ExamplesViewModel(
    private val loadExamples: suspend () -> List<ExampleListItem>,
    private val refreshTrigger: ExampleRefreshTrigger = NoExampleRefreshTrigger,
) : ViewModel() {
    private val _state = MutableStateFlow(P06ExamplesUiState())
    val state: StateFlow<P06ExamplesUiState> = _state.asStateFlow()
    private var entered = false

    fun enter() {
        if (entered) return
        entered = true
        viewModelScope.launch {
            readLocal(showLoading = true)
            val outcome =
                try {
                    refreshTrigger.onEnterExamples()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    PackCheckOutcome.FAILED
                }
            _state.value = _state.value.copy(updateFailed = outcome == PackCheckOutcome.FAILED)
            if (outcome == PackCheckOutcome.UPDATED) readLocal(showLoading = false)
        }
    }

    fun retry() {
        viewModelScope.launch { readLocal(showLoading = true) }
    }

    private suspend fun readLocal(showLoading: Boolean) {
        _state.value = _state.value.copy(loading = showLoading, failed = false)
        try {
            _state.value = _state.value.copy(items = loadExamples(), loading = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = _state.value.copy(items = emptyList(), loading = false, failed = true)
        }
    }
}
