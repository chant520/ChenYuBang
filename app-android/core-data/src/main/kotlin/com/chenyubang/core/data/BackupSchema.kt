package com.chenyubang.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * backup-data / backup-manifest schema 的显式镜像（additionalProperties:false，不用宽松解码）。
 * 只做结构与字段级硬校验；schemaVersion 上限、sha256 与 counts 一致性由 BackupImporter 判定。
 */
internal object BackupSchema {
    private const val MIN_YEAR = 2000
    private const val MAX_YEAR = 2100
    private val uuid = string("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    private val utc = string("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")
    private val text = string(".*")
    private val note = string(".{2,}")
    private val tagName = string(".{1,8}")
    private val optText = nullable(text)
    private val optAnswer = nullable(choice("A", "B", "C", "D"))
    private val optYear = nullable(integer(MIN_YEAR, MAX_YEAR))
    private val optUtc = nullable(utc)
    private val examType = choice("国考", "省考", "事业编", "教师编")
    private val optExamType = nullable(examType)
    private val mistake =
        shape(
            required =
                mapOf(
                    "id" to uuid,
                    "stem" to note,
                    "options" to
                        shape(
                            mapOf("A" to text, "B" to text, "C" to text, "D" to text),
                        ),
                    "state" to choice("待复习", "已掌握"),
                    "createdAt" to utc,
                    "reviewCount" to integer(0),
                    "idiomIds" to array(uuid, 1),
                ),
            optional =
                mapOf(
                    "userExampleId" to nullable(uuid),
                    "imagePath" to optText,
                    "ocrText" to optText,
                    "myAnswer" to optAnswer,
                    "reason" to optText,
                    "tagNames" to array(tagName, 0),
                    "examType" to optExamType,
                    "year" to optYear,
                    "region" to optText,
                    "lastReviewedAt" to optUtc,
                ),
        )
    private val userExample =
        shape(
            required =
                mapOf(
                    "id" to uuid,
                    "stem" to note,
                    "isMistake" to bool,
                    "createdAt" to utc,
                    "idiomIds" to array(uuid, 1),
                ),
            optional =
                mapOf(
                    "imagePath" to optText,
                    "ocrText" to optText,
                    "optionA" to optText,
                    "optionB" to optText,
                    "optionC" to optText,
                    "optionD" to optText,
                    "myAnswer" to optAnswer,
                    "examType" to optExamType,
                    "year" to optYear,
                    "region" to optText,
                ),
        )
    private val review =
        shape(
            mapOf(
                "id" to uuid,
                "mistakeId" to uuid,
                "result" to choice("对", "错"),
                "at" to utc,
            ),
        )
    private val idiomState =
        shape(
            mapOf(
                "idiomId" to uuid,
                "status" to choice("未学", "学过", "已掌握"),
                "favorite" to bool,
            ),
        )
    private val scope =
        shape(
            required = mapOf("type" to examType, "enabled" to bool),
            optional = mapOf("province" to optText),
        )
    private val tag = shape(mapOf("name" to tagName))
    private val counts =
        shape(
            mapOf(
                "mistakes" to integer(0),
                "userExamples" to integer(0),
                "reviews" to integer(0),
                "idiomStates" to integer(0),
                "scopes" to integer(0),
            ),
        )
    private val manifest =
        shape(
            mapOf(
                "schemaVersion" to integer(1),
                "appVersion" to string("^\\d+\\.\\d+\\.\\d+"),
                "build" to integer(1),
                "exportedAt" to utc,
                "counts" to counts,
                "sha256" to string("^[0-9a-f]{64}$"),
            ),
        )
    private val data =
        shape(
            required =
                mapOf(
                    "mistakes" to array(mistake, 0),
                    "reviews" to array(review, 0),
                    "idiomStates" to array(idiomState, 0),
                    "scopes" to array(scope, 0),
                    "tags" to array(tag, 0),
                ),
            optional = mapOf("userExamples" to array(userExample, 0)),
        )

    fun validateManifest(metadata: JsonElement): JsonObject {
        manifest.check(metadata)
        return metadata as JsonObject
    }

    fun validateData(document: JsonElement): JsonObject {
        data.check(document)
        return document as JsonObject
    }
}

private fun interface BackupRule {
    fun check(value: JsonElement)
}

private fun shape(
    required: Map<String, BackupRule>,
    optional: Map<String, BackupRule> = emptyMap(),
): BackupRule =
    BackupRule { value ->
        require(value is JsonObject) { "Expected JSON object" }
        require(value.keys.containsAll(required.keys)) { "Missing backup field" }
        val properties = required + optional
        require(properties.keys.containsAll(value.keys)) { "Unknown backup field" }
        value.forEach { (key, field) -> properties.getValue(key).check(field) }
    }

private fun string(pattern: String): BackupRule {
    val regex = Regex(pattern, RegexOption.DOT_MATCHES_ALL)
    return BackupRule { value ->
        require(value is JsonPrimitive && value.isString && regex.matches(value.content)) {
            "Backup string pattern mismatch"
        }
    }
}

private fun choice(vararg choices: String): BackupRule =
    BackupRule { value ->
        require(value is JsonPrimitive && value.isString && value.content in choices) { "Unknown backup enum" }
    }

private val bool =
    BackupRule { value ->
        require(
            value is JsonPrimitive && !value.isString && (value.content == "true" || value.content == "false"),
        ) { "Expected JSON boolean" }
    }

private fun nullable(rule: BackupRule): BackupRule =
    BackupRule { value ->
        if (value != JsonNull) rule.check(value)
    }

private fun integer(
    minimum: Int,
    maximum: Int = Int.MAX_VALUE,
): BackupRule =
    BackupRule { value ->
        require(value is JsonPrimitive && !value.isString) { "Expected JSON integer" }
        val number = value.content.toIntOrNull()
        require(number != null && number in minimum..maximum) { "Backup integer out of range" }
    }

private fun array(
    item: BackupRule,
    minimum: Int = 0,
): BackupRule =
    BackupRule { value ->
        require(value is JsonArray && value.size >= minimum) { "Expected backup array/minItems" }
        value.forEach { item.check(it) }
    }
