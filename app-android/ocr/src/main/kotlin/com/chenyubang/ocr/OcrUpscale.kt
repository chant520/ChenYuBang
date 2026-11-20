package com.chenyubang.ocr

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 识别前统一上采样：短边至少 [MIN_SHORT_SIDE]px，长边超过 [MAX_LONG_SIDE]px 再等比压回，防止大图 OOM。 */
internal object OcrUpscale {
    const val MIN_SHORT_SIDE = 1400
    const val MAX_LONG_SIDE = 4000

    fun targetSize(
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val shortSide = min(width, height)
        var scale = if (shortSide < MIN_SHORT_SIDE) MIN_SHORT_SIDE.toFloat() / shortSide else 1f
        val longSide = max(width, height) * scale
        if (longSide > MAX_LONG_SIDE) scale *= MAX_LONG_SIDE / longSide
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}
