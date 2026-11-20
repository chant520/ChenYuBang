package com.chenyubang.ocr

/**
 * CTC 贪心解码：空白类为 0，字符类 i 对应字典第 i-1 项；跳过连续重复与空白。
 * 置信度取被保留字符的平均最大概率。
 */
internal object CtcDecoder {
    data class Decoded(val text: String, val score: Float)

    fun decode(
        probabilities: FloatArray,
        timeSteps: Int,
        classes: Int,
        dictionary: List<String>,
    ): Decoded? {
        require(probabilities.size == timeSteps * classes)
        val text = StringBuilder()
        var total = 0f
        var kept = 0
        var previous = -1
        for (step in 0 until timeSteps) {
            val offset = step * classes
            var best = 0
            var bestValue = probabilities[offset]
            for (index in 1 until classes) {
                val value = probabilities[offset + index]
                if (value > bestValue) {
                    bestValue = value
                    best = index
                }
            }
            if (best != 0 && best != previous) {
                val character = dictionary.getOrNull(best - 1)
                if (!character.isNullOrEmpty()) {
                    text.append(character)
                    total += bestValue
                    kept++
                }
            }
            previous = best
        }
        if (text.isEmpty() || kept == 0) return null
        return Decoded(text = text.toString(), score = total / kept)
    }
}
