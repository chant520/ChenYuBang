package com.chenyubang.ocr

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 张量预处理：检测按 PP-OCR 的 BGR 均值方差归一；识别压到高 48。纯函数，便于单测。 */
internal object OcrTensor {
    const val DET_MAX_SIDE = 960
    const val DET_ALIGN = 32
    const val REC_HEIGHT = 48
    const val MIN_REC_WIDTH = 32
    const val REC_MAX_WIDTH = 1600
    private const val CHANNEL_COUNT = 3
    private const val BYTE_SCALE = 255f
    private const val BYTE_MASK = 0xFF
    private const val GREEN_SHIFT = 8
    private const val RED_SHIFT = 16
    private const val DET_MEAN_B = 0.485f
    private const val DET_MEAN_G = 0.456f
    private const val DET_MEAN_R = 0.406f
    private const val DET_STD_B = 0.229f
    private const val DET_STD_G = 0.224f
    private const val DET_STD_R = 0.225f
    private const val REC_MEAN = 0.5f
    private const val REC_STD = 0.5f

    fun detSize(
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return DET_ALIGN to DET_ALIGN
        val scale = min(1f, DET_MAX_SIDE.toFloat() / max(width, height))
        val scaledWidth = alignDown((width * scale).roundToInt())
        val scaledHeight = alignDown((height * scale).roundToInt())
        return scaledWidth to scaledHeight
    }

    fun recSize(
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return MIN_REC_WIDTH to REC_HEIGHT
        val scaledWidth = (REC_HEIGHT.toFloat() * width / height).roundToInt()
        return scaledWidth.coerceIn(MIN_REC_WIDTH, REC_MAX_WIDTH) to REC_HEIGHT
    }

    fun detTensor(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): FloatArray = tensor(pixels, width, height, detMean(), detStd())

    fun recTensor(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): FloatArray = tensor(pixels, width, height, recMean(), recStd())

    private fun tensor(
        pixels: IntArray,
        width: Int,
        height: Int,
        mean: FloatArray,
        std: FloatArray,
    ): FloatArray {
        require(pixels.size == width * height)
        val area = width * height
        val data = FloatArray(CHANNEL_COUNT * area)
        for (index in pixels.indices) {
            val color = pixels[index]
            // PaddleOCR 的输入是 BGR 顺序：0=B、1=G、2=R，分别减各自均值、除各自方差。
            data[index] = ((color and BYTE_MASK) / BYTE_SCALE - mean[0]) / std[0]
            data[area + index] = ((color shr GREEN_SHIFT and BYTE_MASK) / BYTE_SCALE - mean[1]) / std[1]
            data[2 * area + index] = ((color shr RED_SHIFT and BYTE_MASK) / BYTE_SCALE - mean[2]) / std[2]
        }
        return data
    }

    private fun detMean(): FloatArray = floatArrayOf(DET_MEAN_B, DET_MEAN_G, DET_MEAN_R)

    private fun detStd(): FloatArray = floatArrayOf(DET_STD_B, DET_STD_G, DET_STD_R)

    private fun recMean(): FloatArray = floatArrayOf(REC_MEAN, REC_MEAN, REC_MEAN)

    private fun recStd(): FloatArray = floatArrayOf(REC_STD, REC_STD, REC_STD)

    private fun alignDown(value: Int): Int = max(DET_ALIGN, value / DET_ALIGN * DET_ALIGN)
}
