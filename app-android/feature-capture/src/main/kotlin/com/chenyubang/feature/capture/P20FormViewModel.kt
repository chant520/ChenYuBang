package com.chenyubang.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * P20 页面状态容器：候选区与表单分别由两个独立控制器持有，本类只负责组合与生命周期。
 * 页面通过 [candidate] / [form] 分发用户动作，通过 [state] 观察合并后的页面状态。
 */
class P20FormViewModel(
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val stateScope: CoroutineScope = scope ?: viewModelScope
    private val candidateState = MutableStateFlow(P20CandidateUiState())
    private val formState = MutableStateFlow(P20FormState())

    val candidate: P20CandidateController = P20CandidateController(candidateState)
    val form: P20FormController = P20FormController(formState)

    val state: StateFlow<P20CaptureState> =
        combine(candidateState, formState) { candidate, form ->
            P20CaptureState(
                reviewText = candidate.reviewText,
                candidates = candidate.candidates,
                checked = candidate.checked,
                poolSize = candidate.poolSize,
                deleteMode = candidate.deleteMode,
                showManualPicker = candidate.showManualPicker,
                info = candidate.info,
                loading = candidate.loading,
                form = form,
            )
        }.stateIn(stateScope, SharingStarted.Eagerly, P20CaptureState())
}
