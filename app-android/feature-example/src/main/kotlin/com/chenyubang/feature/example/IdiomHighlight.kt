package com.chenyubang.feature.example

/**
 * 例题文本里的成语高亮匹配（工作包 1.5，验收 5/7）。
 *
 * 口径（任务书已定）：不引入分词库，用「在 idiom 表全量词表中扫描题干文本、命中即可点」的
 * 词匹配实现。题干/选项/解析里出现的成语按词匹配标记为 [IdiomHit]，每段文本可一次命中多个成语。
 *
 * 匹配规则：
 *  - 词表按「成语字长降序」排序后扫描，保证「墨守成规」这类更长词优先于其子串（如「成规」）命中；
 *  - 同一文本位置只归属一次命中（重叠部分不再被短词占用）；
 *  - 命中区间按出现顺序输出，互不重叠。
 *
 * [match] 是纯函数（只依赖入参），供 UI 层把 [IdiomHit.idiomId] 通过 onOpenIdiom 回调交给 app 路由跳 P05。
 */
data class IdiomHit(
    /** 命中成语的 id（route idiom/{id} 的参数）。 */
    val idiomId: String,
    /** 命中成语文本。 */
    val word: String,
    /** 在原文中的起始字符偏移。 */
    val start: Int,
    /** 在原文中的结束字符偏移（不含，即 substring(start, end) == word）。 */
    val end: Int,
)

/** 成语高亮匹配的纯函数实现（便于单测）。词表为空时命中数为零。 */
object IdiomHighlight {
    /**
     * 在 [text] 中扫描 [wordIndex]（成语 id → 成语词），返回按位置升序、互不重叠的命中列表。
     * [wordIndex] 的 key 即成语 id（route 参数），value 即成语词。
     */
    fun match(
        text: String,
        wordIndex: Map<String, String>,
    ): List<IdiomHit> {
        if (text.isEmpty() || wordIndex.isEmpty()) return emptyList()
        val sorted = wordIndex.entries.sortedByDescending { it.value.length }
        val hits = mutableListOf<IdiomHit>()
        var cursor = 0
        while (cursor < text.length) {
            var best: IdiomHit? = null
            for ((idiomId, word) in sorted) {
                val index = text.indexOf(word, cursor)
                if (index < 0) continue
                val candidate = IdiomHit(idiomId, word, index, index + word.length)
                // 在当前扫描位置上取最早出现的命中；同一起点取更长的词。
                if (best == null || isEarlier(candidate, best)) best = candidate
            }
            if (best == null) break
            hits += best
            cursor = best.end
        }
        return hits
    }

    /** [candidate] 是否比 [current] 更靠前命中；同起点时更长的词优先。 */
    private fun isEarlier(
        candidate: IdiomHit,
        current: IdiomHit,
    ): Boolean =
        candidate.start < current.start ||
            (candidate.start == current.start && candidate.word.length > current.word.length)
}
