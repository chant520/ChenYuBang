package com.chenyubang.feature.idiom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.FavoriteIdiom
import com.chenyubang.core.data.IdiomLearningStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** P24 收藏列表一行：收藏成语展示字段 + 三态。 */
data class FavoriteIdiomView(
    val idiomId: String,
    val word: String,
    val pinyin: String,
    val frequency: Int,
    val status: IdiomLearningStatus,
) {
    companion object {
        fun of(row: FavoriteIdiom): FavoriteIdiomView =
            FavoriteIdiomView(
                idiomId = row.idiomId,
                word = row.word,
                pinyin = row.pinyin,
                frequency = row.frequency,
                status = IdiomLearningStatus.fromStored(row.status),
            )
    }
}

data class P24FavoriteUiState(
    val items: List<FavoriteIdiomView> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    val isEmpty: Boolean get() = !loading && !failed && items.isEmpty()
}

/**
 * P24 ViewModel：只读取 favoriteIdioms()（JOIN idiom，排除未收藏/缺省行）。
 * 本页不做取消收藏——取消统一在 P05 顶部收藏按钮完成，返回本页按 revision 重新读取，
 * 从而实现「即时移除 + P13 计数联动」且不重复两套写入口。
 */
class P24FavoriteViewModel(
    private val loadFavorite: suspend () -> List<FavoriteIdiom>,
) : ViewModel() {
    private val _state = MutableStateFlow(P24FavoriteUiState())
    val state: StateFlow<P24FavoriteUiState> = _state.asStateFlow()

    fun load() {
        _state.value = P24FavoriteUiState()
        viewModelScope.launch {
            try {
                _state.value = P24FavoriteUiState(items = loadFavorite().map(FavoriteIdiomView::of), loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = P24FavoriteUiState(loading = false, failed = true)
            }
        }
    }
}
