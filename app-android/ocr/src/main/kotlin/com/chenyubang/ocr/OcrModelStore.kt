package com.chenyubang.ocr

import android.content.Context
import java.io.File

/**
 * 模型随包分发：首次使用解压到 filesDir；目录名带模型版本，升级换目录后旧文件自然失效，
 * 不需要额外版本标记。
 */
internal class OcrModelStore(context: Context) {
    private val appContext = context.applicationContext

    data class ModelFiles(
        val detector: File,
        val recognizer: File,
        val dictionary: File,
    )

    fun files(): ModelFiles {
        val dir = File(appContext.filesDir, MODEL_DIR)
        dir.mkdirs()
        return ModelFiles(
            detector = copy("ocr/ppocrv3_det.nb", File(dir, "det.nb")),
            recognizer = copy("ocr/ppocrv3_rec.nb", File(dir, "rec.nb")),
            dictionary = copy("ocr/ppocr_keys_v1.txt", File(dir, "keys.txt")),
        )
    }

    private fun copy(
        assetPath: String,
        target: File,
    ): File {
        if (!target.exists() || target.length() == 0L) {
            appContext.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    private companion object {
        // 换模型版本时改 THIS 标识即可让旧目录整体失效，避免升级后沿用旧模型。
        const val MODEL_DIR = "ocr/ppocrv3-v2.10"
    }
}
