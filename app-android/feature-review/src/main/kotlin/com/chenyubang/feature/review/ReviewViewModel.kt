package com.chenyubang.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.ReviewQuestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * P11 一轮复习的装配：进页重新取队列（不保留中间状态），选完立即判分并落 review_log；
 * 无法比对时不落库、不计正确数；末题判分结果常驻可见，由「下一题」按钮交出本轮小结。
 */
class ReviewViewModel(
    private val scopeIdiomId: String?,
    private val loadQueue: suspend (String?) -> List<ReviewQuestion>,
    private val record: suspend (String, String, String) -> Unit,
    private val now: () -> String = { Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() },
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState(scopeIdiomId = scopeIdiomId))
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** 单成语模式的成语名异步读取，回填到范围行与小结，不改变队列过滤本身。 */
    fun setScopeWord(word: String) {
        _state.value = _state.value.copy(scopeWord = word)
    }

    fun load() {
        val word = _state.value.scopeWord
        _state.value = ReviewUiState(scopeIdiomId = scopeIdiomId, scopeWord = word, loading = true)
        viewModelScope.launch {
            try {
                val questions = loadQueue(scopeIdiomId)
                _state.value =
                    ReviewUiState(
                        scopeIdiomId = scopeIdiomId,
                        scopeWord = word,
                        questions = questions,
                        loading = false,
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value =
                    ReviewUiState(scopeIdiomId = scopeIdiomId, scopeWord = word, loading = false, failed = true)
            }
        }
    }

    fun pick(index: Int) {
        val before = _state.value
        val after = ReviewFlow.pick(before, index)
        if (after === before) return
        _state.value = after
        val answer = after.currentAnswer
        if (answer != null && answer.verdict.comparable) {
            val result = answer.verdict.result
            val mistakeId = after.current?.mistakeId
            if (result != null && mistakeId != null) persistResult(mistakeId, result)
        }
    }

    /** 落库失败后的可重试入口：只补存当前题判分，不重置本轮作答与进度。 */
    fun retryRecord() {
        val current = _state.value.currentAnswer
        val mistakeId = _state.value.current?.mistakeId
        val result = current?.verdict?.result
        if (current?.verdict?.comparable == true && mistakeId != null && result != null) {
            persistResult(mistakeId, result)
        }
    }

    /**
     * 判分落库：先传播取消，再捕获业务异常置错误态；ReviewDao.record 是单事务，失败即整笔回滚，无需补偿写库。
     */
    private fun persistResult(mistakeId: String, result: String) {
        viewModelScope.launch {
            try {
                record(mistakeId, result, now())
                if (_state.value.recordFailed) _state.value = _state.value.copy(recordFailed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(recordFailed = true)
            }
        }
    }

    fun next() {
        _state.value = ReviewFlow.next(_state.value)
    }
}
