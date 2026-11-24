package com.chenyubang.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.URL

class ChannelManifestTest {
    @Test
    fun acceptsOnlyExactTypedManifestAndResolvesSiblingZip() {
        val manifest = ChannelManifest.parse(valid())
        assertEquals(1, manifest.schemaVersion)
        assertEquals("v1.2.3", manifest.packVersion)
        assertEquals(PackCounts(59, 11), manifest.counts)
        for (latest in listOf(
            LATEST,
            "https://packs.example.com:443/channel/latest.json?token=ignored",
        )) {
            val resolved = URL(manifest.zipUrl(latest))
            requireSameOrigin(URL(latest), resolved)
            assertEquals("/channel/pack-v1.2.3.zip", resolved.path)
        }
    }

    @Test
    fun rejectsMissingUnknownDuplicateAndWronglyTypedFields() {
        val invalid =
            listOf(
                validString().replace("\"schemaVersion\":1,", ""),
                validString().replace("\"schemaVersion\":1,", "\"schemaVersion\":1,\"unknown\":true,"),
                validString().replace("\"schemaVersion\":1,", "\"schemaVersion\":1,\"schemaVersion\":1,"),
                validString().replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                validString().replace("\"sizeBytes\":1234", "\"sizeBytes\":\"1234\""),
                validString().replace("\"packVersion\":\"v1.2.3\"", "\"packVersion\":123"),
                validString().replace("\"counts\":{\"idioms\":59,\"examples\":11}", "\"counts\":{\"idioms\":59}"),
                validString().replace(
                    "\"counts\":{\"idioms\":59,\"examples\":11}",
                    "\"counts\":{\"idioms\":59,\"examples\":11,\"x\":1}",
                ),
                validString().replace("\"examples\":11", "\"examples\":11,\"examples\":11"),
                "[]",
                validString() + " trailing",
            )
        invalid.forEach {
                bytes ->
            assertThrows(IllegalArgumentException::class.java) { ChannelManifest.parse(bytes.toByteArray()) }
        }
    }

    @Test
    fun rejectsNewerSchemaBadIdentityDatesCountsDigestAndSizes() {
        val invalid =
            listOf(
                validString().replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                validString().replace("v1.2.3", "1.2.3"),
                validString().replace("2026-02-28T00:00:00Z", "2026-02-30T00:00:00Z"),
                validString().replace("2026-02-28T00:00:00Z", "2026-02-28T00:00:00+00:00"),
                validString().replace("\"idioms\":59", "\"idioms\":0"),
                validString().replace("\"examples\":11", "\"examples\":-1"),
                validString().replace("a".repeat(64), "A".repeat(64)),
                validString().replace("a".repeat(64), "a".repeat(63)),
                validString().replace("\"sizeBytes\":1234", "\"sizeBytes\":0"),
                validString().replace("\"sizeBytes\":1234", "\"sizeBytes\":33554433"),
                validString().replace("\"sizeBytes\":1234", "\"sizeBytes\":999999999999999999999"),
            )
        invalid.forEach {
                bytes ->
            assertThrows(IllegalArgumentException::class.java) { ChannelManifest.parse(bytes.toByteArray()) }
        }
    }

    @Test
    fun rejectsAbsoluteTraversalNestedAndMismatchedPackNames() {
        val names =
            listOf(
                "https://evil.example/pack-v1.2.3.zip",
                "../pack-v1.2.3.zip",
                "sub/pack-v1.2.3.zip",
                "sub\\pack-v1.2.3.zip",
                "pack-v1.2.4.zip",
                "pack-v1.2.3.zip?x=1",
            )
        names.forEach { name ->
            val bytes = validString().replace("pack-v1.2.3.zip", name)
            assertThrows(name, IllegalArgumentException::class.java) { ChannelManifest.parse(bytes.toByteArray()) }
        }
    }

    @Test
    fun rejectsCrossOriginSchemePortAndDifferentDirectory() {
        val manifest = ChannelManifest.parse(valid())
        val expected = URL(LATEST)
        for (url in listOf(
            "https://evil.example/channel/latest.json",
            "http://packs.example.com/channel/latest.json",
            "https://packs.example.com:444/channel/latest.json",
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                requireSameOrigin(expected, URL(url))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            manifest.copy(packFile = "../pack-v1.2.3.zip").zipUrl(LATEST)
        }
    }

    private fun valid(): ByteArray = validString().toByteArray()

    private fun validString(): String =
        (
            "{\"schemaVersion\":1,\"packVersion\":\"v1.2.3\",\"builtAt\":\"2026-02-28T00:00:00Z\"," +
                "\"counts\":{\"idioms\":59,\"examples\":11},\"sha256\":\"${"a".repeat(64)}\"," +
                "\"sizeBytes\":1234,\"packFile\":\"pack-v1.2.3.zip\"}"
        )

    private companion object {
        const val LATEST = "https://packs.example.com/channel/latest.json"
    }
}

class PackVersionTest {
    @Test
    fun comparesEveryNumericSegmentWithoutIntegerConversion() {
        assertEquals(0, PackVersion.compare("v01.002.0003", "v1.2.3"))
        assertEquals(-1, PackVersion.compare("v1.2.9", "v1.2.10"))
        assertEquals(1, PackVersion.compare("v1.10.0", "v1.9.999"))
        assertEquals(-1, PackVersion.compare("v2.999.999", "v3.0.0"))
        assertEquals(1, PackVersion.compare("v999999999999999999999.0.0", "v2.0.0"))
        assertEquals(-1, PackVersion.compare("v0.00000000000000000001.0", "v0.2.0"))
        assertThrows(IllegalArgumentException::class.java) { PackVersion.compare("1.2.3", "v1.2.3") }
    }
}
