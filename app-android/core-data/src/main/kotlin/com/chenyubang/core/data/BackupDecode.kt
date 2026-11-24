package com.chenyubang.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 已通过 BackupSchema 校验的 JsonObject → 内存模型；可选字段缺失按 schema 默认值处理。 */
internal object BackupDecode {
    private val optInt: (JsonObject, String) -> Int? =
        { obj, key -> (obj[key] as? JsonPrimitive)?.content?.toIntOrNull() }
    private val optBool: (JsonObject, String) -> Boolean =
        { obj, key -> (obj[key] as? JsonPrimitive)?.content == "true" }
    private val optList: (JsonObject, String) -> List<String> =
        { obj, key -> (obj[key] as? JsonArray)?.map { (it as JsonPrimitive).content }.orEmpty() }

    fun fromJson(document: JsonObject): BackupSnapshot =
        BackupSnapshot(
            mistakes = mistakes(document),
            userExamples = userExamples(document),
            reviews = reviews(document),
            idiomStates = idiomStates(document),
            scopes = scopes(document),
            tags = tags(document),
        )

    private fun mistakes(document: JsonObject): List<BackupMistake> =
        (document.getValue("mistakes") as JsonArray).map { row ->
            val item = row as JsonObject
            val options = item.getValue("options") as JsonObject
            BackupMistake(
                id = item.string("id"),
                userExampleId = item.optionalString("userExampleId"),
                imagePath = item.optionalString("imagePath"),
                ocrText = item.optionalString("ocrText"),
                stem = item.string("stem"),
                options =
                    BackupOptions(
                        a = options.string("A"),
                        b = options.string("B"),
                        c = options.string("C"),
                        d = options.string("D"),
                    ),
                myAnswer = item.optionalString("myAnswer"),
                reason = item.optionalString("reason"),
                tagNames = optList(item, "tagNames"),
                examType = item.optionalString("examType"),
                year = optInt(item, "year"),
                region = item.optionalString("region"),
                state = item.string("state"),
                createdAt = item.string("createdAt"),
                lastReviewedAt = item.optionalString("lastReviewedAt"),
                reviewCount = item.integer("reviewCount"),
                idiomIds = optList(item, "idiomIds"),
            )
        }

    private fun userExamples(document: JsonObject): List<BackupUserExample> =
        (document["userExamples"] as? JsonArray).orEmpty().map { row ->
            val item = row as JsonObject
            BackupUserExample(
                id = item.string("id"),
                imagePath = item.optionalString("imagePath"),
                ocrText = item.optionalString("ocrText"),
                stem = item.string("stem"),
                optionA = item.optionalString("optionA"),
                optionB = item.optionalString("optionB"),
                optionC = item.optionalString("optionC"),
                optionD = item.optionalString("optionD"),
                myAnswer = item.optionalString("myAnswer"),
                isMistake = optBool(item, "isMistake"),
                examType = item.optionalString("examType"),
                year = optInt(item, "year"),
                region = item.optionalString("region"),
                createdAt = item.string("createdAt"),
                idiomIds = optList(item, "idiomIds"),
            )
        }

    private fun reviews(document: JsonObject): List<BackupReview> =
        (document.getValue("reviews") as JsonArray).map { row ->
            val item = row as JsonObject
            BackupReview(
                id = item.string("id"),
                mistakeId = item.string("mistakeId"),
                result = item.string("result"),
                at = item.string("at"),
            )
        }

    private fun idiomStates(document: JsonObject): List<BackupIdiomState> =
        (document.getValue("idiomStates") as JsonArray).map { row ->
            val item = row as JsonObject
            BackupIdiomState(
                idiomId = item.string("idiomId"),
                status = item.string("status"),
                favorite = optBool(item, "favorite"),
            )
        }

    private fun scopes(document: JsonObject): List<BackupScope> =
        (document.getValue("scopes") as JsonArray).map { row ->
            val item = row as JsonObject
            BackupScope(
                type = item.string("type"),
                province = item.optionalString("province"),
                enabled = optBool(item, "enabled"),
            )
        }

    private fun tags(document: JsonObject): List<BackupTag> =
        (document.getValue("tags") as JsonArray).map { row ->
            BackupTag(name = (row as JsonObject).string("name"))
        }
}
