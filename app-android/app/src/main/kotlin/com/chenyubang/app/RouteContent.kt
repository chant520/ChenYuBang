package com.chenyubang.app

import androidx.compose.runtime.Composable
import com.chenyubang.feature.capture.CaptureCameraScreen
import com.chenyubang.feature.capture.CaptureCropScreen
import com.chenyubang.feature.capture.CaptureOcrScreen
import com.chenyubang.feature.capture.OcrDraft
import com.chenyubang.feature.capture.OcrScreenActions
import com.chenyubang.feature.capture.P20MatchScreen

/** 按当前路由渲染目标页；P04/P05 的返回与跳转与其余路由同口径。 */
@Composable
internal fun RouteContent(
    entry: RouteEntry,
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    when (entry.route) {
        Route.Scope ->
            ScopeDestination(
                scope = deps.scope,
                availableProvinces = deps.availableProvinces,
                saver = deps.scopeSaver,
                onSaved = {
                    deps.onScopeSaved(it)
                    onRoute(router.back())
                },
                onBack = { onRoute(router.back()) },
            )
        Route.Library ->
            LibraryDestination(
                scope = deps.scope,
                database = deps.database,
                onOpenScope = { onRoute(router.push(Route.Scope)) },
                onOpenIdiom = { id -> onRoute(router.push(Route.Idiom, id)) },
            )
        Route.Search ->
            SearchDestination(deps.database, { onRoute(router.back()) }) {
                onRoute(router.push(Route.Idiom, it))
            }
        Route.Idiom ->
            IdiomDestination(
                idiomId = requireNotNull(entry.id),
                database = deps.database,
                onBack = { onRoute(router.back()) },
                onOpenMistakes = { onRoute(router.goToMistakes()) },
                onOpenExample = { id -> onRoute(router.push(Route.Example, id)) },
            )
        Route.Example ->
            ExampleDestination(requireNotNull(entry.id), deps.database, { onRoute(router.back()) }) { id ->
                onRoute(router.push(Route.Idiom, id))
            }
        else -> CaptureRouteContent(entry, router, deps, onRoute)
    }
}

/** P17–P19 五个录题步骤的落点；未拿到上一步产物时退回占位页。 */
@Composable
private fun CaptureRouteContent(
    entry: RouteEntry,
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    when (entry.route) {
        Route.Camera ->
            CaptureCameraScreen(
                onCancel = { onRoute(router.back()) },
                onImageReady = { path -> onRoute(router.attachCaptureImage(path).nextCapture()) },
            )
        Route.Crop ->
            router.captureImagePath?.let { path ->
                CaptureCropScreen(
                    imagePath = path,
                    initialCrop = router.captureCrop,
                    onRetake = { onRoute(router.back()) },
                    onNext = { selection -> onRoute(router.attachCropImage(selection.path, selection.frame)) },
                )
            } ?: RoutePlaceholder(router, deps.scope, onRoute)
        Route.Ocr ->
            router.captureCropPath?.let { path ->
                CaptureOcrScreen(
                    cropPath = path,
                    engine = deps.ocrEngine,
                    initialDraft = router.captureDraft,
                    actions =
                        OcrScreenActions(
                            onBack = { onRoute(router.back()) },
                            onRetake = { onRoute(router.retakeFromOcr()) },
                            onManual = { onRoute(router.attachOcrText(OcrDraft(stem = "", options = emptyList()))) },
                            onNext = { draft -> onRoute(router.attachOcrText(draft)) },
                        ),
                )
            } ?: RoutePlaceholder(router, deps.scope, onRoute)
        Route.Match ->
            router.captureDraft?.let { draft ->
                P20MatchScreen(
                    draft = draft,
                    database = deps.database,
                    onBack = { onRoute(router.back()) },
                    // P04 搜索内容以局部浮层复用：选中回填后关闭，不 push(Route.Search)、不写 router 栈。
                    manualPicker = { onPick, onDismiss ->
                        SearchDestination(
                            database = deps.database,
                            onClose = onDismiss,
                            onOpenIdiom = onPick,
                        )
                    },
                )
            } ?: RoutePlaceholder(router, deps.scope, onRoute)
        else -> RoutePlaceholder(router, deps.scope, onRoute)
    }
}
