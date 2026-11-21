package com.chenyubang.feature.mistake

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val model: P08MistakeListViewModel = viewModel(key = "p08-mistake-list", factory = factory)
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
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        P08Header(onCapture = actions.onCapture)
        when {
            state.loading -> CBar(CBarKind.Info, "正在读取错题", subtitle = "只读本机题库，不联网。")
            state.failed -> CBar(CBarKind.Warn, "错题读取失败，请重试", subtitle = "再进一次本页会重新读取。")
            // 空态（IA §5.4 行 547）：②总览行与 ③视图切换仍是常驻区块，⑥空态区在 ③之后、不含 ④「开始复习」。
            state.total == 0 -> {
                P08Overview(total = state.total, pending = state.pending)
                CSegmentedControl(
                    items = MistakeViewMode.labels,
                    selectedIndex = MistakeViewMode.entries.indexOf(viewMode),
                    onSelect = { idx -> actions.onViewModeChange(MistakeViewMode.entries[idx]) },
                )
                CEmptyState(
                    title = "还没有错题",
                    description = "拍一张卷子上的题录进题库，标记为做错后就会归到对应成语下",
                    actionText = "拍照录入第一道题",
                    onAction = actions.onCapture,
                    // 插画位：§5.4 行 547「插画位（此处用 SVG 矢量图标）」，P08 空态帧内嵌相机图形（A8）。
                    icon = CChromeIcon.Camera,
                )
            }
            else -> {
                P08Overview(total = state.total, pending = state.pending)
                CSegmentedControl(
                    items = MistakeViewMode.labels,
                    selectedIndex = MistakeViewMode.entries.indexOf(viewMode),
                    onSelect = { actions.onViewModeChange(MistakeViewMode.entries[it]) },
                )
                if (state.pending == 0) {
                    CBar(
                        kind = CBarKind.Info,
                        title = "待复习已清空",
                        subtitle = "这批错题都标过已掌握，可以按成语回看。",
                        actionText = "按成语看全部错题",
                        onAction = { actions.onViewModeChange(MistakeViewMode.ByIdiom) },
                    )
                }
                when (viewMode) {
                    MistakeViewMode.ByIdiom ->
                        P08IdiomGroups(groups = state.groups, onOpenIdiom = actions.onOpenIdiom)
                    MistakeViewMode.ByTime ->
                        P08MistakeRows(items = state.mistakes, onOpenMistake = actions.onOpenMistake)
                    MistakeViewMode.ByExamType ->
                        P08ExamTypeGroups(items = state.mistakes, onOpenMistake = actions.onOpenMistake)
                }
                // TODO(3.1): 「开始复习」接 P11 复习队列；本包只保证按钮在位、置灰、文案不改。
                CButton(text = "开始复习", onClick = {}, enabled = false)
            }
        }
    }
}

@Composable
private fun P08Header(onCapture: () -> Unit) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "错题",
            color = c.head,
            fontSize = Tokens.Font.n20.sp,
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
