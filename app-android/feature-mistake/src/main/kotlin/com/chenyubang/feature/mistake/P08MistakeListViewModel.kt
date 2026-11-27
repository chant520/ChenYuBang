package com.chenyubang.feature.mistake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.MistakeIdiomGroup
import com.chenyubang.core.data.MistakeOverview
import com.chenyubang.core.data.MistakeWithIdioms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** P08 列表一行（按录入时间 / 按考试类型共用）。 */
data class MistakeItemView(
    val id: String,
    val stem: String,
    val recordedAt: String,
    val idiomWords: List<String>,
    val examType: String?,
    val userExampleId: String? = null,
)

/** P08「按成语分组」一行。 */
data class MistakeIdiomGroupView(
    val idiomId: String,
    val word: String,
    val count: Int,
    val reasonLine: String?,
)

data class P08MistakeListUiState(
    val total: Int = 0,
    val pending: Int = 0,
    val groups: List<MistakeIdiomGroupView> = emptyList(),
    val mistakes: List<MistakeItemView> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * P08 只读装配：总览 / 分组 / 错题列表三条查询并发订阅。
 *
 * 总览与未来 P02「我的错题」卡同源（都是 MistakeQueryDao.overview），本页不另算一遍；
 * 分组行的错因分布由已取到的错题 tagNames 现场汇总，不额外查库。
 */
private const val STOP_TIMEOUT_MS = 5_000L

class P08MistakeListViewModel(
    loadOverview: () -> Flow<MistakeOverview>,
    loadGroups: () -> Flow<List<MistakeIdiomGroup>>,
    loadMistakes: () -> Flow<List<MistakeWithIdioms>>,
) : ViewModel() {
    val state: StateFlow<P08MistakeListUiState> =
        combine(loadOverview(), loadGroups(), loadMistakes()) { overview, groups, mistakes ->
            buildState(overview, groups, mistakes)
        }.catch {
            emit(P08MistakeListUiState(loading = false, failed = true))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = P08MistakeListUiState(),
        )

    internal fun buildState(
        overview: MistakeOverview,
        groups: List<MistakeIdiomGroup>,
        mistakes: List<MistakeWithIdioms>,
    ): P08MistakeListUiState {
        val items =
            mistakes.map { row ->
                MistakeItemView(
                    id = row.mistake.id,
                    stem = row.mistake.stem.trim(),
                    recordedAt = formatRecordedAt(row.mistake.createdAt),
                    idiomWords = row.idioms.map { it.word },
                    examType = row.mistake.examType,
                    userExampleId = row.mistake.userExampleId,
                )
            }
        // 一次遍历把每道错题的 tag 按关联成语铺开，分组行 O(1) 查表；避免 G×M 嵌套扫描。
        val tagsByIdiom = HashMap<String, MutableList<String>>()
        mistakes.forEach { row ->
            row.idioms.forEach { idiom ->
                tagsByIdiom.getOrPut(idiom.id) { mutableListOf() }.addAll(row.mistake.tagNames)
            }
        }
        val grouped =
            groups.map { group ->
                MistakeIdiomGroupView(
                    idiomId = group.idiomId,
                    word = group.word,
                    count = group.mistakeCount,
                    reasonLine = tagSummaryLine(tagsByIdiom[group.idiomId].orEmpty()),
                )
            }
        return P08MistakeListUiState(
            total = overview.total,
            pending = overview.pending,
            groups = grouped,
            mistakes = items,
            loading = false,
        )
    }
}
