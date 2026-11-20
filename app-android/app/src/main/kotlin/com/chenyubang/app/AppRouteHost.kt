package com.chenyubang.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.data.ExamScopeStore
import com.chenyubang.core.data.LaunchPreferences
import com.chenyubang.core.data.PackImporter
import com.chenyubang.core.data.UserExampleSaver
import com.chenyubang.core.data.ValidatedPack
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CFab
import com.chenyubang.core.ui.CInput
import com.chenyubang.core.ui.COverlay
import com.chenyubang.core.ui.tokens.Tokens
import com.chenyubang.feature.me.ExamScopeSaver
import com.chenyubang.ocr.PaddleOcrEngine
import kotlinx.coroutines.CancellationException
import java.io.File

/** app只编排路由、生命周期和Data注入；目的地不产生题目/统计/成功结果。 */
@Composable
fun AppRouteHost(database: AppDatabase) {
    val context = LocalContext.current.applicationContext
    val scopeStore = remember(database) { ExamScopeStore(database) }
    val importer = remember(database) { PackImporter(database, context) }
    val ocrEngine = remember { PaddleOcrEngine(context) }
    val userExampleSaver =
        remember(database, context) { UserExampleSaver(database.userExampleDao(), File(context.filesDir, "images")) }
    var currentPack by remember { mutableStateOf<ValidatedPack?>(null) }
    var preferences by remember { mutableStateOf<LaunchPreferences?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var availableProvinces by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(database, loadAttempt) {
        loadError = false
        try {
            val launch = loadInitialLaunch(context, importer, scopeStore)
            currentPack = launch.pack
            availableProvinces = launch.provinces
            preferences = launch.preferences
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadError = true
        }
    }
    val loaded = preferences
    if (loaded == null) {
        InitializingScreen(loadError) { loadAttempt++ }
        return
    }
    val pack = requireNotNull(currentPack)
    val saver =
        ExamScopeSaver { scope, complete ->
            scopeStore.save(scope, complete = complete)
            importer.refreshScopeLinks(pack)
        }
    if (!loaded.complete) {
        FirstLaunchGate(loaded.scope, availableProvinces, saver) {
            preferences = LaunchPreferences(true, it)
        }
        return
    }
    RouterShell(
        RouteDeps(
            scope = loaded.scope,
            database = database,
            availableProvinces = availableProvinces,
            scopeSaver = saver,
            onScopeSaved = { preferences = loaded.copy(scope = it) },
            ocrEngine = ocrEngine,
            userExampleSaver = userExampleSaver,
        ),
    )
}

@Composable
private fun RouterShell(deps: RouteDeps) {
    var router by rememberSaveable { mutableStateOf(RouterState()) }
    var confirmExit by remember { mutableStateOf(false) }
    val holder = rememberSaveableStateHolder()
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val back = { if (router.current.route == Route.Queue) confirmExit = true else router = router.back() }
    BackHandler(enabled = systemBackEnabled(router), onBack = back)
    val onSearch: (() -> Unit)? =
        if (router.current.route == Route.Library) ({ router = router.push(Route.Search) }) else null
    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize()) {
            RouteHeader(router = router, back = back, onSearch = onSearch)
            Box(Modifier.weight(1f)) {
                val entry = router.current
                holder.SaveableStateProvider("${router.tab.name}:${entry.instance}") {
                    RouteContent(entry, router, deps) { router = it }
                }
            }
            if (router.showTabs) {
                AppTabs(router.tab.ordinal) { router = router.selectTab(AppTab.entries[it]) }
            }
        }
        if (router.showFab) CaptureShortcut { router = router.startCapture() }
        RouterModal(router, { router = router.dismissModal() })
        if (confirmExit) {
            ExitReviewOverlay(
                onDismiss = { confirmExit = false },
                onConfirm = {
                    confirmExit = false
                    router = router.back()
                },
            )
        }
    }
}

@Composable
private fun BoxScope.CaptureShortcut(onClick: () -> Unit) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Box(
        Modifier.align(Alignment.BottomEnd)
            .padding(end = Tokens.Space.fabRight.dp, bottom = Tokens.Space.fabBottom.dp)
            .semantics { contentDescription = "拍照录题" },
        contentAlignment = Alignment.Center,
    ) {
        CFab(onClick = onClick)
        CChromeGlyph(
            CChromeIcon.Camera,
            colors.fabicon,
            Modifier.size(Tokens.Icon.n24.dp).background(colors.fab),
        )
    }
}

@Composable
internal fun RoutePlaceholder(
    router: RouterState,
    scope: ExamScopeSelection,
    update: (RouterState) -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val scroll = rememberScrollState()
    val epoch = router.rootTopEpoch.getValue(router.tab)
    var consumedEpoch by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(epoch) {
        if (router.current.route.presentation == Presentation.Root && epoch > consumedEpoch) {
            scroll.scrollTo(0)
            consumedEpoch = epoch
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll)
            .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        Text("页面待接入", color = colors.text2, fontSize = Tokens.Font.n13.sp)
        router.current.id?.let { Text(it, color = colors.text3, fontSize = Tokens.Font.n11.sp) }
        val step = RouteRegistry.captureSteps.indexOf(router.current.route)
        if (step >= 0) {
            Text("第 ${step + 1} 步 / 共 5 步", color = colors.text3, fontSize = Tokens.Font.n11.sp)
            CButton("取消录题", { update(router.cancelCapture()) }, variant = CButtonVariant.Ghost)
            CButton("下一步（拍照业务待接入）", {}, enabled = false)
        }
        PlaceholderLinks(router, scope, update)
        Spacer(Modifier.padding(bottom = Tokens.Space.fabBottom.dp))
    }
}

