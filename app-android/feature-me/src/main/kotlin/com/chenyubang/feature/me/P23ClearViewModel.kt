package com.chenyubang.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.ClearData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 待拍板：P23 清空失败逐字串稿未给（视觉契约 §4 只覆盖确认主态），收口轮须替换。
const val P23_CLEAR_FAILURE_PLACEHOLDER = "清空未成功（占位·待拍板）"

data class P23ClearUiState(
    val mistakes: Int = 0,
    val favorites: Int = 0,
    val clearing: Boolean = false,
    val failed: Boolean = false,
)

/**
 * P23 清空数据：进入时只读统计会影响的条数，确认后调 ClearData.clear()（事务内清六表、留安全副本口径
 * 由数据层负责）。_cancel / 遮罩不动数据_；本 ViewModel 不碰路由。
 */
class P23ClearViewModel(
    private val database: AppDatabase,
    private val clearData: ClearData,
) : ViewModel() {
    private val _state = MutableStateFlow(P23ClearUiState())
    val state: StateFlow<P23ClearUiState> = _state.asStateFlow()

    /** 影响卡数字取实时条数（稿面 12 / 4 为排版示意）。只读查询，不改数据层。 */
    fun loadCounts() {
        viewModelScope.launch {
            try {
                val counts =
                    withContext(Dispatchers.IO) {
                        val sqlite = database.openHelper.readableDatabase
                        val mistakes = scalar(sqlite, "SELECT COUNT(*) FROM mistake")
                        val favorites = scalar(sqlite, "SELECT COUNT(*) FROM idiom_state WHERE favorite = 1")
                        mistakes to favorites
                    }
                _state.value = _state.value.copy(mistakes = counts.first, favorites = counts.second)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 统计只影响展示数字，失败时维持 0，不阻断清空主流程。
            }
        }
    }

    fun clear(onCleared: () -> Unit) {
        if (_state.value.clearing) return
        _state.value = _state.value.copy(clearing = true, failed = false)
        viewModelScope.launch {
            try {
                clearData.clear()
                onCleared()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(clearing = false, failed = true)
            }
        }
    }

    private fun scalar(
        sqlite: SupportSQLiteDatabase,
        sql: String,
    ): Int =
        sqlite.query(sql, emptyArray<Any>()).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
}
