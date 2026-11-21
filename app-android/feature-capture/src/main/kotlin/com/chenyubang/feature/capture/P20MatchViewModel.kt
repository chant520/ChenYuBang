package com.chenyubang.feature.capture

import androidx.compose.runtime.Composable
import com.chenyubang.core.data.IdiomEntity
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
    const val NO_TEXT = "（无识别文字）"
    const val READ_FAILED = "题库读取失败，请重试"
    const val AT_LEAST_ONE = "至少保留一条归属"
    const val TAG_RULE = "标签需为 1–8 个字"
    const val EMPTY_STEM = "题干为空，请返回识别结果补字后再保存"
    const val NO_IMAGE = "原图缺失，请返回重拍"
    const val SAVE_FAILED = "保存失败，请重试"

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
     * 命中 → 去重 → 按 word 升序取前 50；bestId 在全部 word 命中词里 frequency 高优先、同频 word 升序取一条。
     * 最匹配落在前 50 之外时用其替换末位并重新按 word 排序，保证展示窗口一定能勾到它。
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
        val ordered = hits.values.sortedBy { it.first.word }
        val best =
            ordered
                .filter { it.second }
                .sortedWith(
                    compareByDescending<Pair<IdiomEntity, Boolean>> { it.first.frequency }
                        .thenBy { it.first.word },
                )
                .firstOrNull()
        val bestId = best?.first?.id
        val pool = ordered.take(MAX_CANDIDATES).toMutableList()
        if (best != null && pool.none { it.first.id == best.first.id }) {
            pool[pool.lastIndex] = best
            pool.sortBy { it.first.word }
        }
        return P20PoolResult(
            candidates =
                pool.map { (entity, wordHit) ->
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

    /** 「加一个」从全库未入池词中取一条（由调用方按 frequency DESC / word ASC 挑）；无档位标签。 */
    fun addedCandidate(entity: IdiomEntity): MatchCandidate =
        MatchCandidate(entity.id, entity.word, entity.frequency, MatchTier.Added)

    /** 空态手动回填：覆盖候选区、默认勾选、标「手动选择」。 */
    fun manualCandidate(entity: IdiomEntity): MatchCandidate =
        MatchCandidate(entity.id, entity.word, entity.frequency, MatchTier.Manual)
}
