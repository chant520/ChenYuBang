package com.chenyubang.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** 生产 endpoint 尚未拍板；保持空配置，不编造对象存储地址。 */
object PackUpdateConfig {
    val LATEST_URL: String? = null
}

interface PackUpdateSource {
    suspend fun latest(): ChannelManifestResult

    suspend fun download(
        manifest: ChannelManifest,
        target: File,
        onProgress: (Int) -> Unit = {},
    ): PackDownloadResult
}

sealed interface ChannelManifestResult {
    data class Success(val manifest: ChannelManifest, val latestUrl: String) : ChannelManifestResult

    data object Unavailable : ChannelManifestResult

    data class Recoverable(val reason: String) : ChannelManifestResult
}

sealed interface PackDownloadResult {
    data object Success : PackDownloadResult

    data class Recoverable(val reason: String) : PackDownloadResult
}

fun interface PackConnectionOpener {
    fun open(url: URL): HttpURLConnection
}

/** 纯 HTTPS GET；证书校验保持平台默认，不携带身份、学习数据、Cookie 或追踪头。 */
class HttpPackUpdateSource(
    val latestUrl: String? = PackUpdateConfig.LATEST_URL,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
    private val connectionOpener: PackConnectionOpener =
        PackConnectionOpener { url ->
            url.openConnection() as HttpURLConnection
        },
) : PackUpdateSource {
    override suspend fun latest(): ChannelManifestResult =
        withContext(Dispatchers.IO) {
            val url = latestUrl ?: return@withContext ChannelManifestResult.Unavailable
            runCatching {
                val bytes =
                    openSuccess(url, "application/json", MAX_MANIFEST_BYTES, expectedSize = null).use { input ->
                        val output = ByteArrayOutputStream()
                        input.copyBounded(output, MAX_MANIFEST_BYTES, expectedSize = null) { _, _ -> }
                        output.toByteArray()
                    }
                ChannelManifest.parse(bytes)
            }.fold(
                onSuccess = { ChannelManifestResult.Success(it, url) },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    ChannelManifestResult.Recoverable("题库清单读取失败：${error.message ?: error.javaClass.simpleName}")
                },
            )
        }

    override suspend fun download(
        manifest: ChannelManifest,
        target: File,
        onProgress: (Int) -> Unit,
    ): PackDownloadResult =
        withContext(Dispatchers.IO) {
            val latest = latestUrl ?: return@withContext PackDownloadResult.Recoverable("未配置题库更新地址")
            runCatching {
                val limit = minOf(ChannelManifest.MAX_SIZE_BYTES, manifest.sizeBytes)
                openSuccess(manifest.zipUrl(latest), "application/zip", limit, manifest.sizeBytes).use { input ->
                    require(target.parentFile?.isDirectory == true) { "Missing pack download directory" }
                    target.outputStream().use { output ->
                        input.copyBounded(output, limit, manifest.sizeBytes) { read, total ->
                            onProgress(((read * PROGRESS_PERCENT) / total).toInt().coerceIn(0, PROGRESS_PERCENT))
                        }
                    }
                }
                require(target.length() == manifest.sizeBytes.toLong()) { "题库下载字节数不符" }
                PackDownloadResult.Success
            }.fold(
                onSuccess = { it },
                onFailure = { error ->
                    target.delete()
                    if (error is CancellationException) throw error
                    PackDownloadResult.Recoverable("题库下载失败：${error.message ?: error.javaClass.simpleName}")
                },
            )
        }

    private fun openSuccess(
        address: String,
        accept: String,
        limit: Int,
        expectedSize: Int?,
    ): InputStream {
        val initial = secureUrl(address)
        var current = initial
        var connection: HttpURLConnection? = null
        var transferred = false
        try {
            repeat(MAX_REDIRECTS + 1) {
                connection?.disconnect()
                val opened =
                    connectionOpener.open(current).apply {
                        requestMethod = "GET"
                        connectTimeout = connectTimeoutMs
                        readTimeout = readTimeoutMs
                        instanceFollowRedirects = false
                        useCaches = false
                        setRequestProperty("Accept", accept)
                    }
                connection = opened
                val code = opened.responseCode
                if (code in REDIRECT_CODES) {
                    current = redirectUrl(opened, current, initial)
                } else {
                    require(code in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) { "HTTP $code" }
                    return responseStream(opened, limit, expectedSize).also { transferred = true }
                }
            }
            return tooManyRedirects()
        } finally {
            if (!transferred) connection?.disconnect()
        }
    }

    private fun redirectUrl(
        connection: HttpURLConnection,
        current: URL,
        initial: URL,
    ): URL {
        val location = connection.getHeaderField("Location") ?: throw IOException("重定向缺少 Location")
        return URL(current, location).also { requireSameOrigin(initial, it) }
    }

    private fun responseStream(
        connection: HttpURLConnection,
        limit: Int,
        expectedSize: Int?,
    ): InputStream {
        val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
        require(declared == null || declared in 0..limit.toLong()) { "Content-Length 非法或超限" }
        require(declared == null || expectedSize == null || declared == expectedSize.toLong()) { "响应字节数不符" }
        return ClosingInputStream(connection.inputStream) { connection.disconnect() }
    }

    private fun tooManyRedirects(): Nothing = throw IOException("重定向次数过多")

    private fun secureUrl(value: String): URL =
        URL(value).also {
            require(it.protocol.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() && it.userInfo == null) {
                "题库更新仅支持无用户信息的 HTTPS 地址"
            }
        }

    private fun InputStream.copyBounded(
        output: OutputStream,
        limit: Int,
        expectedSize: Int?,
        onProgress: (Long, Int) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (total < limit) {
            require(!Thread.currentThread().isInterrupted) { "题库下载已中断" }
            val count = read(buffer, 0, minOf(buffer.size, (limit - total).toInt()))
            val single = if (count == 0) readSingle() else 0
            if (count == -1 || single == -1) break
            if (count > 0) {
                output.write(buffer, 0, count)
                total += count
            } else {
                output.write(single)
                total++
            }
            onProgress(total, expectedSize ?: 0)
        }
        require(readSingle() == -1) { "响应超过大小上限" }
        require(expectedSize == null || total == expectedSize.toLong()) { "响应截断或字节数不符" }
    }

    private fun InputStream.readSingle(): Int {
        require(!Thread.currentThread().isInterrupted) { "题库下载已中断" }
        return read()
    }

    private class ClosingInputStream(
        input: InputStream,
        private val onClose: () -> Unit,
    ) : FilterInputStream(input) {
        override fun close() {
            try {
                super.close()
            } finally {
                onClose()
            }
        }
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 64 * 1024
        const val MAX_REDIRECTS = 5
        const val BUFFER_BYTES = 8192
        const val PROGRESS_PERCENT = 100
        const val HTTP_SUCCESS_MIN = 200
        const val HTTP_SUCCESS_MAX = 299
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
