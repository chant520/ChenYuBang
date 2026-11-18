package com.chenyubang.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PackSchemaTest {
    @Test
    fun rejectsWrongDigestAndUnsupportedSchemaVersion() {
        rejected(PackFixtureTest.mutate(manifest = { it.with("sha256", JsonPrimitive("0".repeat(DIGEST_LENGTH))) }))
        rejected(PackFixtureTest.mutate(manifest = { it.with("schemaVersion", JsonPrimitive(2)) }))
    }

    @Test
    fun rejectsMissingRequiredFieldAndWrongCounts() {
        rejected(PackFixtureTest.mutate(idioms = { it.firstChanged { row -> JsonObject(row - "sense") } }))
        rejected(
            PackFixtureTest.mutate(manifest = {
                it.with("counts", (it.getValue("counts") as JsonObject).with("idioms", JsonPrimitive(1)))
            }),
        )
    }

    @Test
    fun rejectsWrongTypesWithRecomputedDigest() {
        rejected(
            PackFixtureTest.mutate(idioms = {
                it.firstChanged {
                        row ->
                    row.with("frequency", JsonPrimitive("1"))
                }
            }),
        )
        rejected(
            PackFixtureTest.mutate(idioms = {
                it.firstChanged {
                        row ->
                    row.with("scopes", JsonPrimitive("国考"))
                }
            }),
        )
        rejected(
            PackFixtureTest.mutate(examples = {
                it.firstChanged {
                        row ->
                    row.with("year", JsonPrimitive(false))
                }
            }),
        )
        rejected(PackFixtureTest.mutate(idioms = { it.firstChanged { row -> row.with("origin", JsonNull) } }))
    }

    @Test
    fun rejectsUnknownEnumsWithRecomputedDigest() {
        rejected(PackFixtureTest.mutate(idioms = { it.firstChanged { row -> row.with("color", JsonPrimitive("未知")) } }))
        rejected(
            PackFixtureTest.mutate(examples = {
                it.firstChanged {
                        row ->
                    row.with("answer", JsonPrimitive("E"))
                }
            }),
        )
        rejected(
            PackFixtureTest.mutate(examples = {
                it.firstChanged { row -> row.with("questionType", JsonPrimitive("未知")) }
            }),
        )
    }

    @Test
    fun rejectsAdditionalPropertiesAtEveryObjectLevel() {
        rejected(PackFixtureTest.mutate(manifest = { it.with("extra", JsonPrimitive(1)) }))
        rejected(
            PackFixtureTest.mutate(manifest = {
                it.with("counts", (it.getValue("counts") as JsonObject).with("extra", JsonPrimitive(1)))
            }),
        )
        rejected(PackFixtureTest.mutate(idioms = { it.firstChanged { row -> row.with("extra", JsonPrimitive(1)) } }))
        rejected(PackFixtureTest.mutate(examples = { it.firstChanged { row -> row.with("extra", JsonPrimitive(1)) } }))
        rejected(
            PackFixtureTest.mutate(examples = {
                it.firstChanged {
                        row ->
                    row.with("options", (row.getValue("options") as JsonObject).with("E", JsonPrimitive("未知")))
                }
            }),
        )
    }

    @Test
    fun validatesNestedPitfallsTypesAndEnums() {
        val bad = JsonArray(listOf(JsonObject(mapOf("kind" to JsonPrimitive("未知"), "note" to JsonPrimitive("边界测试")))))
        rejected(PackFixtureTest.mutate(idioms = { it.firstChanged { row -> row.with("pitfalls", bad) } }))
        rejected(
            PackFixtureTest.mutate(idioms = {
                it.firstChanged { row -> row.with("synonyms", JsonArray(listOf(JsonPrimitive(1)))) }
            }),
        )
    }

    @Test
    fun rejectsPatternsBoundsAndEmptyArrays() {
        rejected(PackFixtureTest.mutate(idioms = { it.firstChanged { row -> row.with("word", JsonPrimitive("abc")) } }))
        rejected(
            PackFixtureTest.mutate(idioms = {
                it.firstChanged {
                        row ->
                    row.with("frequency", JsonPrimitive(0))
                }
            }),
        )
        rejected(
            PackFixtureTest.mutate(idioms = {
                it.firstChanged {
                        row ->
                    row.with("scopes", JsonArray(emptyList()))
                }
            }),
        )
        rejected(PackFixtureTest.mutate(examples = { it.firstChanged { row -> row.with("year", JsonPrimitive(1)) } }))
        rejected(
            PackFixtureTest.mutate(examples = {
                it.firstChanged {
                        row ->
                    row.with("idiomIds", JsonArray(emptyList()))
                }
            }),
        )
        rejected(PackFixtureTest.mutate(manifest = { it.with("packVersion", JsonPrimitive("latest")) }))
        rejected(PackFixtureTest.mutate(manifest = { it.with("builtAt", JsonPrimitive("2026")) }))
    }

    @Test
    fun provinceRequiresNonblankRegionButNationalRegionMayBeOmitted() {
        for (region in listOf<JsonPrimitive?>(null, JsonPrimitive("  "), JsonPrimitive(""))) {
            rejected(
                PackFixtureTest.mutate(examples = {
                    it.firstChanged {
                            row ->
                        if (region == null) JsonObject(row - "region") else row.with("region", region)
                    }
                }),
            )
        }
        val pack =
            PackFixtureTest.validate(
                PackFixtureTest.mutate(examples = { rows ->
                    JsonArray(
                        rows.map { element ->
                            val row = element as JsonObject
                            if (row.string("examType") == "国考") JsonObject(row - "region") else row
                        },
                    )
                }),
            )
        assertTrue(PackRows.examples(pack).filter { it.entity.examType == "国考" }.all { it.entity.region == "" })
        assertEquals(PackFixtureTest.EXAMPLE_COUNT, pack.counts.examples)
    }

    private fun rejected(bytes: ByteArray) {
        assertThrows(IllegalArgumentException::class.java) { PackFixtureTest.validate(bytes) }
    }

    companion object {
        private const val DIGEST_LENGTH = 64
    }
}
