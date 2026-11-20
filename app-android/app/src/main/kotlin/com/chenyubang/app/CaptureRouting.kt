package com.chenyubang.app

import com.chenyubang.feature.capture.CropFrame
import com.chenyubang.feature.capture.OcrDraft

fun RouterState.startCapture(): RouterState {
    if (!showFab) return this
    return append(RouteEntry(Route.Camera, instance = sequence)).copy(captureBaseDepth = stack.size)
}

fun RouterState.nextCapture(): RouterState {
    require(captureBaseDepth != null)
    val step = RouteRegistry.captureSteps.indexOf(current.route)
    require(step in 0..2)
    return append(RouteEntry(RouteRegistry.captureSteps[step + 1], instance = sequence))
}

/** P17 → P18：先记下本机图片路径再推进；P18 起各步只读这份路径。 */
fun RouterState.attachCaptureImage(path: String): RouterState {
    require(captureBaseDepth != null && current.route == Route.Camera && path.isNotBlank())
    return copy(captureImagePath = path, captureCropPath = null)
}

/** P18 → P19：记下导出的裁切图路径与框选参数；框外内容不参与后续识别。 */
fun RouterState.attachCropImage(
    path: String,
    crop: CropFrame,
): RouterState {
    require(captureBaseDepth != null && current.route == Route.Crop && path.isNotBlank())
    // 新的裁切图意味着重新识别，上一轮的文本草稿不再适用。
    return copy(captureCropPath = path, captureCrop = crop, captureDraft = null).nextCapture()
}

/** P19 → P20：把页面上的题干与选项原样带下去；P20 返回 P19 时回填（IA 第 30 条）。 */
fun RouterState.attachOcrText(draft: OcrDraft): RouterState {
    require(captureBaseDepth != null && current.route == Route.Ocr)
    return copy(captureDraft = draft).nextCapture()
}

/** 失败态「重拍这张」：丢弃这张图与草稿，回到 P17 重新取景。 */
fun RouterState.retakeFromOcr(): RouterState {
    require(captureBaseDepth != null && current.route == Route.Ocr)
    val base = stack.take(requireNotNull(captureBaseDepth))
    return withStack(base)
        .copy(captureImagePath = null, captureCropPath = null, captureCrop = null, captureDraft = null)
        .startCapture()
}

fun RouterState.captureSaved(idiomId: String): RouterState {
    require(captureBaseDepth != null && current.route == Route.Match && idiomId.isNotBlank())
    val base = stack.take(requireNotNull(captureBaseDepth))
    return withStack(base).append(RouteEntry(Route.Done, idiomId, sequence)).copy(capturedIdiomId = idiomId)
}

fun RouterState.cancelCapture(): RouterState {
    val depth = captureBaseDepth ?: return this
    return withStack(stack.take(depth))
        .copy(
            captureBaseDepth = null,
            capturedIdiomId = null,
            captureImagePath = null,
            captureCropPath = null,
            captureCrop = null,
            captureDraft = null,
        )
}

fun RouterState.captureDestination(destination: Route): RouterState {
    require(current.route == Route.Done)
    val id = requireNotNull(capturedIdiomId)
    val cleared = cancelCapture()
    return when (destination) {
        Route.Idiom -> cleared.push(Route.Idiom, id)
        Route.Mistakes -> cleared.goToMistakes()
        Route.Camera -> cleared.startCapture()
        else -> error("Unsupported capture destination")
    }
}
