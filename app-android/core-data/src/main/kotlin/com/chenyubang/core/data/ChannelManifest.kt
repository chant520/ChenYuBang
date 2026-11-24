package com.chenyubang.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** latest.json 的严格投影；只接受规范字段，不使用宽松 JSON 解码器。 */
data class ChannelManifest(
    val schemaVersion: Int,
    val packVersion: String,
    val builtAt: String,
    val counts: PackCounts,
    val sha256: String,
    val sizeBytes: Int,
    val packFile: String,
) {
    fun zipUrl(latestUrl: String): String {
        val base = URL(latestUrl)
        val resolved = URL(base, packFile)
        requireSameOrigin(base, resolved)
        require(resolved.path.substringBeforeLast('/') == base.path.substringBeforeLast('/')) {
            "Channel pack must be beside latest.json"
        }
        return resolved.toExternalForm()
    }

    companion object {
        const val MAX_SIZE_BYTES = 32 * 1024 * 1024
        private val version = Regex("^v\\d+\\.\\d+\\.\\d+$")
        private val digest = Regex("^[0-9a-f]{64}$")
        private val builtAtFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private val fields =
            setOf(
                "schemaVersion",
                "packVersion",
                "builtAt",
                "counts",
                "sha256",
                "sizeBytes",
                "packFile",
            )

        fun parse(bytes: ByteArray): ChannelManifest {
            val root =
                PackJson.parse(bytes) as? JsonObject
                    ?: throw IllegalArgumentException("Channel manifest must be an object")
            require(root.keys == fields) { "Missing or unknown channel manifest field" }
            val schema = root.channelInteger("schemaVersion")
            require(schema == 1) { "Unsupported channel schemaVersion" }
            val packVersion = root.channelString("packVersion")
            require(version.matches(packVersion)) { "Invalid channel packVersion" }
            val builtAt = root.channelString("builtAt")
            requireValidBuiltAt(builtAt)
            val counts =
                root.getValue("counts") as? JsonObject
                    ?: throw IllegalArgumentException("Invalid channel counts")
            require(counts.keys == setOf("idioms", "examples")) { "Invalid channel counts fields" }
            val idiomCount = counts.channelInteger("idioms")
            val exampleCount = counts.channelInteger("examples")
            require(idiomCount > 0 && exampleCount > 0) { "Invalid channel counts" }
            val sha256 = root.channelString("sha256")
            require(digest.matches(sha256)) { "Invalid channel sha256" }
            val size = root.channelInteger("sizeBytes")
            require(size in 1..MAX_SIZE_BYTES) { "Invalid channel sizeBytes" }
            val packFile = root.channelString("packFile")
            require(packFile == "pack-$packVersion.zip") { "Invalid channel packFile" }
            require(!packFile.contains('/') && !packFile.contains('\\') && !packFile.contains(':')) {
                "Channel packFile must be a relative filename"
            }
            return ChannelManifest(
                schema,
                packVersion,
                builtAt,
                PackCounts(idiomCount, exampleCount),
                sha256,
                size,
                packFile,
            )
        }

        private fun requireValidBuiltAt(value: String) {
            require(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$").matches(value)) { "Invalid channel builtAt" }
            try {
                LocalDateTime.parse(value.dropLast(1), builtAtFormat)
            } catch (_: DateTimeParseException) {
                throw IllegalArgumentException("Invalid channel builtAt")
            }
        }
    }
}

internal fun requireSameOrigin(
    expected: URL,
    actual: URL,
) {
    require(
        expected.protocol.equals(actual.protocol, ignoreCase = true) &&
            expected.host.equals(actual.host, ignoreCase = true) &&
            effectivePort(expected) == effectivePort(actual) &&
            actual.userInfo == null,
    ) { "HTTPS origin changed" }
    require(actual.protocol.equals("https", ignoreCase = true)) { "Only HTTPS is allowed" }
}

internal fun effectivePort(url: URL): Int =
    if (url.port >= 0) {
        url.port
    } else if (url.protocol.equals("https", true)) {
        HTTPS_PORT
    } else {
        HTTP_PORT
    }

private const val HTTPS_PORT = 443
private const val HTTP_PORT = 80

private fun JsonObject.channelString(key: String): String =
    (getValue(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw IllegalArgumentException("Channel $key must be a string")

private fun JsonObject.channelInteger(key: String): Int =
    (getValue(key) as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
        ?: throw IllegalArgumentException("Channel $key must be an integer")