@Composable
private fun PlaceholderLinks(
    router: RouterState,
    scope: ExamScopeSelection,
    update: (RouterState) -> Unit,
) {
    when (router.current.route) {
        Route.Recommend -> {
            RouteScopeLine(scope)
            CButton("考试范围", { update(router.push(Route.Scope)) }, variant = CButtonVariant.Ghost)
            CButton("进入成语库", { update(router.push(Route.Library)) })
            CButton("搜索", { update(router.push(Route.Search)) }, variant = CButtonVariant.Ghost)
            CButton("去错题本", { update(router.goToMistakes()) }, variant = CButtonVariant.Ghost)
        }
        Route.Search -> SearchPlaceholder()
        Route.Examples -> CButton("考试范围", { update(router.push(Route.Scope)) }, variant = CButtonVariant.Ghost)
        Route.Mistakes -> {
            PlaceholderFilters(listOf("按成语", "按时间", "按考试类型"))
            CButton("拍照录题", { update(router.startCapture()) })
            CButton("开始复习（队列待接入）", {}, enabled = false)
        }
        Route.Idiom -> {
            CButton("复习这个成语（队列待接入）", {}, enabled = false)
            CButton("去错题本", { update(router.goToMistakes()) }, variant = CButtonVariant.Ghost)
        }
        Route.Me -> {
            RouteScopeLine(scope)
            CButton("考试范围设置", { update(router.push(Route.Scope)) })
            CButton("备份与恢复", { update(router.push(Route.Backup)) })
            CButton("题库版本", { update(router.push(Route.Pack)) })
            CButton("清空数据", { update(router.present(Route.Clear)) }, variant = CButtonVariant.Ghost)
        }
        Route.Backup ->
            CButton(
                "查看导入方式（仅外壳）",
                { update(router.present(Route.ImportMode)) },
                variant = CButtonVariant.Ghost,
            )
        Route.Queue, Route.Summary -> ReviewPlaceholder(router, update)
        else -> Unit
    }
}

@Composable
private fun ReviewPlaceholder(
    router: RouterState,
    update: (RouterState) -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Text(
        router.review?.idiomId?.let { "只看『$it』" } ?: "全部待复习",
        color = colors.text2,
        fontSize = Tokens.Font.n12.sp,
    )
    if (router.current.route == Route.Summary) {
        CButton(if (router.review?.idiomId == null) "回错题本" else "回成语详情", { update(router.exitReview()) })
        CButton("再复习一轮（队列待接入）", {}, enabled = false)
    }
}

@Composable
private fun SearchPlaceholder() {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    CInput(query, { query = it }, Modifier.focusRequester(focus), placeholder = "完整成语 / 单字 / 全拼 / 首字母")
    CButton("清空", { query = "" }, variant = CButtonVariant.Ghost)
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun PlaceholderFilters(labels: List<String>) {
    var selection by rememberSaveable { mutableStateOf(0) }
    com.chenyubang.core.ui.CSegmentedControl(items = labels, selectedIndex = selection, onSelect = { selection = it })
}

@Composable
private fun RouteScopeLine(scope: ExamScopeSelection) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Text(scope.description, color = colors.text2, fontSize = Tokens.Font.n13.sp)
    if (scope.provincial) {
        Text("省份用于筛选省考内容，可自行增减", color = colors.text3, fontSize = Tokens.Font.n12.sp)
    }
    if (scope.isEmpty) Text("不选范围时成语库和例题库会是空的，可以稍后在『我的』里改", color = colors.text3, fontSize = Tokens.Font.n12.sp)
}

@Composable
private fun RouterModal(
    router: RouterState,
    cancel: () -> Unit,
) {
    val modal = router.modal ?: return
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    COverlay(modal.title, modal == Route.Clear, cancel) {
        if (modal == Route.Clear) {
            Text("将清空错题、收藏、学习状态；成语库与例题库不受影响。", color = colors.text2, fontSize = Tokens.Font.n12.sp)
            CButton("确认清空（数据操作待接入）", {}, variant = CButtonVariant.DangerFilled, enabled = false)
        } else {
            com.chenyubang.core.ui.CBadge("覆盖", com.chenyubang.core.ui.CBadgeKind.Error)
            Text("覆盖会替换本机数据；合并保留本机记录。恢复待接入，不会执行导入。", color = colors.text2, fontSize = Tokens.Font.n12.sp)
            com.chenyubang.core.ui.CTag("合并")
            CButton("覆盖 / 合并（恢复待接入）", {}, enabled = false)
        }
        CButton("取消", cancel, variant = CButtonVariant.Ghost)
    }
}
