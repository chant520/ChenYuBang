package com.chenyubang.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PackCanonicalTest {
    @Test
    fun authenticCombinedDigestMatchesIndependentPythonFact() {
        assertEquals(
            PackFixtureTest.DIGEST,
            PackCanonical.digest(PackFixtureTest.json("idioms"), PackFixtureTest.json("examples")),
        )
    }

    @Test
    fun sortsByCodePointNotUtf16AndNormalizesKeysAndValues() {
        val value =
            JsonObject(
                mapOf("😀" to JsonPrimitive(2), "\ue000" to JsonPrimitive(1), "e\u0301" to JsonPrimitive("e\u0301")),
            )
        assertEquals("{\"é\":\"é\",\"\ue000\":1,\"😀\":2}", PackCanonical.text(value))
    }

    @Test
    fun controlEscapesMatchPythonWithoutAsciiOrSlashEscaping() {
        assertEquals(
            "\"é/\\\"\\\\\\b\\f\\n\\r\\t\\u0000\\u001f\u2028\"",
            PackCanonical.text(JsonPrimitive("e\u0301/\"\\\b\u000c\n\r\t\u0000\u001f\u2028")),
        )
    }

    @Test
    fun acceptsWholeFloatAsIntegerButNotNonintegralOrExcessiveExponent() {
        assertEquals("[1,100,0]", PackCanonical.text(PackJson.parse("[1.0,1e2,-0.0]".toByteArray())))
        for ((number, expected) in listOf(
            "1.0000000000000001" to "1",
            "9007199254740993.0" to "9007199254740992",
            "9007199254740993" to "9007199254740993",
            "1e30" to "1000000000000000019884624838656",
            "-0.0" to "0",
        )) {
            assertEquals(number, expected, PackCanonical.text(PackJson.parse(number.toByteArray())))
        }
        for (number in listOf("1.5", "1e999999999", "NaN", "+1", "01")) {
            assertThrows(Exception::class.java) { PackJson.parse(number.toByteArray()) }
        }
    }

    @Test
    fun rejectsDuplicateKeysAndNfcKeyCollisionsBeforeObjectConstruction() {
        for (json in listOf("{\"id\":1,\"id\":2}", "{\"é\":1,\"e\\u0301\":2}")) {
            assertThrows(IllegalArgumentException::class.java) { PackJson.parse(json.toByteArray()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PackCanonical.text(JsonObject(mapOf("é" to JsonPrimitive(1), "e\u0301" to JsonPrimitive(2))))
        }
    }

    @Test
    fun realManifestDuplicateKeysAreRejectedBeforeDigestOrSchema() {
        val files = PackFixtureTest.files()
        val metadata = files.getValue("manifest.json").toString(Charsets.UTF_8)
        for (prefix in listOf("{\"schemaVersion\":1,", "{\"é\":1,\"e\\u0301\":2,")) {
            val modified = metadata.replaceFirst("{", prefix).toByteArray()
            val failure =
                assertThrows(IllegalArgumentException::class.java) {
                    PackFixtureTest.validate(PackFixtureTest.archive(files + ("manifest.json" to modified)))
                }
            assertTrue(failure.message.orEmpty().contains("Duplicate JSON key"))
        }
    }

    @Test
    fun rejectsInvalidJsonSyntaxAndSurrogates() {
        for (json in listOf("[1,]", "{id:1}", "{\"id\":1,}", "\"\\uD800\"", "\"\\uDC00\"", "\"\\x41\"")) {
            assertThrows(Exception::class.java) { PackJson.parse(json.toByteArray()) }
        }
    }
}
