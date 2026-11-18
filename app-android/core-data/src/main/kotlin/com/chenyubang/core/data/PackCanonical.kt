package com.chenyubang.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.security.MessageDigest
import java.text.Normalizer

/** Python tools/canonical.py parity: NFC, code-point key order, whole numbers, no whitespace. */
internal object PackCanonical {
    private const val DIGEST_BYTE_MASK = 0xff
    private const val MAX_NUMBER_LENGTH = 128
    private const val MAX_NUMBER_SCALE = 64
    private val codePointOrder =
        Comparator<String> { left, right ->
            val a = left.codePoints().toArray()
            val b = right.codePoints().toArray()
            var comparison = 0
            var index = 0
            while (index < minOf(a.size, b.size) && comparison == 0) {
                comparison = a[index].compareTo(b[index])
                index++
            }
            if (comparison == 0) a.size.compareTo(b.size) else comparison
        }

    fun text(value: JsonElement): String =
        when (value) {
            is JsonObject -> objectText(value)
            is JsonArray -> value.joinToString(",", "[", "]") { text(it) }
            JsonNull -> "null"
            is JsonPrimitive -> primitiveText(value)
        }

    fun digest(
        idioms: JsonElement,
        examples: JsonElement,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(text(examples).toByteArray(Charsets.UTF_8))
        digest.update(text(idioms).toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and DIGEST_BYTE_MASK) }
    }

    fun integer(number: String): String {
        require(number.length <= MAX_NUMBER_LENGTH) { "JSON number too long" }
        val decimal = BigDecimal(number)
        require(decimal.scale() in -MAX_NUMBER_SCALE..MAX_NUMBER_SCALE) { "JSON number too large" }
        if (number.none { it == '.' || it == 'e' || it == 'E' }) return decimal.toBigIntegerExact().toString()
        val value = number.toDouble()
        require(value.isFinite() && value % 1.0 == 0.0) { "Nonintegral or nonfinite JSON number" }
        return BigDecimal(value).toBigIntegerExact().toString()
    }

    private fun objectText(value: JsonObject): String {
        val normalized = linkedMapOf<String, JsonElement>()
        value.forEach { (key, element) ->
            val name = Normalizer.normalize(key, Normalizer.Form.NFC)
            require(normalized.put(name, element) == null) { "NFC key collision" }
        }
        return normalized.keys.sortedWith(codePointOrder).joinToString(",", "{", "}") {
            quote(it) + ":" + text(normalized.getValue(it))
        }
    }

    private fun primitiveText(value: JsonPrimitive): String =
        when {
            value.isString -> quote(value.content)
            value.content == "true" || value.content == "false" -> value.content
            else -> integer(value.content)
        }

    private fun quote(value: String): String =
        buildString {
            append('"')
            Normalizer.normalize(value, Normalizer.Form.NFC).forEach { character ->
                append(escape(character))
            }
            append('"')
        }

    private fun escape(character: Char): String =
        when (character) {
            '"' -> "\\\""
            '\\' -> "\\\\"
            '\b' -> "\\b"
            '\u000c' -> "\\f"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> if (character < ' ') "\\u%04x".format(character.code) else character.toString()
        }
}
