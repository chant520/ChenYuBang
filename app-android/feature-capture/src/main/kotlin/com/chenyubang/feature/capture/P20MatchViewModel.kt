package com.chenyubang.feature.capture

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import com.chenyubang.core.data.IdiomEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * P20 候选档位。三档互斥、一词一档：最匹配 > 同题出现 > 语义相近。
 * [MatchTier.Manual] 是空态 P04 回填专用的新文案标签，沿用 CCheckRow 的 trailing 样式，不新增 token。
 * [MatchTier.Added] 是「加一个」从全库补入的词，不属于命中档位，故不显示标签。
 */
enum class MatchTier(val label: String?) {
    Best("最匹配"),
    SameQuestion("同题出现"),
    Semantic("语义相近"),
    Manual("手动选择"),
    Added(null),
}

/** 一行候选；频率只用于「最匹配」并列判定，不写库。 */
data class MatchCandidate(
    val id: String,
    val word: String,
    val frequency: Int,
    val tier: MatchTier,
)

/**
 * P20 页面级状态。勾选数恒为 1（[checkedId]），换/加/删只改候选列表：
 * [candidates] 是展示窗口（初始 K=3），[poolSize] 是完整候选池大小（决定「换一个」是否可用）。
 * 所有状态只在 ViewModel 内，不进 RouterState、不落 DB。
 */
data class P20MatchUiState(
    val reviewText: String = "",
    val candidates: List<MatchCandidate> = emptyList(),
    val checkedId: String? = null,
    val poolSize: Int = 0,
    val deleteMode: Boolean = false,
    val showManualPicker: Boolean = false,
    val info: String? = null,
    val loading: Boolean = true,
) {
    /** 空态仅两种来源：有文字但零命中、用户删光；加载中不算空态。 */
    val empty: Boolean get() = !loading && candidates.isEmpty()
    val canCycle: Boolean get() = poolSize > P20Matcher.WINDOW_SIZE
    val canAdd: Boolean get() = candidates.size < P20Matcher.MAX_CANDIDATES
}

data class P20PoolResult(
    val candidates: List<MatchCandidate>,
    val bestId: String?,
)

/** P20 页面回调打包：避免渲染层参数过多（detekt LongParameterList）。 */
data class P20MatchActions(
    val onEdit: () -> Unit,
    val onSelect: (String) -> Unit,
    val onCycle: () -> Unit,
    val onAdd: () -> Unit,
    val onToggleDelete: () -> Unit,
    val onDelete: (String) -> Unit,
    val onOpenManual: () -> Unit,
    val onDismissManual: () -> Unit,
    val onManualPick: (String) -> Unit,
    val onSave: () -> Unit,
    val onDismissInfo: () -> Unit,
    val manualPicker: @Composable (onPick: (String) -> Unit, onDismiss: () -> Unit) -> Unit,
)

/**
 * P20 候选生成的纯函数集合：全库词表扫描命中 + 三档判定 + JSON 降级。
 *
 * 不做 OCR 错字模糊容错（错字在 P19 补字兜底）；命中口径是原样子串全词命中。
 * 候选范围＝全库（调用方传 [allIdiomsForMatch] 的结果），不按考试范围过滤。
 */
object P20Matcher {
    /** 展示窗口批长：高保真稿 C P20 一屏三条。 */
    const val WINDOW_SIZE = 3
    const val MAX_CANDIDATES = 50

    const val EMPTY_TITLE = "没匹配到成语，可手动选一个"
    const val MANUAL_ACTION = "手动挑成语"
    const val NO_MORE_CANDIDATES = "没有更多候选"
    const val CANDIDATE_LIMIT = "候选最多 50 条"
    const val SAVE_INFO = "保存功能下一步开放"

    private val json = Json { ignoreUnknownKeys = true }

    /** synonyms 是 JSON 字符串列，解析失败/缺失/非法一律降级空数组，不抛到 UI。 */
    fun synonymsOf(raw: String): List<String> =
        try {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        } catch (_: Exception) {
            emptyList()
        }

