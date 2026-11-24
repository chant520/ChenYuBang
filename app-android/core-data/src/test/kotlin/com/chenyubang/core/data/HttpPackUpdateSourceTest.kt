package com.chenyubang.core.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL

class HttpPackUpdateSourceTest {
    @Test
    fun latestUsesHttpsGetAcceptOnlyAndPlatformTimeouts() =
        runBlocking {
            val connection = FakeConnection(LATEST, body = manifestBytes())
            val result = HttpPackUpdateSource(LATEST, connectionOpener = { connection }).latest()
            assertTrue(result is ChannelManifestResult.Success)
            assertEquals("GET", connection.requestMethod)
            assertEquals("application/json", connection.requestProperties["Accept"])
            assertEquals(setOf("Accept"), connection.requestProperties.keys)
            assertEquals(10_000, connection.connectTimeout)
            assertEquals(30_000, connection.readTimeout)
            assertFalse(connection.instanceFollowRedirects)
            assertFalse(connection.useCaches)
            assertTrue(connection.disconnected)
        }

    @Test
    fun insecureOrMissingConfigurationNeverOpensConnection() =
        runBlocking {
            var opened = 0
            val opener =
                PackConnectionOpener {
                    opened++
                    error("must not open")
                }
            assertTrue(
                HttpPackUpdateSource(null, connectionOpener = opener).latest() is ChannelManifestResult.Unavailable,
            )
            val insecure = HttpPackUpdateSource("http://example.com/latest.json", connectionOpener = opener).latest()
            assertTrue(insecure is ChannelManifestResult.Recoverable)
            assertTrue((insecure as ChannelManifestResult.Recoverable).reason.contains("HTTPS"))
            assertEquals(0, opened)
        }

    @Test
    fun latestRejectsNon2xxMalformedOversizeAndInterruptedBodies() =
        runBlocking {
            val cases =
                listOf(
                    FakeConnection(LATEST, code = 503, body = byteArrayOf()),
                    FakeConnection(LATEST, body = "not-json".toByteArray()),
                    FakeConnection(LATEST, body = ByteArray(64 * 1024 + 1)),
                    FakeConnection(LATEST, body = manifestBytes(), failure = InterruptedIOException("interrupted")),
                )
            cases.forEach { connection ->
                val result = HttpPackUpdateSource(LATEST, connectionOpener = { connection }).latest()
                assertTrue(result is ChannelManifestResult.Recoverable)
                assertTrue(connection.disconnected)
            }
        }

    @Test
    fun redirectsStayWithinInitialOriginAndAreBounded() =
        runBlocking {
            val first = FakeConnection(LATEST, code = 302, headers = mapOf("Location" to "/channel/moved.json"))
            val second = FakeConnection("https://packs.example.com/channel/moved.json", body = manifestBytes())
            var index = 0
            val accepted =
                HttpPackUpdateSource(LATEST, connectionOpener = {
                    if (index++ == 0) first else second
                }).latest()
            assertTrue(accepted is ChannelManifestResult.Success)
            assertTrue(first.disconnected)
            assertTrue(second.disconnected)

            for (location in listOf(
                "https://evil.example/latest.json",
                "http://packs.example.com/latest.json",
                "https://packs.example.com:444/latest.json",
            )) {
                val redirect = FakeConnection(LATEST, code = 301, headers = mapOf("Location" to location))
                val result = HttpPackUpdateSource(LATEST, connectionOpener = { redirect }).latest()
                assertTrue(result is ChannelManifestResult.Recoverable)
                assertTrue(redirect.disconnected)
            }
            val loop = FakeConnection(LATEST, code = 302, headers = mapOf("Location" to LATEST))
            val loops = HttpPackUpdateSource(LATEST, connectionOpener = { loop }).latest()
            assertTrue(loops is ChannelManifestResult.Recoverable)
            assertEquals(6, loop.opens)
            assertTrue(loop.disconnected)
        }

