package com.chenyubang.ocr

/**
 * 把识别行切成「题干 + 选项」：以 A–D 选项标记起头的行为分界，标记前的行归题干，
 * 标记后的无标记行并入上一个选项（长选项折行）。带坐标时按 x 列区分折行与另一列的新选项。
 * 标记全部漏识时，仅当末四行短且无句末标点才回填 A–D，并置 [Classification.guessed] 供 P19 提示复核。
 * 只认结构、不改字。
 */
internal object OcrLineClassifier {
    private val OPTION_MARKER = Regex("^\\s*([A-Da-dＡ-Ｄａ-ｄ])\\s*[．.、:：,，)）]\\s*")

    // 双列版式会被 OCR 并成一行（如「A．异曲同工B、同气连枝」），需按行内标记切开。
    // 仅认「字母 + 分隔符」的严格标记，避免把选项正文里的英文字母误当选项。
    private val INLINE_MARKER = Regex("([A-Da-dＡ-Ｄａ-ｄ])\\s*[．.、:：,，)）]")
    private val SENTENCE_END = Regex("[。！？；;!?…]\\s*$")
    private const val MAX_GUESSED_OPTION_LENGTH = 12
    private val OPTION_KEYS = listOf("A", "B", "C", "D")

    data class Classification(val stem: String, val options: Map<String, String>, val guessed: Boolean = false)

    private data class Segment(val text: String, val column: Int)

    /** 兼容纯文本调用（无坐标）：不做跨列纠正。 */
    fun classify(lines: List<String>): Pair<String, Map<String, String>> =
        classifyDetailed(lines).let { it.stem to it.options }

    fun classifyDetailed(lines: List<String>): Classification =
        classifySegments(lines.map { Segment(it.trim(), 0) }.filter { it.text.isNotEmpty() })

    /** 带检测框的分类：同一行按 x 列切段，无标记段与上一有标记段不同列时视为新选项。 */
    fun classifyWithBoxes(lines: List<OcrLineGrouper.Line>): Classification {
        val segments =
            OcrLineGrouper.groupRows(lines).flatMap { row ->
                OcrLineGrouper.splitByColumn(row).mapNotNull { segment ->
                    segment.text.trim().takeIf { it.isNotEmpty() }?.let { Segment(it, segment.column) }
                }
            }
        return classifySegments(segments)
    }

    private fun classifySegments(segments: List<Segment>): Classification {
        val stem = StringBuilder()
        val options = LinkedHashMap<String, String>()
        var current: String? = null
        var currentColumn = 0
        for (segment in segments) {
            val line = segment.text
            val marker = OPTION_MARKER.find(line)
            if (marker != null) {
                val key = normalizeMarker(marker.groupValues[1])
                val body = line.substring(marker.range.last + 1).trim()
                val (split, lastKey) = splitInline(body, key)
                options.putAll(split)
                current = lastKey
                currentColumn = segment.column
            } else if (current == null) {
                if (stem.isNotEmpty()) stem.append('\n')
                stem.append(line)
            } else if (segment.column != currentColumn) {
                val nextKey = OPTION_KEYS.firstOrNull { it !in options }
                if (nextKey != null) {
                    options[nextKey] = line
                    current = nextKey
                    currentColumn = segment.column
                } else {
                    appendToCurrent(options, current, line)
                }
            } else {
                appendToCurrent(options, current, line)
            }
        }
        if (options.isEmpty() && segments.size > OPTION_KEYS.size) {
            val tail = segments.drop(segments.size - OPTION_KEYS.size).map { it.text }
            if (tail.all { it.length <= MAX_GUESSED_OPTION_LENGTH && !SENTENCE_END.containsMatchIn(it) }) {
                val fallbackStem = segments.take(segments.size - OPTION_KEYS.size).joinToString("\n") { it.text }
                return Classification(fallbackStem, OPTION_KEYS.zip(tail).toMap(), guessed = true)
            }
        }
        return Classification(stem.toString().trim(), options.toSortedMap())
    }

    private fun appendToCurrent(
        options: MutableMap<String, String>,
        key: String,
        line: String,
    ) {
        val previous = options.getValue(key)
        options[key] = if (previous.isEmpty()) line else previous + line
    }

    /** 把双列并成一行的选项正文按行内标记切开；只切结构不改字。返回切出的分段与最后一个选项 key。 */
    private fun splitInline(
        body: String,
        key: String,
    ): Pair<Map<String, String>, String> {
        val inline = INLINE_MARKER.findAll(body).toList()
        if (inline.size <= 1 && (inline.isEmpty() || inline.first().range.first == 0)) {
            return mapOf(key to body) to key
        }
        val parts = LinkedHashMap<String, String>()
        var cursor = 0
        var activeKey = key
        for (hit in inline) {
            val segment = body.substring(cursor, hit.range.first).trim()
            if (segment.isNotEmpty() || cursor == 0) parts[activeKey] = segment
            activeKey = normalizeMarker(hit.groupValues[1])
            cursor = hit.range.last + 1
        }
        val tail = body.substring(cursor).trim()
        if (tail.isNotEmpty()) parts[activeKey] = tail
        return parts to activeKey
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
