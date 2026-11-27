package com.chenyubang.feature.example

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.ExampleListItem
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CList
import com.chenyubang.core.ui.CListRow
import com.chenyubang.core.ui.CSkeletonBlock
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.tokens.Tokens

private const val SKELETON_ROWS = 3

data class P06ExamplesActions(
    val onOpenPack: () -> Unit,
    val onOpenScope: () -> Unit,
    val onOpenExample: (String) -> Unit,
)

/** P06 只在例题根路由装配；详情、隐藏与后台都不会调用本入口。 */
@Composable
fun P06ExamplesRoute(
    database: AppDatabase,
    entryEpoch: Int,
    refreshTrigger: ExampleRefreshTrigger,
    actions: P06ExamplesActions,
) {
    val factory =
        remember(database, refreshTrigger) {
            viewModelFactory {
                initializer {
                    P06ExamplesViewModel(
                        { database.exampleDao().listInScope() },
                        refreshTrigger,
                    )
                }
            }
        }
    val model: P06ExamplesViewModel = viewModel(key = "p06-examples-$entryEpoch", factory = factory)
    val state by model.state.collectAsState()
    LaunchedEffect(entryEpoch) { model.enter() }
    P06ExamplesContent(
        state = state,
        onRetry = model::retry,
        onOpenPack = actions.onOpenPack,
        onOpenScope = actions.onOpenScope,
        onOpenExample = actions.onOpenExample,
    )
}

@Composable
fun P06ExamplesContent(
    state: P06ExamplesUiState,
    onRetry: () -> Unit = {},
    onOpenPack: () -> Unit = {},
    onOpenScope: () -> Unit = {},
    onOpenExample: (String) -> Unit = {},
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        Text("考试类型 / 年份 / 题型 / 是否做过", color = colors.text3, fontSize = Tokens.FontRole.caption.sp)
        if (state.updateFailed) {
            CBar(
                kind = CBarKind.Warn,
                title = "题库更新失败，\n当前题库仍可离线浏览",
                subtitle = "请到题库版本页手动重试。",
                actionText = "去题库版本",
                onAction = onOpenPack,
            )
        }
        when {
            state.failed ->
                CBar(
                    kind = CBarKind.Warn,
                    title = "例题读取失败，请重试",
                    subtitle = "重试只重新读取本机数据。",
                    actionText = "重试",
                    onAction = onRetry,
                )
            state.loading ->
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp)) {
                    repeat(SKELETON_ROWS) { CSkeletonBlock(Tokens.Space.hitMin.dp, Modifier) }
                }
            state.items.isEmpty() ->
                CEmptyState(
                    title = "当前范围还没有例题",
                    description = "可去考试范围设置调整范围。",
                    actionText = "去考试范围设置",
                    onAction = onOpenScope,
                )
            else -> ExampleList(state.items, onOpenExample)
        }
        Text("共 ${state.items.size} 条", color = colors.text3, fontSize = Tokens.FontRole.caption.sp)
        Text("只获取题库内容，不改动本机学习记录。", color = colors.text3, fontSize = Tokens.FontRole.caption.sp)
        Spacer(Modifier.size(Tokens.Space.fabBottom.dp))
    }
}

@Composable
private fun ExampleList(
    items: List<ExampleListItem>,
    onOpenExample: (String) -> Unit,
) {
    // 题库随安装包内置、条目数固定（content/examples/examples.json 当前 11 条），
    // 不随用户录入增长；一次组合全量有明确上界，故保留 CList，不引入 LazyColumn。
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp)) {
        items.forEach { item ->
            val example = item.example
            CList {
                CListRow(
                    name = "${example.year} · ${example.examType}",
                    pinyin = example.region.ifBlank { example.questionType },
                    sub = example.stem,
                    trailing = "${item.idiomCount} 个成语",
                    meta = { CTag(example.questionType) },
                    showDivider = false,
                    onClick = { onOpenExample(example.id) },
                )
            }
        }
    }
}
