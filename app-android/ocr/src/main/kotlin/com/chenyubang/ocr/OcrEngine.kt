package com.chenyubang.ocr

/**
 * 端侧离线 OCR 契约（规范 §2.3）。
 *
 * 硬约束：
 *  - 不允许任何网络调用；ML Kit 中文需要首次下载模型，等于新增第三处联网，当前口径禁止。
 *  - 输入是裁切后的本地图片路径，输出只保留「题干 + 四个选项」的文本行。
 *  - **送识别前必须上采样**：短边 ≥1400px（实测同一张图原尺寸直跑会丢整行选项，2× 放大后全对，见 docs/OCR实测记录.md）。
 *  - 引擎选择（PaddleOCR mobile / Tesseract）要等样张实测通过率后再定，接口先固定。
 */
interface OcrEngine {
    suspend fun recognize(imagePath: String): OcrResult
}

data class OcrResult(
    val stem: String?,
    val options: Map<String, String>,
    val rawLines: List<String>,
    val durationMs: Long,
) {
    val completedOptionCount: Int get() = options.size
}

/**
 * 占位实现：真机未接入引擎时返回空结果，供 P19 失败态（E20）联调用。
 * 它不是「实测通过」的实现——通过率必须用 20 张真实卷面照片量出来。
 */
class UnavailableOcrEngine : OcrEngine {
    override suspend fun recognize(imagePath: String): OcrResult =
        OcrResult(stem = null, options = emptyMap(), rawLines = emptyList(), durationMs = 0)
}
