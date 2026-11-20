package com.chenyubang.ocr

import kotlin.math.max
import kotlin.math.min

/**
 * DB 检测后处理（PP-OCR 默认参数）：概率图阈值 0.3 → 连通域 → 平均分 ≥0.5 → 按
 * area × unclipRatio / perimeter 外扩（unclip 1.6）。坐标落在概率图自身尺度。
 */
internal object OcrDetPostProcess {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float, val score: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    const val BOX_THRESHOLD = 0.3f
    const val SCORE_THRESHOLD = 0.5f
    const val UNCLIP_RATIO = 1.6f
    private const val MIN_BOX_SIDE = 2f

    /** 一个连通域的包围盒（右/下边界为开区间），附该域平均概率。 */
    private class Region(val left: Int, val top: Int, val right: Int, val bottom: Int, val score: Float)

    fun boxes(
        probability: FloatArray,
        width: Int,
        height: Int,
        boxThreshold: Float = BOX_THRESHOLD,
        scoreThreshold: Float = SCORE_THRESHOLD,
        unclipRatio: Float = UNCLIP_RATIO,
    ): List<Box> {
        require(probability.size == width * height)
        return FloodFill(probability, width, height, boxThreshold).scan()
            .mapNotNull { region -> toBox(region, width, height, scoreThreshold, unclipRatio) }
    }

    /** 四邻域洪泛：概率图逐像素扫描，未访问且高于阈值的像素作为种子。 */
    private class FloodFill(
        private val probability: FloatArray,
        private val width: Int,
        private val height: Int,
        private val threshold: Float,
    ) {
        private val visited = BooleanArray(probability.size)
        private val stack = IntArray(probability.size)

        fun scan(): List<Region> {
            val result = ArrayList<Region>()
            for (start in probability.indices) {
                if (visited[start] || probability[start] <= threshold) continue
                result.add(fill(start))
            }
            return result
        }

        private fun fill(start: Int): Region {
            stack[0] = start
            visited[start] = true
            var top = 1
            var left = width
            var right = -1
            var boxTop = height
            var boxBottom = -1
            var sum = 0f
            var count = 0
            while (top > 0) {
                val index = stack[--top]
                val x = index % width
                val y = index / width
                left = min(left, x)
                right = max(right, x)
                boxTop = min(boxTop, y)
                boxBottom = max(boxBottom, y)
                sum += probability[index]
                count++
                top = push(index - 1, x > 0, top)
                top = push(index + 1, x < width - 1, top)
                top = push(index - width, y > 0, top)
                top = push(index + width, y < height - 1, top)
            }
            return Region(left, boxTop, right + 1, boxBottom + 1, sum / count)
        }

        private fun push(
            index: Int,
            inside: Boolean,
            top: Int,
        ): Int {
            if (!inside || visited[index] || probability[index] <= threshold) return top
            visited[index] = true
            stack[top] = index
            return top + 1
        }
    }

    private fun toBox(
        region: Region,
        width: Int,
        height: Int,
        scoreThreshold: Float,
        unclipRatio: Float,
    ): Box? {
        if (region.score < scoreThreshold) return null
        val left = region.left.toFloat()
        val top = region.top.toFloat()
        val right = region.right.toFloat()
        val bottom = region.bottom.toFloat()
        val expanded = expand(left, top, right, bottom, unclipRatio)
        val box =
            Box(
                left = expanded[0].coerceIn(0f, width.toFloat()),
                top = expanded[1].coerceIn(0f, height.toFloat()),
                right = expanded[2].coerceIn(0f, width.toFloat()),
                bottom = expanded[3].coerceIn(0f, height.toFloat()),
                score = region.score,
            )
        return box.takeIf { it.width >= MIN_BOX_SIDE && it.height >= MIN_BOX_SIDE }
    }

    private fun expand(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        ratio: Float,
    ): FloatArray {
        val width = right - left
        val height = bottom - top
        val perimeter = 2f * (width + height)
        if (perimeter <= 0f) return floatArrayOf(left, top, right, bottom)
        val offset = width * height * ratio / perimeter
        return floatArrayOf(left - offset, top - offset, right + offset, bottom + offset)
    }
}

/** det 概率图尺度 → 上采样图尺度。 */
internal fun OcrDetPostProcess.Box.scaled(
    scaleX: Float,
    scaleY: Float,
): OcrDetPostProcess.Box =
    copy(
        left = left * scaleX,
        top = top * scaleY,
        right = right * scaleX,
        bottom = bottom * scaleY,
    )
