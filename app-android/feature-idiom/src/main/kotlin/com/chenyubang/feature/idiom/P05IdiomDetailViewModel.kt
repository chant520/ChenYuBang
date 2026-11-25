package com.chenyubang.feature.idiom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.ExampleEntity
import com.chenyubang.core.data.IdiomEntity
import com.chenyubang.core.data.IdiomLearningState
import com.chenyubang.core.data.IdiomLearningStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** P05 页面级状态：详情 / 关联系统真题 / 加载 / 找不到该成语 / 读库失败，四态互斥。 */
data class P05IdiomDetailUiState(
    val detail: IdiomDetailView? = null,
    val examples: List<IdiomExampleView> = emptyList(),
    /** A6 自评 + A7 收藏当前值；缺行由 DAO.state 投影补未学、未收藏。 */
    val state: IdiomLearningState = IdiomLearningState("", IdiomLearningStatus.UNLEARNED, false),
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
    private val loadState: suspend (String) -> IdiomLearningState = {
        IdiomLearningState(it, IdiomLearningStatus.UNLEARNED, false)
    },
    private val writeStatus: suspend (String, IdiomLearningStatus) -> Unit = { _, _ -> },
    private val writeFavorite: suspend (String, Boolean) -> Unit = { _, _ -> },
) : ViewModel() {
    private val _state = MutableStateFlow(P05IdiomDetailUiState())
    val state: StateFlow<P05IdiomDetailUiState> = _state.asStateFlow()

    private var currentId: String = ""

    fun load(id: String) {
        currentId = id
        _state.value = P05IdiomDetailUiState()
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
                            state = loadState(id),
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

    /** A6 自评：写入后从 DAO 回读刷新，保证缺行/并发写入后的值一致。 */
    fun updateStatus(status: IdiomLearningStatus) {
        val id = currentId
        if (id.isBlank()) return
        viewModelScope.launch {
            writeStatus(id, status)
            _state.value = _state.value.copy(state = loadState(id))
        }
    }

    /** A7 收藏：写入后回读刷新，收藏标签与 P24/P13 计数走同一 DAO。 */
    fun updateFavorite(favorite: Boolean) {
        val id = currentId
        if (id.isBlank()) return
        viewModelScope.launch {
            writeFavorite(id, favorite)
            _state.value = _state.value.copy(state = loadState(id))
        }
    }
}
