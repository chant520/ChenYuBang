package com.chenyubang.ocr

import kotlin.math.abs

/** 同一行被检测框切成多段时按行合并：竖直重叠 ≥50% 视为同一行；左右并排且中心接近的框也归同一行。 */
internal object OcrLineGrouper {
    data class Line(val box: OcrDetPostProcess.Box, val text: String)

    /** 行内按 x 间隙切出的列：column 相同的段属于同一列。 */
    data class ColumnSegment(val text: String, val column: Int)

    private const val ROW_OVERLAP_RATIO = 0.5f
    private const val SIDE_BY_SIDE_GAP_RATIO = 0.5f
    private const val ROW_CENTER_TOLERANCE = 0.7f
    private const val COLUMN_GAP_RATIO = 0.1f

    fun group(lines: List<Line>): List<String> =
        groupRows(lines)
            .map { row -> row.joinToString(separator = "") { it.text } }
            .filter { it.isNotBlank() }

    /** y 行 + x 列联合聚类：行内从左到右、行间从上到下。 */
    fun groupRows(lines: List<Line>): List<List<Line>> {
        val rows = ArrayList<MutableList<Line>>()
        for (line in lines.sortedBy { it.box.top }) {
            val row = rows.firstOrNull { row -> row.any { existing -> sameRow(existing.box, line.box) } }
            if (row != null) row.add(line) else rows.add(mutableListOf(line))
        }
        return rows
            .map { it.sortedBy { line -> line.box.left } }
            .sortedBy { row -> row.minOf { it.box.top } }
    }

    /** 把一行按 x 间隙切成列，供 classifier 区分「折行」与「另一列的新选项」。 */
    fun splitByColumn(row: List<Line>): List<ColumnSegment> {
        val sorted = row.sortedBy { it.box.left }
        if (sorted.isEmpty()) return emptyList()
        val columns = IntArray(sorted.size)
        var column = 0
        for (i in 1 until sorted.size) {
            val previous = sorted[i - 1].box
            val current = sorted[i].box
            val gap = current.left - previous.right
            if (gap > maxOf(previous.width, current.width) * COLUMN_GAP_RATIO) column++
            columns[i] = column
        }
        return sorted.mapIndexed { index, line -> ColumnSegment(line.text, columns[index]) }
    }

    private fun sameRow(
        first: OcrDetPostProcess.Box,
        second: OcrDetPostProcess.Box,
    ): Boolean {
        val overlapY = minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)
        val shorter = minOf(first.height, second.height)
        if (shorter > 0f && overlapY >= shorter * ROW_OVERLAP_RATIO) return true
        // 双列 A/B 垂直错位 <50% 时按 x 并排 + 中心接近补归同一行。
        val gapX = maxOf(first.left, second.left) - minOf(first.right, second.right)
        val centerGap = abs((first.top + first.bottom - second.top - second.bottom) / 2f)
        return gapX > 0f &&
            gapX <= maxOf(first.width, second.width) * SIDE_BY_SIDE_GAP_RATIO &&
            centerGap <= maxOf(first.height, second.height) * ROW_CENTER_TOLERANCE
    }
}
