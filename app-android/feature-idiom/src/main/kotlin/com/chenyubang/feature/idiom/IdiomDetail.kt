package com.chenyubang.feature.idiom

import com.chenyubang.core.data.IdiomEntity

/**
 * P05 成语详情展示模型（信息架构 v1.3 P05 ①–⑦）。
 *
 * 字段均为「可直接呈现的文本」：缺字段已在 [IdiomDetail.viewOf] 里填 [IdiomDetail.PLACEHOLDER]，
 * 页面层不再做空值判断，也不出现空白区块。读音=pinyin（朗读属未来项，本期只展示拼音）；
 * 释义=sense；感情色彩=color；易错点=pitfalls；近反义辨析=synonyms/antonyms；出处=origin。
 */
data class IdiomDetailView(
    val id: String,
    val word: String,
    val pinyin: String,
    val color: String,
    val sense: String,
    val origin: String,
    val pitfalls: List<String>,
    val synonyms: List<String>,
    val antonyms: List<String>,
)

/**
 * P05 的取值 / 缺字段判定与 JSON 数组解析（纯函数，便于单测）。
 * 不依赖 Activity / Room，测试直接构造字段即可覆盖「五项齐全」与「缺字段暂无」。
 */
object IdiomDetail {
    /** 缺字段统一占位，不出现「暂无内容」这类故障式文案，也不留空白。 */
    const val PLACEHOLDER = "暂无"

    private val SENTENCE_END = charArrayOf('。', '！', '？', '；')

    fun viewOf(entity: IdiomEntity): IdiomDetailView =
        IdiomDetailView(
            id = entity.id,
            word = present(entity.word),
            pinyin = present(entity.pinyin),
            color = colorText(entity.color),
            sense = present(entity.sense),
            origin = present(entity.origin),
            pitfalls = parseJsonStringArray(entity.pitfalls),
            synonyms = parseJsonStringArray(entity.synonyms),
            antonyms = parseJsonStringArray(entity.antonyms),
        )

    /** 空串 / 纯空白 / null 一律归 [PLACEHOLDER]。 */
    fun present(value: String?): String = value?.trim()?.takeIf { it.isNotEmpty() } ?: PLACEHOLDER

    /** 感情色彩归一到「褒义 / 贬义 / 中性」；无法识别时仍按缺字段处理。 */
    fun colorText(raw: String?): String =
        when (raw?.trim()) {
            "褒", "褒义" -> "褒义"
            "贬", "贬义" -> "贬义"
            "中", "中性" -> "中性"
            else -> present(raw)
        }

    /** 易错点 / 近义 / 反义的空列表呈现：整项填 [PLACEHOLDER]，不空白。 */
    fun presentList(items: List<String>): String =
        items.filter { it.isNotBlank() }.joinToString("、").ifBlank { PLACEHOLDER }

    /**
     * 解析库里的 JSON 字符串数组（如 `["望文生义：…","对象误用：…"]`）。
     * 自实现小解析器而非切分：释义里可能含英文逗号，按引号状态逐字扫描才不会拆错。
     * 非数组文本按单项处理；null / 空串 / `[]` 返回空列表。
     */
    fun parseJsonStringArray(raw: String?): List<String> {
        val text = raw?.trim().takeUnless { it.isNullOrEmpty() || it == "null" }
        val parsed =
            when {
                text == null -> emptyList()
                !text.startsWith("[") -> listOf(text.trim('"'))
                else -> scanJsonArray(text)
            }
        return parsed.map { it.trim() }.filter { it.isNotEmpty() && it != "null" }
    }

    private fun scanJsonArray(text: String): List<String> {
        val result = mutableListOf<String>()
        val item = StringBuilder()
        var inQuote = false
        var escaped = false
        var index = 1
        while (index < text.length) {
            val ch = text[index]
            when {
                escaped -> {
                    item.append(ch)
                    escaped = false
                }
                ch == '\\' && inQuote -> escaped = true
                ch == '"' -> inQuote = !inQuote
                ch == ',' && !inQuote -> {
                    result += item.toString().trim()
                    item.setLength(0)
                }
                ch == ']' && !inQuote -> break
                else -> item.append(ch)
            }
            index++
        }
        if (item.isNotBlank()) result += item.toString().trim()
        return result
    }

    /** 释义首句：按第一个句读符截断并保留该符号，供 P04 结果行使用。 */
    fun firstSentence(sense: String?): String {
        val text = present(sense)
        if (text == PLACEHOLDER) return text
        val end = text.indexOfAny(SENTENCE_END)
        return if (end < 0) text else text.substring(0, end + 1)
    }
}
