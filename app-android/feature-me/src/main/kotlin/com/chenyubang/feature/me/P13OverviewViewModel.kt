package com.chenyubang.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.MistakeOverview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** E2 五项统计；全空自然为 0，不做成长体系。 */
data class P13OverviewStats(
    /** 已学成语：idiom_state status IN (学过,已掌握)。 */
    val learned: Int = 0,
    /** 收藏：idiom_state favorite=1，与 P24 列表同源。 */
    val favorites: Int = 0,
    /** 错题总数：mistake 主表按 mistake.id 计。 */
    val mistakes: Int = 0,
    /** 待复习：mistake.state='待复习'。 */
    val pending: Int = 0,
    /** 已掌握：idiom_state status='已掌握'，非错题 C9 掌握态。 */
    val mastered: Int = 0,
)

/**
 * P13 E2 统计 ViewModel：三计数取 idiom_state，错题/待复习取 mistake.overview()。
 * 只做只读聚合，不写任何数据；取数失败保留全 0，不给页面加错误态（E2 是概览区）。
 */
class P13OverviewViewModel(
    private val loadLearned: suspend () -> Int,
    private val loadMastered: suspend () -> Int,
    private val loadFavorite: suspend () -> Int,
    private val loadMistakeOverview: suspend () -> MistakeOverview,
) : ViewModel() {
    private val _state = MutableStateFlow(P13OverviewStats())
    val state: StateFlow<P13OverviewStats> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                val overview = loadMistakeOverview()
                _state.value =
                    P13OverviewStats(
                        learned = loadLearned(),
                        favorites = loadFavorite(),
                        mistakes = overview.total,
                        pending = overview.pending,
                        mastered = loadMastered(),
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = P13OverviewStats()
            }
        }
    }
}
