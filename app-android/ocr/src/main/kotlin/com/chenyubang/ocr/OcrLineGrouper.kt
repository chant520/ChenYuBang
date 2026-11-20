package com.chenyubang.ocr

/** 同一行被检测框切成多段时按行合并：竖直重叠 ≥50% 视为同一行，行内从左到右拼接。 */
internal object OcrLineGrouper {
    data class Line(val box: OcrDetPostProcess.Box, val text: String)

    private const val ROW_OVERLAP_RATIO = 0.5f

    fun group(lines: List<Line>): List<String> {
        val rows = ArrayList<MutableList<Line>>()
        for (line in lines.sortedBy { it.box.top }) {
            val row = rows.firstOrNull { sameRow(it.last().box, line.box) }
            if (row != null) row.add(line) else rows.add(mutableListOf(line))
        }
        return rows
            .map { row -> row.sortedBy { it.box.left } }
            .sortedBy { row -> row.minOf { it.box.top } }
            .map { row -> row.joinToString(separator = "") { it.text } }
            .filter { it.isNotBlank() }
    }

    private fun sameRow(
        first: OcrDetPostProcess.Box,
        second: OcrDetPostProcess.Box,
    ): Boolean {
        val overlap = minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)
        val shorter = minOf(first.height, second.height)
        return shorter > 0f && overlap >= shorter * ROW_OVERLAP_RATIO
    }
}
