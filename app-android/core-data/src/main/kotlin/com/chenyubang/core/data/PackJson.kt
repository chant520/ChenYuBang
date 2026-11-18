package com.chenyubang.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/** Parse before constructing JsonObject: kotlinx JSON alone would silently overwrite duplicate keys. */
internal object PackJson {
    fun parse(bytes: ByteArray): JsonElement {
        val decoder =
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        return PackJsonReader(decoder.decode(ByteBuffer.wrap(bytes)).toString()).parse()
    }
}

private const val MAX_JSON_DEPTH = 32
private val jsonNumber = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")

private class PackJsonReader(private val source: String) {
    private var position = 0

    fun parse(): JsonElement {
        val result = value(0)
        whitespace()
        require(position == source.length) { "Trailing JSON data" }
        return result
    }

    private fun value(depth: Int): JsonElement {
        require(depth <= MAX_JSON_DEPTH) { "JSON depth limit" }
        whitespace()
        require(position < source.length) { "Truncated JSON" }
        return when (source[position]) {
            '{' -> objectValue(depth + 1)
            '[' -> arrayValue(depth + 1)
            '"' -> JsonPrimitive(stringValue())
            't' -> literal("true", JsonPrimitive(true))
            'f' -> literal("false", JsonPrimitive(false))
            'n' -> literal("null", JsonNull)
            else -> numberValue()
        }
    }

    private fun objectValue(depth: Int): JsonObject {
        position++
        val fields = linkedMapOf<String, JsonElement>()
        if (consume('}')) return JsonObject(fields)
        do {
            whitespace()
            val key = stringValue()
            require(key !in fields) { "Duplicate JSON key (including NFC collision)" }
            require(consume(':')) { "Missing JSON colon" }
            fields[key] = value(depth)
        } while (consume(','))
        require(consume('}')) { "Missing JSON object end" }
        return JsonObject(fields)
    }

    private fun arrayValue(depth: Int): JsonArray {
        position++
        val elements = mutableListOf<JsonElement>()
        if (consume(']')) return JsonArray(elements)
        do {
            elements += value(depth)
        } while (consume(','))
        require(consume(']')) { "Missing JSON array end" }
        return JsonArray(elements)
    }

    private fun stringValue(): String {
        val end = stringEnd(source, position)
        val token = source.substring(position, end)
        position = end
        val decoded = (Json.parseToJsonElement(token) as JsonPrimitive).content
        requireValidSurrogates(decoded)
        return Normalizer.normalize(decoded, Normalizer.Form.NFC)
    }

    private fun numberValue(): JsonPrimitive {
        val token = jsonNumber.find(source, position)
        require(token != null && token.range.first == position) { "Invalid JSON number" }
        position = token.range.last + 1
        // Preserve a numeric primitive, not a string; reject nonintegral and excessive exponents now.
        return Json.parseToJsonElement(PackCanonical.integer(token.value)) as JsonPrimitive
    }

    private fun literal(
        token: String,
        result: JsonElement,
    ): JsonElement {
        require(source.startsWith(token, position)) { "Invalid JSON literal" }
        position += token.length
        return result
    }

    private fun consume(character: Char): Boolean {
        whitespace()
        if (position == source.length || source[position] != character) return false
        position++
        return true
    }

    private fun whitespace() {
        while (position < source.length && source[position] in " \t\r\n") position++
    }
}

private fun stringEnd(
    source: String,
    start: Int,
): Int {
    require(start < source.length && source[start] == '"') { "Expected JSON string" }
    var position = start + 1
    while (position < source.length) {
        val character = source[position++]
        when (character) {
            '"' -> return position
            '\\' -> position = escapeEnd(source, position)
            else -> require(character >= ' ') { "Unescaped JSON control" }
        }
    }
    error("Truncated JSON string")
}

private fun escapeEnd(
    source: String,
    start: Int,
): Int {
    require(start < source.length) { "Truncated JSON escape" }
    val escaped = source[start]
    if (escaped != 'u') {
        require(escaped in "\"\\/bfnrt") { "Invalid JSON escape" }
        return start + 1
    }
    val end = start + 1 + UNICODE_ESCAPE_DIGITS
    require(end <= source.length) { "Truncated Unicode escape" }
    require(source.substring(start + 1, end).all { it in "0123456789abcdefABCDEF" }) { "Invalid Unicode escape" }
    return end
}

private const val UNICODE_ESCAPE_DIGITS = 4

private fun requireValidSurrogates(value: String) {
    var index = 0
    while (index < value.length) {
        val character = value[index++]
        if (Character.isHighSurrogate(character)) {
            require(index < value.length && Character.isLowSurrogate(value[index])) { "Unpaired JSON surrogate" }
            index++
        } else {
            require(!Character.isLowSurrogate(character)) { "Unpaired JSON surrogate" }
        }
    }
}
