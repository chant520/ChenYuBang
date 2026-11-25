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
import com.chenyubang.feature.me.P15BackupRoute
import com.chenyubang.feature.me.P16PackRoute

/** 按当前路由渲染目标页；P04/P05 的返回与跳转与其余路由同口径。 */
@Composable
internal fun RouteContent(
    entry: RouteEntry,
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
    back: () -> Unit,
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
        Route.Library -> LibraryRouteContent(entry, router, deps, onRoute)
        Route.Examples -> ExamplesRouteContent(router, deps, onRoute)
        Route.Search ->
            SearchDestination(deps.database, { onRoute(router.back()) }) {
                onRoute(router.push(Route.Idiom, it))
            }
        Route.Idiom, Route.Favorites -> IdiomRouteContent(entry, router, deps, onRoute)
        Route.Example ->
            ExampleDestination(requireNotNull(entry.id), deps.database, { onRoute(router.back()) }) { id ->
                onRoute(router.push(Route.Idiom, id))
            }
        Route.Me -> MeRouteContent(router, deps, onRoute)
        Route.Mistakes ->
            MistakesDestination(
                viewMode = router.mistakesView,
                revision = router.contentRevision,
                database = deps.database,
                actions = mistakeActions(router, deps.database, onRoute),
            )
        Route.IdiomMistakes -> IdiomMistakesRouteContent(entry, router, deps, onRoute)
        Route.Mistake -> UserExampleRouteContent(entry, router, deps, onRoute)
        Route.Queue, Route.Summary -> ReviewRouteContent(entry, router, deps, onRoute, back)
        Route.SelfTest -> SelfTestRouteContent(entry, router, deps, onRoute)
        Route.Backup ->
            P15BackupRoute(
                vm = requireNotNull(deps.backupVm),
                onPresentImportMode = { onRoute(router.present(Route.ImportMode)) },
            )
        Route.Pack -> P16PackRoute(deps.packUpdates)
        else -> CaptureRouteContent(entry, router, deps, onRoute)
    }
}

/** P05 成语详情 / P24 我的收藏：同属 Idiom 归属的二级 Push 路由接线。 */
@Composable
private fun IdiomRouteContent(
    entry: RouteEntry,
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    when (entry.route) {
        Route.Idiom ->
            IdiomDestination(
                idiomId = requireNotNull(entry.id),
                database = deps.database,
                onBack = { onRoute(router.back()) },
                onOpenMistakes = { onRoute(router.goToMistakes()) },
                onOpenExample = { id -> onRoute(router.push(Route.Example, id)) },
                onReviewIdiom = { count -> onRoute(router.startReview(count)) },
                revision = router.contentRevision,
            )
        Route.Favorites ->
            FavoriteDestination(
                database = deps.database,
                revision = router.contentRevision,
                onBack = { onRoute(router.back()) },
                onOpenIdiom = { id -> onRoute(router.push(Route.Idiom, id)) },
                onGoLibrary = { onRoute(router.selectTab(AppTab.Idiom).push(Route.Library)) },
            )
        else -> Unit
    }
}

/** P09 成语下录入题：行点击进入 P10（user_example.id）。 */
@Composable
private fun IdiomMistakesRouteContent(
    entry: RouteEntry,
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    IdiomMistakesDestination(
        idiomId = requireNotNull(entry.id),
        database = deps.database,
        onBack = { onRoute(router.back()) },
        onOpenIdiom = { id -> onRoute(router.push(Route.Idiom, id)) },
        onOpenExample = { id -> onRoute(router.push(Route.Mistake, id)) },
        revision = router.contentRevision,
    )
}

/** P10 录入题详情：路由键 mistake/{id} 的值取 user_example.id。 */
@Composable
private fun UserExampleRouteContent(
    entry: RouteEntry,
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    UserExampleDestination(
        exampleId = requireNotNull(entry.id),
        database = deps.database,
        onBack = { onRoute(router.back()) },
        onOpenIdiom = { id -> onRoute(router.push(Route.Idiom, id)) },
        onDataChanged = { onRoute(router.bumpContentRevision()) },
    )
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
        else -> RoutePlaceholder(router, deps.scope, deps.database, onRoute)
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
    } ?: RoutePlaceholder(router, deps.scope, deps.database, onRoute)
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
    } ?: RoutePlaceholder(router, deps.scope, deps.database, onRoute)
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
            instance = router.current.instance,
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
    } ?: RoutePlaceholder(router, deps.scope, deps.database, onRoute)
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
