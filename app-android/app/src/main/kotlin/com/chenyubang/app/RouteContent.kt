package com.chenyubang.app

import androidx.compose.runtime.Composable
import com.chenyubang.feature.capture.CaptureCameraScreen
import com.chenyubang.feature.capture.CaptureCropScreen
import com.chenyubang.feature.capture.CaptureOcrScreen
import com.chenyubang.feature.capture.OcrDraft
import com.chenyubang.feature.capture.OcrScreenActions
import com.chenyubang.feature.capture.P20FormDeps
import com.chenyubang.feature.capture.P20FormScreen
import com.chenyubang.feature.capture.P21DoneScreen

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

/** P17–P21 录题步骤的分发；未拿到上一步产物时退回占位页。 */
@Composable
private fun CaptureRouteContent(
    entry: RouteEntry,
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    when (entry.route) {
        Route.Camera -> CameraRoute(router, onRoute)
        Route.Crop -> CropRoute(router, deps, onRoute)
        Route.Ocr -> OcrRoute(router, deps, onRoute)
        Route.Match -> MatchRoute(router, deps, onRoute)
        Route.Done -> DoneRoute(router, onRoute)
        else -> RoutePlaceholder(router, deps.scope, onRoute)
    }
}

@Composable
private fun CameraRoute(
    router: RouterState,
    onRoute: (RouterState) -> Unit,
) {
    CaptureCameraScreen(
        onCancel = { onRoute(router.back()) },
        onImageReady = { path -> onRoute(router.attachCaptureImage(path).nextCapture()) },
    )
}

@Composable
private fun CropRoute(
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    router.captureImagePath?.let { path ->
        CaptureCropScreen(
            imagePath = path,
            initialCrop = router.captureCrop,
            onRetake = { onRoute(router.back()) },
            onNext = { selection -> onRoute(router.attachCropImage(selection.path, selection.frame)) },
        )
    } ?: RoutePlaceholder(router, deps.scope, onRoute)
}

@Composable
private fun OcrRoute(
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
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
}

@Composable
private fun MatchRoute(
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    router.captureDraft?.let { draft ->
        P20FormScreen(
            draft = draft,
            deps =
                P20FormDeps(
                    database = deps.database,
                    saver = deps.userExampleSaver,
                    imagePath = router.captureImagePath.orEmpty(),
                    // P04 搜索内容以局部浮层复用：选中回填后关闭，不 push(Route.Search)、不写 router 栈。
                    manualPicker = { onPick, onDismiss ->
                        SearchDestination(
                            database = deps.database,
                            onClose = onDismiss,
                            onOpenIdiom = onPick,
                        )
                    },
                ),
            onBack = { onRoute(router.back()) },
            onSaved = { userExampleId, idioms -> onRoute(router.captureSaved(userExampleId, idioms)) },
        )
    } ?: RoutePlaceholder(router, deps.scope, onRoute)
}

@Composable
private fun DoneRoute(
    router: RouterState,
    onRoute: (RouterState) -> Unit,
) {
    P21DoneScreen(
        idioms = router.capturedIdioms,
        onOpenIdiom = { onRoute(router.captureDestination(Route.Idiom)) },
        onOpenMistakes = { onRoute(router.captureDestination(Route.Mistakes)) },
        onContinue = { onRoute(router.captureDestination(Route.Camera)) },
    )
}
