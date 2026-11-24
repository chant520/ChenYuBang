package com.chenyubang.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Explicit mirror of shared/schema/{pack-manifest,idioms,examples}.schema.json, without permissive decoding. */
internal object PackSchema {
    private const val MIN_FREQUENCY = 1
    private const val MAX_FREQUENCY = 5
    private const val MIN_YEAR = 2000
    private const val MAX_YEAR = 2100
    private val uuid = string("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    private val word = string("^[\\u4e00-\\u9fa5]{2,8}$")
    private val note = string(".{4,}")
    private val examType = choice("国考", "省考", "事业编", "教师编")
    private val pitfalls =
        array(
            shape(mapOf("kind" to choice("望文生义", "对象误用", "褒贬误用", "语义重复", "搭配不当"), "note" to note)),
            minimum = 0,
        )
    private val idiom =
        shape(
            required =
                mapOf(
                    "id" to uuid,
                    "word" to word,
                    "pinyin" to string("^[a-z]+( [a-z]+){1,7}$"),
                    "initials" to string("^[a-z]{2,8}$"),
                    "sense" to note,
                    "color" to choice("褒义", "贬义", "中性"),
                    "frequency" to integer(MIN_FREQUENCY, MAX_FREQUENCY),
                    "scopes" to array(examType),
                ),
            optional =
                mapOf(
                    "origin" to note,
                    "pitfalls" to pitfalls,
                    "synonyms" to array(word, minimum = 0),
                    "antonyms" to array(word, minimum = 0),
                ),
        )
    private val example =
        shape(
            required =
                mapOf(
                    "id" to uuid,
                    "stem" to string(".{6,}"),
                    "options" to
                        shape(
                            mapOf(
                                "A" to string(".{2,}"), "B" to string(".{2,}"), "C" to string(".{2,}"),
                                "D" to string(".{2,}"),
                            ),
                        ),
                    "answer" to choice("A", "B", "C", "D"),
                    "explain" to note,
                    "examType" to examType,
                    "year" to integer(MIN_YEAR, MAX_YEAR),
                    "questionType" to choice("言语理解", "逻辑填空", "语句表达", "常识判断"),
                    "idiomIds" to array(uuid),
                ),
            optional = mapOf("region" to string(".{2,}")),
        )
    private val manifest =
        shape(
            mapOf(
                "schemaVersion" to integer(1),
                "packVersion" to string("^v\\d+\\.\\d+\\.\\d+$"),
                "builtAt" to string("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$"),
                "counts" to shape(mapOf("idioms" to integer(1), "examples" to integer(1))),
                "sha256" to string("^[0-9a-f]{64}$"),
            ),
        )

    fun validate(
        metadata: JsonElement,
        idioms: JsonElement,
        examples: JsonElement,
    ) {
        manifest.check(metadata)
        array(idiom).check(idioms)
        array(example).check(examples)
        require((metadata as JsonObject).integer("schemaVersion") == 1) { "Unsupported pack schemaVersion" }
        val words = (idioms as JsonArray).map { (it as JsonObject).string("word") }
        require(words.distinct().size == words.size) { "Duplicate idiom word" }
        validateReferences(idioms, examples as JsonArray)
    }

    private fun validateReferences(
        idioms: JsonArray,
        examples: JsonArray,
    ) {
        val ids = idioms.map { (it as JsonObject).string("id") }
        val allIds = ids + examples.map { (it as JsonObject).string("id") }
        require(allIds.toSet().size == allIds.size) { "Duplicate UUID across idioms/examples" }
        val known = ids.toSet()
        examples.forEach { element ->
            val item = element as JsonObject
            val links = (item.getValue("idiomIds") as JsonArray).map { (it as JsonPrimitive).content }
            require(links.all { it in known }) { "Unclosed example idiom reference" }
            if (item.string("examType") == "省考") {
                require(item.optionalString("region")?.isNotBlank() == true) { "Provincial example needs region" }
            }
        }
    }
}

private fun interface PackRule {
    fun check(value: JsonElement)
}

private fun shape(
    required: Map<String, PackRule>,
    optional: Map<String, PackRule> = emptyMap(),
): PackRule =
    PackRule { value ->
        require(value is JsonObject) { "Expected JSON object" }
        require(value.keys.containsAll(required.keys)) { "Missing required pack field" }
        val properties = required + optional
        require(properties.keys.containsAll(value.keys)) { "Unknown pack field" }
        value.forEach { (key, field) -> properties.getValue(key).check(field) }
    }

private fun string(pattern: String): PackRule {
    val regex = Regex(pattern, RegexOption.DOT_MATCHES_ALL)
    return PackRule { value ->
        require(value is JsonPrimitive && value.isString) { "Expected JSON string" }
        require(regex.matches(value.content)) { "Pack string pattern mismatch" }
    }
}

private fun choice(vararg choices: String): PackRule =
    PackRule { value ->
        require(value is JsonPrimitive && value.isString && value.content in choices) { "Unknown pack enum" }
    }

private fun integer(
    minimum: Int,
    maximum: Int = Int.MAX_VALUE,
): PackRule =
    PackRule { value ->
        require(value is JsonPrimitive && !value.isString) { "Expected JSON integer" }
        val number = value.content.toIntOrNull()
        require(number != null && number in minimum..maximum) { "Pack integer out of range" }
    }

private fun array(
    item: PackRule,
    minimum: Int = 1,
): PackRule =
    PackRule { value ->
        require(value is JsonArray && value.size >= minimum) { "Expected pack array/minItems" }
        value.forEach { item.check(it) }
    }

internal fun JsonObject.string(key: String): String = (getValue(key) as JsonPrimitive).content

internal fun JsonObject.optionalString(key: String): String? =
    (get(key) as? JsonPrimitive)
        ?.takeIf { it !is JsonNull }
        ?.content

internal fun JsonObject.integer(key: String): Int = string(key).toInt()
