package com.chenyubang.ocr

/**
 * 端侧离线 OCR 契约（开发规范 v1.6 §2.3）。
 *
 * 硬约束：
 *  - 不允许任何网络调用；ML Kit 中文需要首次下载模型，等于新增第三处联网，当前口径禁止。
 *  - 输入是 P18 导出的裁切图本地路径，输出只保留「题干 + 四个选项」的文本行。
 *  - **送识别前必须上采样**：短边 ≥1400px（代理实测同一张图原尺寸直跑会丢整行选项，2× 放大后全对，见 docs/OCR实测记录.md）。
 *  - 引擎＝PaddleOCR mobile（Paddle-Lite 2.10 + 官方 PP-OCRv3 det/rec .nb，模型随包）；模型 opt 版本须与 so 一致；真机精度复测后置（B1），不得以代理数字宣称达标。
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
    val isEmpty: Boolean get() = stem.isNullOrBlank() && options.isEmpty()
}

/**
 * 占位实现：用于没有端侧引擎的运行环境（如不加载 Paddle-Lite 的测试）。
 * 它返回空结果，页面按 P19 失败态呈现；不是「实测通过」的实现。
 */
class UnavailableOcrEngine : OcrEngine {
    override suspend fun recognize(imagePath: String): OcrResult =
        OcrResult(stem = null, options = emptyMap(), rawLines = emptyList(), durationMs = 0)
}
