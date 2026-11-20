package com.chenyubang.app

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
    return copy(captureImagePath = path)
}

fun RouterState.captureSaved(idiomId: String): RouterState {
    require(captureBaseDepth != null && current.route == Route.Match && idiomId.isNotBlank())
    val base = stack.take(requireNotNull(captureBaseDepth))
    return withStack(base).append(RouteEntry(Route.Done, idiomId, sequence)).copy(capturedIdiomId = idiomId)
}

fun RouterState.cancelCapture(): RouterState {
    val depth = captureBaseDepth ?: return this
    return withStack(stack.take(depth))
        .copy(captureBaseDepth = null, capturedIdiomId = null, captureImagePath = null)
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
