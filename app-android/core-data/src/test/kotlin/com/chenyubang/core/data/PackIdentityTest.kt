package com.chenyubang.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PackIdentityTest {
    @Test
    fun rejectsDuplicateIdiomUuidAndWordIndependently() {
        for (field in listOf("id", "word")) {
            rejected(
                PackFixtureTest.mutate(idioms = { rows ->
                    val second = rows[1] as JsonObject
                    rows.firstChanged { it.with(field, second.getValue(field)) }
                }),
            )
        }
    }

    @Test
    fun rejectsDuplicateExampleUuid() {
        rejected(
            PackFixtureTest.mutate(examples = { rows ->
                rows.firstChanged { it.with("id", (rows[1] as JsonObject).getValue("id")) }
            }),
        )
    }

    @Test
    fun rejectsUuidSharedBetweenIdiomAndExample() {
        val id = (PackFixtureTest.json("idioms") as JsonArray).first() as JsonObject
        rejected(PackFixtureTest.mutate(examples = { it.firstChanged { row -> row.with("id", id.getValue("id")) } }))
    }

    @Test
    fun rejectsNonV4AndInvalidVariantUuid() {
        val original = ((PackFixtureTest.json("idioms") as JsonArray).first() as JsonObject).string("id")
        for (invalid in listOf(
            original.replaceRange(VERSION_INDEX, VERSION_INDEX + 1, "1"),
            original.replaceRange(VARIANT_INDEX, VARIANT_INDEX + 1, "7"),
        )) {
            rejected(
                PackFixtureTest.mutate(idioms = {
                    it.firstChanged {
                            row ->
                        row.with("id", JsonPrimitive(invalid))
                    }
                }),
            )
        }
    }

    @Test
    fun rejectsUnclosedReferenceWithValidUuid() {
        val missing = "00000000-0000-4000-8000-000000000000"
        rejected(
            PackFixtureTest.mutate(examples = {
                it.firstChanged { row -> row.with("idiomIds", JsonArray(listOf(JsonPrimitive(missing)))) }
            }),
        )
    }

    @Test
    fun validatedPackCannotBeDirectlyConstructedOrCopied() {
        assertFalse(ValidatedPack::class.java.constructors.any { !it.isSynthetic })
        assertFalse(ValidatedPack::class.java.methods.any { it.name == "copy" })
    }

    private fun rejected(bytes: ByteArray) {
        assertThrows(IllegalArgumentException::class.java) { PackFixtureTest.validate(bytes) }
    }

    companion object {
        private const val VERSION_INDEX = 14
        private const val VARIANT_INDEX = 19
    }
}