    /** 题干 + 选项合并成一段可回看文字；选项带 A．B．… 前缀便于阅读，不影响命中。 */
    fun mergedText(draft: OcrDraft): String =
        buildString {
            if (draft.stem.isNotBlank()) append(draft.stem.trim())
            draft.options.forEachIndexed { index, option ->
                if (option.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(OcrOptionKeys.getOrElse(index) { "" }).append('．').append(option.trim())
                }
            }
        }

    /**
     * 命中 → 去重 → 按 word 升序取前 50；bestId 在 word 命中词里 frequency 高优先、同频 word 升序取第一条。
     * 一个词同时命中 word 与 synonyms 时只按最高档归位。
     */
    fun buildPool(
        text: String,
        idioms: List<IdiomEntity>,
    ): P20PoolResult {
        if (text.isBlank() || idioms.isEmpty()) return P20PoolResult(emptyList(), null)
        val hits = LinkedHashMap<String, Pair<IdiomEntity, Boolean>>()
        for (idiom in idioms) {
            val wordHit = idiom.word.isNotBlank() && text.contains(idiom.word)
            val synonymHit =
                !wordHit && synonymsOf(idiom.synonyms).any { it.isNotBlank() && text.contains(it) }
            if (wordHit || synonymHit) hits.putIfAbsent(idiom.id, idiom to wordHit)
        }
        val ordered = hits.values.sortedBy { it.first.word }.take(MAX_CANDIDATES)
        val bestId =
            ordered
                .filter { it.second }
                .sortedWith(
                    compareByDescending<Pair<IdiomEntity, Boolean>> { it.first.frequency }
                        .thenBy { it.first.word },
                )
                .firstOrNull()
                ?.first
                ?.id
        return P20PoolResult(
            candidates =
                ordered.map { (entity, wordHit) ->
                    MatchCandidate(
                        id = entity.id,
                        word = entity.word,
                        frequency = entity.frequency,
                        tier =
                            when {
                                entity.id == bestId -> MatchTier.Best
                                wordHit -> MatchTier.SameQuestion
                                else -> MatchTier.Semantic
                            },
                    )
                },
            bestId = bestId,
        )
    }

    /** 「加一个」从全库未展示词中取一条附加候选；无档位标签。 */
    fun addedCandidate(entity: IdiomEntity): MatchCandidate =
        MatchCandidate(entity.id, entity.word, entity.frequency, MatchTier.Added)

    /** 空态手动回填：覆盖候选区、默认勾选、标「手动选择」。 */
    fun manualCandidate(entity: IdiomEntity): MatchCandidate =
        MatchCandidate(entity.id, entity.word, entity.frequency, MatchTier.Manual)
}

/**
 * P20 ViewModel（纯状态机，无协程、无写库）。
 * 页面加载全库词表后调用 [bootstrap] 生成候选；其余操作均同步，便于单测覆盖三档/勾选/换加删/空态。
 */
class P20MatchViewModel : ViewModel() {
    private val _state = MutableStateFlow(P20MatchUiState())
    val state: StateFlow<P20MatchUiState> = _state.asStateFlow()

    private var allIdioms: List<IdiomEntity> = emptyList()
    private var pool: List<MatchCandidate> = emptyList()
    private var bestId: String? = null
    private var windowStart = 0

    /** 首屏装配：全库词表按 frequency DESC / word ASC 备好供「加一个」，再生成候选池与初始窗口。 */
    fun bootstrap(
        text: String,
        idioms: List<IdiomEntity>,
    ) {
        allIdioms = idioms.sortedWith(compareByDescending<IdiomEntity> { it.frequency }.thenBy { it.word })
        val result = P20Matcher.buildPool(text, idioms)
        pool = result.candidates
        bestId = result.bestId
        windowStart = 0
        val window = pool.take(P20Matcher.WINDOW_SIZE)
        _state.value =
            P20MatchUiState(
                reviewText = text,
                candidates = window,
                checkedId = firstCheck(window, bestId),
                poolSize = pool.size,
                loading = false,
            )
    }

