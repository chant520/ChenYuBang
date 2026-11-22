package com.chenyubang.feature.mistake

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** 库里错题状态的取值（开发规范 §4.1）；P08/P09 只做展示，不改状态。 */
internal const val MISTAKE_STATE_PENDING = "待复习"

private const val EXAM_TYPE_UNKNOWN = "未标注考试类型"

/** 错题/录入题时间：ISO 字符串 →「2026-11-14 录入」；空、null、非法一律回退原串（空串保持空串）。 */
internal fun formatRecordedAt(
    iso: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val date = Instant.parse(iso).atZone(zone).toLocalDate()
        "%04d-%02d-%02d 录入".format(date.year, date.monthValue, date.dayOfMonth)
    } catch (_: DateTimeParseException) {
        iso
    }
}

/**
 * 分组行错因分布小字（设计稿 P08「望文生义 2 · 对象误用 1」）。
 * 计数降序、同数按标签名；没有任何标签时返回 null，行内不画这一行。
 */
internal fun tagSummaryLine(tagNames: List<String>): String? {
    if (tagNames.isEmpty()) return null
    val counts = LinkedHashMap<String, Int>()
    tagNames.forEach { tag -> counts[tag] = (counts[tag] ?: 0) + 1 }
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .joinToString(" · ") { (tag, count) -> "$tag $count" }
}

/** 按考试类型分组时的组名：null 归「未标注考试类型」（IA v1.4 P08 ⑤）。 */
internal fun examTypeLabel(examType: String?): String = examType?.takeIf { it.isNotBlank() } ?: EXAM_TYPE_UNKNOWN

/** P09 释义一行：「贬义。原指……」；色彩与释义缺一都不留多余标点。 */
internal fun idiomSenseLine(
    color: String?,
    sense: String?,
): String {
    val tone = color?.trim().orEmpty()
    val meaning = sense?.trim().orEmpty()
    return when {
        tone.isEmpty() -> meaning
        meaning.isEmpty() -> tone
        meaning.startsWith("。") -> tone + meaning
        else -> "$tone。$meaning"
    }
}
