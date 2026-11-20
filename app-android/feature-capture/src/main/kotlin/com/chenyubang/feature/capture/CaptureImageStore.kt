package com.chenyubang.feature.capture

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * P17 拍照/选图的图片落本机缓存（零联网）：P18 裁切与后续识别都读这个文件。
 * 放在 cacheDir 而非用户数据目录——拍照链路中途取消即整条丢弃，不做草稿。
 */
internal object CaptureImageStore {
    fun newCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, "capture").apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.jpg")
    }

    /** 把相册选中的照片复制到缓存；读流失败返回 null，由页面提示重试。 */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
    ): File? =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            try {
                val target = newCaptureFile(context)
                input.use { source ->
                    target.outputStream().use { sink -> source.copyTo(sink) }
                }
                target
            } catch (_: IOException) {
                null
            }
        }
}
