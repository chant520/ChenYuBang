package com.chenyubang.feature.idiom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.IdiomEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** P05 页面级状态：详情 / 加载 / 找不到该成语 / 读库失败，四态互斥。 */
data class P05IdiomDetailUiState(
    val detail: IdiomDetailView? = null,
    val loading: Boolean = true,
    val missing: Boolean = false,
    val failed: Boolean = false,
)

/**
 * P05 成语详情 ViewModel：词义与出处等数据来自 core-data 只读查询（byId）。
 *
 * A4「相关题目」双来源与 A5「我的录入题 N」在本期只占位：数据分别待 1.6 关联真题与
 * 阶段 2 `user_example` 接入，故此处不读 example / user_example，也不生成任何题目内容。
 */
class P05IdiomDetailViewModel(
    private val loadIdiom: suspend (String) -> IdiomEntity?,
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
                        P05IdiomDetailUiState(detail = IdiomDetail.viewOf(entity), loading = false)
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = P05IdiomDetailUiState(loading = false, failed = true)
            }
        }
    }
}
