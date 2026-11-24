package com.chenyubang.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val HTTP_OK_MIN = 200
private const val HTTP_OK_MAX = 299

/**
 * 题库更新通道（规范 §4.6）。生产地址 bucket/endpoint 未在任何事实源定稿，故 URL 由调用方注入；
 * 未给 URL 时 [HttpPackUpdateSource] 返回 [PackUpdateFetch.Unavailable]，绝不编造地址。
 */
interface PackUpdateSource {
    suspend fun fetch(url: String?): PackUpdateFetch
}

sealed interface PackUpdateFetch {
    data class Success(
        val zipBytes: ByteArray,
    ) : PackUpdateFetch

    object Unavailable : PackUpdateFetch

    data class Recoverable(
        val reason: String,
    ) : PackUpdateFetch
}

/** HttpURLConnection 骨架：仅 HTTPS、纯 GET、不携带用户数据；超时 10s/30s；不自动跟随重定向。 */
class HttpPackUpdateSource(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
) : PackUpdateSource {
    override suspend fun fetch(url: String?): PackUpdateFetch =
        when {
            url.isNullOrBlank() -> PackUpdateFetch.Unavailable
            !url.startsWith("https://", ignoreCase = true) ->
                PackUpdateFetch.Recoverable("题库更新仅支持 HTTPS 地址")
            else -> download(url)
        }

    private suspend fun download(url: String): PackUpdateFetch =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection =
                    (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = connectTimeoutMs
                        readTimeout = readTimeoutMs
                        instanceFollowRedirects = false
                    }
                val code = connection.responseCode
                if (code !in HTTP_OK_MIN..HTTP_OK_MAX) {
                    return@withContext PackUpdateFetch.Recoverable("题库下载失败（HTTP $code）")
                }
                PackUpdateFetch.Success(readBounded(connection.inputStream, MAX_DOWNLOAD_BYTES))
            } catch (error: IOException) {
                PackUpdateFetch.Recoverable("题库下载中断：${error.message ?: error.javaClass.simpleName}")
            } finally {
                connection?.disconnect()
            }
        }

    private fun readBounded(
        input: InputStream,
        limit: Int,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
            if (count == -1) return output.toByteArray()
            if (count == 0) {
                val byte = input.read()
                if (byte == -1) break
                require(output.size() < limit) { "Pack download byte limit" }
                output.write(byte)
            } else {
                require(count <= limit - output.size()) { "Pack download byte limit" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private companion object {
        const val BUFFER_BYTES = 8192
        const val MAX_DOWNLOAD_BYTES = 32 * 1024 * 1024
    }
}
