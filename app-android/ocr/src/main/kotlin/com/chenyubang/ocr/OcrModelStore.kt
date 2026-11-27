package com.chenyubang.ocr

import android.content.Context
import java.io.File
import java.io.IOException

/** 模型版本：.nb 文件头偏移 2 处写着 v2.10，目录名由此派生，换模型只改这里。 */
internal const val OCR_MODEL_VERSION = "v2.10"

/**
 * 模型随包分发：首次使用解压到 filesDir。先写 .tmp 再原子 rename；
 * 字节数与版本头不符即删掉重解压，半截文件不会进入 native 加载路径。
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
        if (!dir.exists() && !dir.mkdirs()) throw IOException("cannot create model dir: $dir")
        return ModelFiles(
            detector = ensure("ocr/ppocrv3_det.nb", File(dir, "det.nb"), DET_BYTES, versioned = true),
            recognizer = ensure("ocr/ppocrv3_rec.nb", File(dir, "rec.nb"), REC_BYTES, versioned = true),
            dictionary = ensure("ocr/ppocr_keys_v1.txt", File(dir, "keys.txt"), KEYS_BYTES, versioned = false),
        )
    }

    private fun ensure(
        assetPath: String,
        target: File,
        expectedBytes: Long,
        versioned: Boolean,
    ): File {
        if (OcrModelValidator.isValid(target, expectedBytes, versioned)) return target
        val tmp = File(target.path + TMP_SUFFIX)
        var failure: IOException? = null
        repeat(EXTRACT_ATTEMPTS) {
            failure =
                try {
                    extractAtomically(assetPath, tmp, expectedBytes, versioned, target)
                    null
                } catch (problem: IOException) {
                    tmp.delete()
                    problem
                }
            if (failure == null) return target
        }
        throw failure ?: IOException("extract failed: $assetPath")
    }

    /** 成功即把验证过的 tmp 原子 rename 成目标；任一环节失败抛同一类异常由 ensure 收拾 tmp。 */
    private fun extractAtomically(
        assetPath: String,
        tmp: File,
        expectedBytes: Long,
        versioned: Boolean,
        target: File,
    ) {
        val cleared = !tmp.exists() || tmp.delete()
        val copied =
            cleared &&
                runCatching {
                    appContext.assets.open(assetPath).use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output, COPY_BUFFER) }
                    }
                }.isSuccess
        val valid = copied && OcrModelValidator.isValid(tmp, expectedBytes, versioned)
        // 同目录 rename 为原子替换；失败绝不把半截文件放到目标路径。
        if (!valid || !tmp.renameTo(target)) throw IOException("extract failed: $assetPath")
    }

    private companion object {
        const val MODEL_DIR = "ocr/ppocrv3-$OCR_MODEL_VERSION"
        const val DET_BYTES = 1_092_190L
        const val REC_BYTES = 10_786_246L
        const val KEYS_BYTES = 32_873L
        const val EXTRACT_ATTEMPTS = 2
        const val COPY_BUFFER = 64 * 1024
        const val TMP_SUFFIX = ".tmp"
    }
}

/** 纯文件校验，便于 JVM 单测；版本头位于 .nb 偏移 2，字典只校验字节数。 */
internal object OcrModelValidator {
    private const val VERSION_OFFSET = 2
    private val versionMagic = OCR_MODEL_VERSION.toByteArray(Charsets.US_ASCII)

    fun isValid(
        file: File,
        expectedBytes: Long,
        versioned: Boolean,
    ): Boolean {
        if (!file.exists() || file.length() != expectedBytes) return false
        return !versioned || hasVersionMagic(file)
    }

    private fun hasVersionMagic(file: File): Boolean {
        val header = ByteArray(VERSION_OFFSET + versionMagic.size)
        file.inputStream().use { stream ->
            var offset = 0
            while (offset < header.size) {
                val read = stream.read(header, offset, header.size - offset)
                if (read < 0) return false
                offset += read
            }
        }
        return versionMagic.indices.all { header[VERSION_OFFSET + it] == versionMagic[it] }
    }
}
