package com.chenyubang.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class PackIdiom(val entity: IdiomEntity, val scopes: Set<String>)

internal data class PackExample(val entity: ExampleEntity, val idiomIds: Set<String>)

internal object PackRows {
    fun idioms(pack: ValidatedPack): List<PackIdiom> =
        pack.idioms.map { element ->
            val row = element as JsonObject
            PackIdiom(
                entity =
                    IdiomEntity(
                        id = row.string("id"),
                        word = row.string("word"),
                        pinyin = row.string("pinyin"),
                        initials = row.string("initials"),
                        sense = row.string("sense"),
                        origin = row.optionalString("origin"),
                        color = row.string("color"),
                        pitfalls = PackCanonical.text(row["pitfalls"] ?: JsonArray(emptyList())),
                        synonyms = PackCanonical.text(row["synonyms"] ?: JsonArray(emptyList())),
                        antonyms = PackCanonical.text(row["antonyms"] ?: JsonArray(emptyList())),
                        frequency = row.integer("frequency"),
                    ),
                scopes = strings(row, "scopes"),
            )
        }

    fun examples(pack: ValidatedPack): List<PackExample> =
        pack.examples.map { element ->
            val row = element as JsonObject
            val options = row.getValue("options") as JsonObject
            PackExample(
                entity =
                    ExampleEntity(
                        id = row.string("id"),
                        stem = row.string("stem"),
                        optionA = options.string("A"),
                        optionB = options.string("B"),
                        optionC = options.string("C"),
                        optionD = options.string("D"),
                        answer = row.string("answer"),
                        explain = row.string("explain"),
                        examType = row.string("examType"),
                        year = row.integer("year"),
                        region = row.optionalString("region") ?: "",
                        questionType = row.string("questionType"),
                    ),
                idiomIds = strings(row, "idiomIds"),
            )
        }

    private fun strings(
        row: JsonObject,
        key: String,
    ): Set<String> = (row.getValue(key) as JsonArray).map { (it as JsonPrimitive).content }.toSet()
}
