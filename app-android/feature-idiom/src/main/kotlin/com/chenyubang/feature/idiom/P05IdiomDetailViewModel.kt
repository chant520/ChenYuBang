package com.chenyubang.feature.idiom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.ExampleEntity
import com.chenyubang.core.data.IdiomEntity
import com.chenyubang.core.data.IdiomLearningState
import com.chenyubang.core.data.IdiomLearningStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    /** A5：该成语下全部录入题数（含未标错），N=0 时入口灰显不可点。 */
    val userExampleCount: Int = 0,
    /** D1 单成语复习入口门控：该成语下当前待复习错题数。 */
    val pendingCount: Int = 0,
    /** A6/A7 写库失败提示；成功后自动清除。 */
    val updateFailed: Boolean = false,
    val loading: Boolean = true,
    val missing: Boolean = false,
    val failed: Boolean = false,
)

/**
 * P05 成语详情 ViewModel：词义与出处等数据来自 core-data 只读查询（byId）。
 *
 * A4「相关题目 · 系统题库」按 example_idiom 关联读真题（1.6）；A5「我的录入题 N」按
 * user_example_idiom 统计该成语下全部录入题（含未标错），只取计数、不生成题目内容。
 */
class P05IdiomDetailViewModel(
    private val loadIdiom: suspend (String) -> IdiomEntity?,
    private val loadExamples: suspend (String) -> List<ExampleEntity>,
    private val countUserExamples: suspend (String) -> Int = { 0 },
    private val loadState: suspend (String) -> IdiomLearningState = {
        IdiomLearningState(it, IdiomLearningStatus.UNLEARNED, false)
    },
    private val writeStatus: suspend (String, IdiomLearningStatus) -> Unit = { _, _ -> },
    private val writeFavorite: suspend (String, Boolean) -> Unit = { _, _ -> },
    private val loadPendingCount: suspend (String) -> Int = { 0 },
) : ViewModel() {
    private val _state = MutableStateFlow(P05IdiomDetailUiState())
    val state: StateFlow<P05IdiomDetailUiState> = _state.asStateFlow()

    private var currentId: String = ""

    /** 单 Job：复习点击重查取消上一次在途请求，替代 Composable 里的 Mutex。 */
    private var pendingCountJob: Job? = null

    fun load(id: String) {
        currentId = id
        pendingCountJob?.cancel()
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
                            userExampleCount = countUserExamples(id),
                            pendingCount = loadPendingCount(id),
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

    /** revision 变化只刷新待复习计数，不重置整页详情（与旧实现语义一致）。 */
    fun refreshPendingCount() {
        val id = currentId
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(pendingCount = loadPendingCount(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 刷新失败保留上次计数，不打断页面。
            }
        }
    }

    /** A6 自评：写入后从 DAO 回读刷新，保证缺行/并发写入后的值一致；写失败只提示、不改旧值。 */
    fun updateStatus(status: IdiomLearningStatus) {
        val id = currentId
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                writeStatus(id, status)
                _state.value = _state.value.copy(state = loadState(id), updateFailed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(updateFailed = true)
            }
        }
    }

    /** A7 收藏：写入后回读刷新，收藏标签与 P24/P13 计数走同一 DAO；失败处理同 A6。 */
    fun updateFavorite(favorite: Boolean) {
        val id = currentId
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                writeFavorite(id, favorite)
                _state.value = _state.value.copy(state = loadState(id), updateFailed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(updateFailed = true)
            }
        }
    }

    /**
     * D1 复习入口：点击时重查最新待复习数，避免展示计数与当前队列之间发生掌握状态变化。
     * 单 Job 取消旧请求；重查失败保留上次计数且不触发跳转。
     */
    fun requestReview(onReady: (Int) -> Unit) {
        val id = currentId
        if (id.isBlank()) return
        pendingCountJob?.cancel()
        pendingCountJob =
            viewModelScope.launch {
                try {
                    val latest = loadPendingCount(id)
                    if (currentId == id) {
                        _state.value = _state.value.copy(pendingCount = latest)
                        if (latest > 0) onReady(latest)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 重查失败：保留已展示计数，不跳转。
                }
            }
    }
}
