package com.chenyubang.feature.capture

/** P19 对外的四个去向：返回裁切、重拍、手动补字、带着文本进修正页。 */
data class OcrScreenActions(
    val onBack: () -> Unit,
    val onRetake: () -> Unit,
    val onManual: () -> Unit,
    val onNext: (OcrDraft) -> Unit,
)
