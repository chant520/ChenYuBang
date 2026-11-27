package com.chenyubang.feature.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.math.max

/**
 * P17 拍照/选图的图片落本机缓存（零联网）：P18 裁切与后续识别都读这个文件。
 * 放在 cacheDir 而非用户数据目录——拍照链路中途取消即整条丢弃，不做草稿。
 */
internal object CaptureImageStore {
    private const val CAPTURE_DIR = "capture"
    private const val MAX_SOURCE_BYTES = 30L * 1024 * 1024
    private const val MAX_IMPORT_DIM = 3200
    private const val COPY_BUFFER_SIZE = 8 * 1024
    private const val IMPORT_JPEG_QUALITY = 85

    fun newCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.jpg")
    }

    /** 超长边按 2 的幂抽样；纯函数便于单测。 */
    internal fun sampleSize(
        width: Int,
        height: Int,
        maxDim: Int = MAX_IMPORT_DIM,
    ): Int {
        var sample = 1
        while (max(width, height) / sample > maxDim) sample *= 2
        return sample
    }

    /** 取消/重拍时删除本次缓存，不留草稿。 */
    internal fun delete(file: File?): Boolean = file?.let { if (it.exists()) it.delete() else true } ?: true

    internal fun delete(path: String?): Boolean = path?.let { delete(File(it)) } ?: true

    /** 把相册选中的照片复制到缓存；超限/读流失败/权限失效返回 null，由页面提示重试。 */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
    ): File? =
        withContext(Dispatchers.IO) {
            val input =
                try {
                    context.contentResolver.openInputStream(uri)
                } catch (_: SecurityException) {
                    null
                } catch (_: IOException) {
                    null
                } catch (_: IllegalStateException) {
                    null
                } ?: return@withContext null
            val target = newCaptureFile(context)
            var accepted = false
            try {
                accepted = copyCapped(input, target, MAX_SOURCE_BYTES) && downsampleIfNeeded(target)
            } catch (_: IOException) {
                accepted = false
            } catch (_: SecurityException) {
                accepted = false
            } catch (_: IllegalStateException) {
                accepted = false
            }
            if (accepted) {
                target
            } else {
                delete(target)
                null
            }
        }

    /** 单次拷贝循环；返回 -1 表示超限，调用方删除半截文件。 */
    private fun copyLoop(
        source: InputStream,
        sink: java.io.OutputStream,
        maxBytes: Long,
    ): Boolean {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            copied += read
            if (copied > maxBytes) return false
            sink.write(buffer, 0, read)
        }
        return true
    }

    /** 非局部返回由 inline use 支撑；超限直接判失败，不留半截文件。 */
    private fun copyCapped(
        input: InputStream,
        target: File,
        maxBytes: Long,
    ): Boolean =
        input.use { source ->
            target.outputStream().use { sink -> copyLoop(source, sink, maxBytes) }
        }

    /** 大图抽样解码后回写，避免 P18/P19 每次全尺寸解码占用峰值内存。 */
    private fun downsampleIfNeeded(target: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, bounds)
        val sample =
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) 0 else sampleSize(bounds.outWidth, bounds.outHeight)
        val decoded =
            if (sample <= 1) {
                null
            } else {
                BitmapFactory.decodeFile(
                    target.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }
        return if (decoded == null) {
            sample == 1
        } else {
            try {
                target.outputStream().use { decoded.compress(Bitmap.CompressFormat.JPEG, IMPORT_JPEG_QUALITY, it) }
            } finally {
                decoded.recycle()
            }
        }
    }
}
