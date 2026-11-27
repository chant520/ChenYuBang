package com.chenyubang.feature.mistake

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CIconKind
import com.chenyubang.core.ui.CSegmentedControl
import com.chenyubang.core.ui.tokens.Tokens

/** P08 路由入口：装配三路只读查询；视图所选存在 RouterState，页面本身不持有。 */
@Composable
fun P08MistakeListRoute(
    viewMode: MistakeViewMode,
    database: AppDatabase,
    actions: P08MistakeActions,
    revision: Long = 0,
) {
    val factory =
        remember(database) {
            viewModelFactory {
                initializer {
                    P08MistakeListViewModel(
                        loadOverview = { database.mistakeQueryDao().overview() },
                        loadGroups = { database.mistakeQueryDao().groupsByIdiom() },
                        loadMistakes = { database.mistakeQueryDao().mistakesByTime() },
                    )
                }
            }
        }
    val model: P08MistakeListViewModel = viewModel(key = "p08-mistake-list-$revision", factory = factory)
    val state by model.state.collectAsState()
    P08MistakeListContent(state = state, viewMode = viewMode, actions = actions)
}

/**
 * P08 错题列表（信息架构 v1.4 第 290 行）。
 *
 * 顶部页名与「拍照录入题库」主按钮由页面自绘（self-chrome），悬浮 FAB 由 shell 统一提供；
 * 「开始复习」本包只在位置灰，复习队列属工作包 3.1。
 */
@Composable
fun P08MistakeListContent(
    state: P08MistakeListUiState,
    viewMode: MistakeViewMode,
    actions: P08MistakeActions,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
    ) {
        item { P08Header(onCapture = actions.onCapture) }
        actions.pageMessage?.let { message ->
            sectionGap()
            item { P08ReviewHint(message, actions.onDismissMessage) }
        }
        p08StateBody(state, viewMode, actions)
    }
}

/** P08 状态分发：加载/失败/空态与三种视图。 */
private fun LazyListScope.p08StateBody(
    state: P08MistakeListUiState,
    viewMode: MistakeViewMode,
    actions: P08MistakeActions,
) {
    when {
        state.loading -> {
            sectionGap()
            item { CBar(CBarKind.Info, "正在读取错题", subtitle = "只读本机题库，不联网。") }
        }
        state.failed -> {
            sectionGap()
            item { CBar(CBarKind.Warn, "错题读取失败，请重试", subtitle = "再进一次本页会重新读取。") }
        }
        state.total == 0 -> {
            sectionGap()
            item { P08Overview(total = state.total, pending = state.pending) }
            sectionGap()
            item { mistakeViewSelector(viewMode, actions) }
            sectionGap()
            item {
                CEmptyState(
                    title = "还没有错题",
                    description = "拍一张卷子上的题录进题库，标记为做错后就会归到对应成语下",
                    actionText = "拍照录入第一道题",
                    onAction = actions.onCapture,
                    // 插画位：§5.4 行 547「插画位（此处用 SVG 矢量图标）」，P08 空态帧内嵌相机图形（A8）。
                    icon = CChromeIcon.Camera,
                )
            }
        }
        else -> p08LoadedBody(state, viewMode, actions)
    }
}

/** P08 有错题时的列表与复习入口。 */
private fun LazyListScope.p08LoadedBody(
    state: P08MistakeListUiState,
    viewMode: MistakeViewMode,
    actions: P08MistakeActions,
) {
    sectionGap()
    item { P08Overview(total = state.total, pending = state.pending) }
    sectionGap()
    item { mistakeViewSelector(viewMode, actions) }
    if (state.pending == 0) {
        sectionGap()
        item {
            CBar(
                kind = CBarKind.Info,
                title = "待复习已清空",
                subtitle = "这批错题都标过已掌握，可以按成语回看。",
                actionText = "按成语看全部错题",
                onAction = { actions.onViewModeChange(MistakeViewMode.ByIdiom) },
            )
        }
    }
    sectionGap()
    when (viewMode) {
        MistakeViewMode.ByIdiom ->
            p08IdiomGroups(groups = state.groups, onOpenIdiom = actions.onOpenIdiom)
        MistakeViewMode.ByTime ->
            p08MistakeRows(items = state.mistakes, onOpenMistake = actions.onOpenMistake)
        MistakeViewMode.ByExamType ->
            p08ExamTypeGroups(items = state.mistakes, onOpenMistake = actions.onOpenMistake)
    }
    sectionGap()
    item {
        CButton(
            text = "开始复习 · ${state.pending} 道待复习",
            onClick = { actions.onStartReview(state.pending) },
            enabled = state.pending > 0,
        )
    }
}

/** 原 Column 的 spacedBy(block) 区块间距；行内分隔由各列表自己画，避免给每条错题加 16dp 空隙。 */
private fun LazyListScope.sectionGap() {
    item { Spacer(Modifier.height(Tokens.Space.block.dp)) }
}

@Composable
private fun P08ReviewHint(
    message: String,
    onDismiss: () -> Unit,
) {
    CBar(CBarKind.Info, title = message, actionText = "知道了", onAction = onDismiss)
}

@Composable
private fun mistakeViewSelector(
    viewMode: MistakeViewMode,
    actions: P08MistakeActions,
) {
    CSegmentedControl(
        items = MistakeViewMode.labels,
        selectedIndex = MistakeViewMode.entries.indexOf(viewMode),
        onSelect = { actions.onViewModeChange(MistakeViewMode.entries[it]) },
    )
}

@Composable
private fun P08Header(onCapture: () -> Unit) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "错题",
            color = c.head,
            fontSize = Tokens.FontRole.pageTitle.sp,
            fontWeight = FontWeight.W700,
            modifier = Modifier.weight(1f),
        )
        CButton(
            text = "拍照录入题库",
            onClick = onCapture,
            variant = CButtonVariant.Top,
            icon = CIconKind.Camera,
        )
    }
}
