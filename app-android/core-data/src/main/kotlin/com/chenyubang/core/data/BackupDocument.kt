package com.chenyubang.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 内存模型 ↔ data.json/manifest.json 的唯一编组点；序列化形状以 shared/schema 为准。 */
internal object BackupDocument {
    private val str: (String?) -> JsonElement = { if (it == null) JsonNull else JsonPrimitive(it) }
    private val num: (Int) -> JsonElement = { JsonPrimitive(it) }
    private val strings: (List<String>) -> JsonArray = { JsonArray(it.map { value -> JsonPrimitive(value) }) }

    fun toJson(snapshot: BackupSnapshot): JsonObject =
        JsonObject(
            linkedMapOf<String, JsonElement>(
                "mistakes" to JsonArray(snapshot.mistakes.map { mistakeJson(it) }),
                "userExamples" to JsonArray(snapshot.userExamples.map { userExampleJson(it) }),
                "reviews" to JsonArray(snapshot.reviews.map { reviewJson(it) }),
                "idiomStates" to JsonArray(snapshot.idiomStates.map { idiomStateJson(it) }),
                "scopes" to JsonArray(snapshot.scopes.map { scopeJson(it) }),
                "tags" to JsonArray(snapshot.tags.map { tagJson(it) }),
            ),
        )

    fun manifest(
        counts: BackupCounts,
        exportedAt: String,
        sha256: String,
    ): JsonObject =
        JsonObject(
            linkedMapOf<String, JsonElement>(
                "schemaVersion" to num(BackupSpec.SCHEMA_VERSION),
                "appVersion" to JsonPrimitive(BackupSpec.APP_VERSION),
                "build" to num(BackupSpec.BUILD),
                "exportedAt" to JsonPrimitive(exportedAt),
                "counts" to
                    JsonObject(
                        linkedMapOf<String, JsonElement>(
                            "mistakes" to num(counts.mistakes),
                            "userExamples" to num(counts.userExamples),
                            "reviews" to num(counts.reviews),
                            "idiomStates" to num(counts.idiomStates),
                            "scopes" to num(counts.scopes),
                        ),
                    ),
                "sha256" to JsonPrimitive(sha256),
            ),
        )

    /** 复用 PackCanonical 的规范化，协议等价 tools/canonical.py:sha256_of。 */
    fun sha256(document: JsonObject): String = PackCanonical.digest(document)

    fun counts(document: JsonObject): BackupCounts {
        val size: (String) -> Int = { (document[it] as? JsonArray)?.size ?: 0 }
        return BackupCounts(
            size("mistakes"),
            size("userExamples"),
            size("reviews"),
            size("idiomStates"),
            size("scopes"),
        )
    }

    /** 旧备份 manifest.counts 可缺 userExamples，按 0 兼容；与 data 缺数组时的 counts() 对齐。 */
    fun declaredCounts(manifest: JsonObject): BackupCounts {
        val counts = manifest.getValue("counts") as JsonObject
        return BackupCounts(
            mistakes = counts.integer("mistakes"),
            userExamples = counts.optionalString("userExamples")?.toIntOrNull() ?: 0,
            reviews = counts.integer("reviews"),
            idiomStates = counts.integer("idiomStates"),
            scopes = counts.integer("scopes"),
        )
    }

    private fun mistakeJson(mistake: BackupMistake): JsonObject =
        JsonObject(
            linkedMapOf<String, JsonElement>(
                "id" to JsonPrimitive(mistake.id),
                "userExampleId" to str(mistake.userExampleId),
                "imagePath" to str(mistake.imagePath),
                "ocrText" to str(mistake.ocrText),
                "stem" to JsonPrimitive(mistake.stem),
                "options" to
                    JsonObject(
                        linkedMapOf<String, JsonElement>(
                            "A" to JsonPrimitive(mistake.options.a),
                            "B" to JsonPrimitive(mistake.options.b),
                            "C" to JsonPrimitive(mistake.options.c),
                            "D" to JsonPrimitive(mistake.options.d),
                        ),
                    ),
                "myAnswer" to str(mistake.myAnswer),
                "reason" to str(mistake.reason),
                "tagNames" to strings(mistake.tagNames),
                "examType" to str(mistake.examType),
                "year" to if (mistake.year == null) JsonNull else JsonPrimitive(mistake.year),
                "region" to str(mistake.region),
                "state" to JsonPrimitive(mistake.state),
                "createdAt" to JsonPrimitive(mistake.createdAt),
                "lastReviewedAt" to str(mistake.lastReviewedAt),
                "reviewCount" to num(mistake.reviewCount),
                "idiomIds" to strings(mistake.idiomIds),
            ),
        )

    private fun userExampleJson(example: BackupUserExample): JsonObject =
        JsonObject(
            linkedMapOf<String, JsonElement>(
                "id" to JsonPrimitive(example.id),
                "imagePath" to str(example.imagePath),
                "ocrText" to str(example.ocrText),
                "stem" to JsonPrimitive(example.stem),
                "optionA" to str(example.optionA),
                "optionB" to str(example.optionB),
                "optionC" to str(example.optionC),
                "optionD" to str(example.optionD),
                "myAnswer" to str(example.myAnswer),
                "isMistake" to JsonPrimitive(example.isMistake),
                "examType" to str(example.examType),
                "year" to if (example.year == null) JsonNull else JsonPrimitive(example.year),
                "region" to str(example.region),
                "createdAt" to JsonPrimitive(example.createdAt),
                "idiomIds" to strings(example.idiomIds),
            ),
        )

    private fun reviewJson(review: BackupReview): JsonObject =
        JsonObject(
            linkedMapOf<String, JsonElement>(
                "id" to JsonPrimitive(review.id),
                "mistakeId" to JsonPrimitive(review.mistakeId),
                "result" to JsonPrimitive(review.result),
                "at" to JsonPrimitive(review.at),
            ),
        )

    private fun idiomStateJson(state: BackupIdiomState): JsonObject =
        JsonObject(
            linkedMapOf<String, JsonElement>(
                "idiomId" to JsonPrimitive(state.idiomId),
                "status" to JsonPrimitive(state.status),
                "favorite" to JsonPrimitive(state.favorite),
            ),
        )

    private fun scopeJson(scope: BackupScope): JsonObject =
        JsonObject(
            linkedMapOf<String, JsonElement>(
                "type" to JsonPrimitive(scope.type),
                "province" to str(scope.province),
                "enabled" to JsonPrimitive(scope.enabled),
            ),
        )

    private fun tagJson(tag: BackupTag): JsonObject =
        JsonObject(linkedMapOf<String, JsonElement>("name" to JsonPrimitive(tag.name)))
}