    @Test
    fun downloadStreamsExactBytesAndDeletesFailedPartialFile() =
        runBlocking {
            val body = "zip-bytes".toByteArray()
            val manifest = channel(size = body.size)
            val target = File.createTempFile("channel-download", ".zip")
            val connection =
                FakeConnection(
                    manifest.zipUrl(LATEST),
                    body = body,
                    headers = mapOf("Content-Length" to "${body.size}"),
                )
            val progress = mutableListOf<Int>()
            try {
                val result =
                    HttpPackUpdateSource(LATEST, connectionOpener = { connection })
                        .download(manifest, target) { progress += it }
                assertEquals(PackDownloadResult.Success, result)
                assertTrue(body.contentEquals(target.readBytes()))
                assertEquals("GET", connection.requestMethod)
                assertEquals("application/zip", connection.requestProperties["Accept"])
                assertEquals(setOf("Accept"), connection.requestProperties.keys)
                assertEquals(listOf(100), progress)
                assertTrue(connection.disconnected)
            } finally {
                target.delete()
            }
        }

    @Test
    fun downloadRejectsDeclaredOrActualSizeMismatchInterruptionAndLimit() =
        runBlocking {
            val body = "zip-bytes".toByteArray()
            val manifest = channel(size = body.size)
            val connections =
                listOf(
                    FakeConnection(manifest.zipUrl(LATEST), body = body, headers = mapOf("Content-Length" to "99")),
                    FakeConnection(manifest.zipUrl(LATEST), body = body.copyOf(body.size - 1)),
                    FakeConnection(manifest.zipUrl(LATEST), body = body + 1),
                    FakeConnection(
                        manifest.zipUrl(LATEST),
                        body = body,
                        failure = InterruptedIOException("interrupted"),
                    ),
                    FakeConnection(
                        manifest.zipUrl(LATEST),
                        body = body,
                        headers = mapOf("Content-Length" to "${ChannelManifest.MAX_SIZE_BYTES + 1}"),
                    ),
                )
            connections.forEach { connection ->
                val target = File.createTempFile("channel-download-fail", ".zip")
                try {
                    val result =
                        HttpPackUpdateSource(LATEST, connectionOpener = { connection }).download(manifest, target) {}
                    assertTrue(result is PackDownloadResult.Recoverable)
                    assertFalse(target.exists())
                    assertTrue(connection.disconnected)
                } finally {
                    target.delete()
                }
            }
        }

    private fun channel(size: Int) =
        ChannelManifest(
            schemaVersion = 1,
            packVersion = "v2.0.0",
            builtAt = "2026-02-28T00:00:00Z",
            counts = PackCounts(59, 11),
            sha256 = "a".repeat(64),
            sizeBytes = size,
            packFile = "pack-v2.0.0.zip",
        )

    private fun manifestBytes(): ByteArray =
        (
            "{\"schemaVersion\":1,\"packVersion\":\"v1.2.3\",\"builtAt\":\"2026-02-28T00:00:00Z\"," +
                "\"counts\":{\"idioms\":59,\"examples\":11},\"sha256\":\"${"a".repeat(64)}\"," +
                "\"sizeBytes\":1234,\"packFile\":\"pack-v1.2.3.zip\"}"
        ).toByteArray()

    private class FakeConnection(
        url: String,
        private val code: Int = 200,
        private val body: ByteArray = byteArrayOf(),
        private val headers: Map<String, String> = emptyMap(),
        private val failure: IOException? = null,
    ) : HttpURLConnection(URL(url)) {
        val requestProperties = linkedMapOf<String, String>()
        var disconnected = false
        var opens = 0

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int {
            opens++
            return code
        }

        override fun getHeaderField(name: String): String? =
            headers.entries.firstOrNull {
                it.key.equals(
                    name,
                    true,
                )
            }?.value

        override fun getInputStream(): InputStream = failure?.let { throw it } ?: ByteArrayInputStream(body)

        override fun setRequestProperty(
            key: String,
            value: String?,
        ) {
            if (value != null) requestProperties[key] = value
        }
    }

    private companion object {
        const val LATEST = "https://packs.example.com/channel/latest.json"
    }
}