    /** 用户点选某一行：勾选恒 1 条，点已勾选项不取消。 */
    fun select(id: String) {
        if (_state.value.candidates.any { it.id == id }) {
            _state.value = _state.value.copy(checkedId = id)
        }
    }

    /** 换一个：游标前进一批（K=3）取下一批替换展示，到池尾回卷池首；池 ≤K 禁用。 */
    fun cycle() {
        if (pool.size <= P20Matcher.WINDOW_SIZE) {
            _state.value = _state.value.copy(info = P20Matcher.NO_MORE_CANDIDATES)
            return
        }
        windowStart = (windowStart + P20Matcher.WINDOW_SIZE) % pool.size
        val next = pool.drop(windowStart).take(P20Matcher.WINDOW_SIZE)
        _state.value = _state.value.copy(candidates = next, checkedId = firstCheck(next, bestId), info = null)
    }

    /** 加一个：全库排除当前展示词，frequency DESC / word ASC 取一条追加末尾；满 50 禁用。 */
    fun add() {
        val current = _state.value.candidates
        if (current.size >= P20Matcher.MAX_CANDIDATES) {
            _state.value = _state.value.copy(info = P20Matcher.CANDIDATE_LIMIT)
            return
        }
        val shown = current.map { it.id }.toSet()
        val pick = allIdioms.firstOrNull { it.id !in shown }
        if (pick == null) {
            _state.value = _state.value.copy(info = P20Matcher.NO_MORE_CANDIDATES)
            return
        }
        _state.value =
            _state.value.copy(
                candidates = current + P20Matcher.addedCandidate(pick),
                info = null,
            )
    }

    fun toggleDeleteMode() {
        _state.value = _state.value.copy(deleteMode = !_state.value.deleteMode, info = null)
    }

    /** 删一个：移除该条（同时从池移除，避免换一个又带回来）；删光进空态并退出删除态。 */
    fun remove(id: String) {
        val next = _state.value.candidates.filterNot { it.id == id }
        pool = pool.filterNot { it.id == id }
        if (windowStart >= pool.size) windowStart = 0
        // 删掉当前勾选项时，在剩余 word 命中词里按「最匹配」规则重选。
        bestId =
            next
                .filter { it.tier == MatchTier.Best || it.tier == MatchTier.SameQuestion }
                .sortedWith(compareByDescending<MatchCandidate> { it.frequency }.thenBy { it.word })
                .firstOrNull()
                ?.id
        _state.value =
            _state.value.copy(
                candidates = next,
                checkedId = firstCheck(next, bestId),
                poolSize = pool.size,
                deleteMode = next.isNotEmpty() && _state.value.deleteMode,
                info = null,
            )
    }

    /** 打开/关闭手选浮层；关闭时其余状态不变。 */
    fun setManualPicker(visible: Boolean) {
        _state.value = _state.value.copy(showManualPicker = visible)
    }

    /** 选中回填：覆盖候选区、默认勾该条、标「手动选择」，关闭浮层。 */
    fun pickManual(id: String) {
        val entity = allIdioms.firstOrNull { it.id == id }
        if (entity == null) {
            setManualPicker(false)
            return
        }
        val candidate = P20Matcher.manualCandidate(entity)
        pool = listOf(candidate)
        bestId = null
        windowStart = 0
        _state.value =
            P20MatchUiState(
                reviewText = _state.value.reviewText,
                candidates = listOf(candidate),
                checkedId = candidate.id,
                poolSize = 1,
                loading = false,
            )
    }

    fun showInfo(message: String) {
        _state.value = _state.value.copy(info = message)
    }

    fun dismissInfo() {
        _state.value = _state.value.copy(info = null)
    }
}

/** 勾选跟着最匹配走；最匹配不在本批时退到本批首条，始终保证恰好一条。 */
private fun firstCheck(
    display: List<MatchCandidate>,
    bestId: String?,
): String? {
    if (display.isEmpty()) return null
    return if (bestId != null && display.any { it.id == bestId }) bestId else display.first().id
}
