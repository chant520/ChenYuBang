package com.chenyubang.feature.capture

import com.chenyubang.core.data.IdiomEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * P20 候选区状态机：多选（≥1）、换一组（不改勾选）、加一个、删一个、手动挑成语追加去重。
 * 只操作候选局部状态；表单状态由 P20FormController 独立持有，二者在 ViewModel 组合。
 */
class P20CandidateController(
    private val stateFlow: MutableStateFlow<P20CandidateUiState>,
) {
    private var allIdioms: List<IdiomEntity> = emptyList()
    private var pool: List<MatchCandidate> = emptyList()
    private var checkedIds = LinkedHashSet<String>()
    private var bestId: String? = null
    private var windowStart = 0

    /** 首屏装配：默认只勾最匹配一项；勾选顺序按候选池，手动增补排末尾。 */
    fun bootstrap(
        text: String,
        idioms: List<IdiomEntity>,
    ) {
        allIdioms = idioms.sortedWith(compareByDescending<IdiomEntity> { it.frequency }.thenBy { it.word })
        val result = P20Matcher.buildPool(text, idioms)
        pool = result.candidates
        bestId = result.bestId
        windowStart = 0
        checkedIds = linkedSetOf()
        val window = P20Matcher.initialWindow(pool, bestId)
        val first = bestId ?: window.firstOrNull()?.id
        first?.let { checkedIds.add(it) }
        stateFlow.value =
            P20CandidateUiState(
                reviewText = text,
                candidates = window,
                checked = checkedRefs(),
                poolSize = pool.size,
                loading = false,
            )
    }

    /** 点候选行或已选归属标签：未选加入，已选取消；取消最后一条后保存禁用，不自动补选。 */
    fun select(id: String) {
        val current = stateFlow.value
        if (pool.none { it.id == id } && current.candidates.none { it.id == id }) return
        if (!checkedIds.add(id)) checkedIds.remove(id)
        stateFlow.update { it.copy(checked = checkedRefs(), info = null, candidateHint = null) }
    }

    /** 换一组：仅推进展示窗口（K=3），不改已勾选集合；到池尾回卷。 */
    fun cycle() {
        // 换一组仅在 canCycle（池 > K）时可点：池 ≤ K 时静默返回（防御测试直调除零）。
        // 「没有更多候选」不在此写入——它由 CandidateRows 按 !canCycle 静态派生，常显可见。
        if (pool.size <= P20Matcher.WINDOW_SIZE) return
        windowStart = (windowStart + P20Matcher.WINDOW_SIZE) % pool.size
        stateFlow.update {
            it.copy(
                candidates = pool.drop(windowStart).take(P20Matcher.WINDOW_SIZE),
                info = null,
                candidateHint = null,
            )
        }
    }

    /** 加一个：全库排除池内词，frequency DESC / word ASC 取一条追加末尾；满 50 禁用；不自动勾选。 */
    fun add() {
        if (pool.size >= P20Matcher.MAX_CANDIDATES) {
            stateFlow.update { it.copy(candidateHint = P20Matcher.CANDIDATE_LIMIT) }
            return
        }
        val inPool = pool.map { it.id }.toSet()
        val pick = allIdioms.firstOrNull { it.id !in inPool }
        if (pick == null) {
            stateFlow.update { it.copy(candidateHint = P20Matcher.NO_MORE_CANDIDATES) }
            return
        }
        val candidate = P20Matcher.addedCandidate(pick)
        pool = pool + candidate
        stateFlow.update {
            it.copy(candidates = it.candidates + candidate, poolSize = pool.size, info = null, candidateHint = null)
        }
    }

    fun toggleDeleteMode() =
        stateFlow.update { it.copy(deleteMode = !it.deleteMode, info = null, candidateHint = null) }

    /** 删一个：从池与窗口一并移除，若正是已选项则同步取消勾选；档位按剩余命中重算。 */
    fun remove(id: String) {
        pool = pool.filterNot { it.id == id }
        checkedIds.remove(id)
        if (windowStart >= pool.size) windowStart = 0
        bestId =
            pool
                .filter { it.tier == MatchTier.Best || it.tier == MatchTier.SameQuestion }
                .sortedWith(compareByDescending<MatchCandidate> { it.frequency }.thenBy { it.word })
                .firstOrNull()
                ?.id
        pool =
            pool.map { candidate ->
                when (candidate.tier) {
                    MatchTier.Semantic, MatchTier.Added, MatchTier.Manual -> candidate
                    MatchTier.Best, MatchTier.SameQuestion ->
                        candidate.copy(
                            tier = if (candidate.id == bestId) MatchTier.Best else MatchTier.SameQuestion,
                        )
                }
            }
        stateFlow.update { before ->
            val next =
                before.candidates
                    .filterNot { it.id == id }
                    .map { candidate ->
                        when (candidate.tier) {
                            MatchTier.Semantic, MatchTier.Added, MatchTier.Manual -> candidate
                            MatchTier.Best, MatchTier.SameQuestion ->
                                candidate.copy(
                                    tier = if (candidate.id == bestId) MatchTier.Best else MatchTier.SameQuestion,
                                )
                        }
                    }
            before.copy(
                candidates = next,
                checked = checkedRefs(),
                poolSize = pool.size,
                deleteMode = next.isNotEmpty() && before.deleteMode,
                info = null,
                candidateHint = null,
            )
        }
    }

    fun setManualPicker(visible: Boolean) = stateFlow.update { it.copy(showManualPicker = visible) }

    /** 手动挑成语「追加」：已在池内只补勾选，不重复；新选追加到池与窗口末尾、勾选顺序排末尾。 */
    fun pickManual(id: String) {
        stateFlow.update { it.copy(showManualPicker = false) }
        val entity = allIdioms.firstOrNull { it.id == id } ?: return
        val candidate = P20Matcher.manualCandidate(entity)
        if (pool.none { it.id == id }) pool = pool + candidate
        checkedIds.add(id)
        stateFlow.update { current ->
            current.copy(
                candidates =
                    if (current.candidates.any { it.id == id }) current.candidates else current.candidates + candidate,
                poolSize = pool.size,
                checked = checkedRefs(),
                info = null,
                candidateHint = null,
            )
        }
    }

    /** info 与表单 saveError 分属两条状态；传 null 即清除。 */
    fun setInfo(message: String?) = stateFlow.update { it.copy(info = message) }

    private fun checkedRefs(): List<IdiomRef> = pool.filter { it.id in checkedIds }.map { IdiomRef(it.id, it.word) }
}
