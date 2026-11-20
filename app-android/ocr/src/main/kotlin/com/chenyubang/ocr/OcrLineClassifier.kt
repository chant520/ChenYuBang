package com.chenyubang.ocr

/**
 * 把识别行切成「题干 + 选项」：以 A–D 选项标记起头的行为分界，标记前的行归题干，
 * 标记后的无标记行并入上一个选项（长选项折行）。只认结构、不改字。
 */
internal object OcrLineClassifier {
    private val OPTION_MARKER = Regex("^\\s*([A-Da-dＡ-Ｄａ-ｄ])\\s*[．.、:：,，)）]\\s*")

    fun classify(lines: List<String>): Pair<String, Map<String, String>> {
        val stem = StringBuilder()
        val options = LinkedHashMap<String, String>()
        var current: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val marker = OPTION_MARKER.find(line)
            if (marker != null) {
                val key = normalizeMarker(marker.groupValues[1])
                val body = line.substring(marker.range.last + 1).trim()
                options[key] = body
                current = key
            } else if (current == null) {
                if (stem.isNotEmpty()) stem.append('\n')
                stem.append(line)
            } else {
                val previous = options.getValue(current)
                options[current] = if (previous.isEmpty()) line else previous + line
            }
        }
        return stem.toString().trim() to options.toSortedMap()
    }

    private fun normalizeMarker(value: String): String {
        val upper = value.uppercase()
        return when (upper) {
            "Ａ" -> "A"
            "Ｂ" -> "B"
            "Ｃ" -> "C"
            "Ｄ" -> "D"
            else -> upper
        }
    }
}
