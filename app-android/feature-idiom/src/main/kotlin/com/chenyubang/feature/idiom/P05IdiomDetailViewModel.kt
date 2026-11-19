package com.chenyubang.feature.idiom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.ExampleEntity
import com.chenyubang.core.data.IdiomEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** P05 页面级状态：详情 / 关联系统真题 / 加载 / 找不到该成语 / 读库失败，四态互斥。 */
data class P05IdiomDetailUiState(
    val detail: IdiomDetailView? = null,
    val examples: List<IdiomExampleView> = emptyList(),
    val loading: Boolean = true,
    val missing: Boolean = false,
    val failed: Boolean = false,
)

/**
 * P05 成语详情 ViewModel：词义与出处等数据来自 core-data 只读查询（byId）。
 *
 * A4「相关题目 · 系统题库」按 example_idiom 关联读真题（1.6）；A5「我的录入题 N」仍只占位，
 * 待阶段 2 `user_example` 接入，此处不读 user_example，也不生成任何题目内容。
 */
class P05IdiomDetailViewModel(
    private val loadIdiom: suspend (String) -> IdiomEntity?,
    private val loadExamples: suspend (String) -> List<ExampleEntity>,
) : ViewModel() {
    private val _state = MutableStateFlow(P05IdiomDetailUiState())
    val state: StateFlow<P05IdiomDetailUiState> = _state.asStateFlow()

    fun load(id: String) {
        _state.value = P05IdiomDetailUiState(loading = true)
        viewModelScope.launch {
            try {
                val entity = loadIdiom(id)
                _state.value =
                    if (entity == null) {
                        P05IdiomDetailUiState(loading = false, missing = true)
                    } else {
                        P05IdiomDetailUiState(
                            detail = IdiomDetail.viewOf(entity),
                            examples = loadExamples(id).map(IdiomExamples::viewOf),
                            loading = false,
                        )
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = P05IdiomDetailUiState(loading = false, failed = true)
            }
        }
    }
}
