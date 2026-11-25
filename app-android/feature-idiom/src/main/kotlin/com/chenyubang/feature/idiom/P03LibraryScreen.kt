package com.chenyubang.feature.idiom

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.data.IdiomLearningState
import com.chenyubang.core.data.IdiomLearningStatus
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CLetterIndex
import com.chenyubang.core.ui.CLink
import com.chenyubang.core.ui.CList
import com.chenyubang.core.ui.CListRow
import com.chenyubang.core.ui.CSegmentedControl
import com.chenyubang.core.ui.CSkeletonBlock
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.tokens.Tokens

/** 骨架屏行数：与首屏一屏内可见的列表行数大致对齐，避免加载态跳动。 */
private const val SKELETON_ROWS = 3

data class P03LibraryEntry(
    val instance: Long,
    val epoch: Int,
)

data class P03LibraryActions(
    val onOpenScope: () -> Unit,
    val onOpenPack: () -> Unit,
    val onOpenIdiom: (String) -> Unit,
)

/** P03 路由入口：真实进入先读本机库，再检查共享题库更新；范围变化只重读本机。 */
@Composable
fun P03LibraryRoute(
    scope: ExamScopeSelection,
    database: AppDatabase,
    entry: P03LibraryEntry,
    refreshTrigger: LibraryRefreshTrigger,
    actions: P03LibraryActions,
) {
    val factory =
        remember(database, refreshTrigger) {
            viewModelFactory {
                initializer {
                    P03LibraryViewModel(
                        loadIdioms = {
                            database.idiomDao().libraryIdioms().map { row ->
                                LibraryIdiom(row.id, row.word, row.pinyin, row.initials, row.frequency)
                            }
                        },
                        refreshTrigger = refreshTrigger,
                        loadStates = { ids ->
                            database.idiomStateDao().statesForIds(ids).associate { row ->
                                row.idiomId to
                                    IdiomLearningState(
                                        idiomId = row.idiomId,
                                        status = IdiomLearningStatus.fromStored(row.status),
                                        favorite = row.favorite,
                                    )
                            }
                        },
                    )
                }
            }
        }
    val library: P03LibraryViewModel = viewModel(key = "p03-library-${entry.instance}", factory = factory)
    val state by library.state.collectAsState()
    LaunchedEffect(entry.instance, entry.epoch) { library.enter(scope, entry.epoch) }
    LaunchedEffect(scope) { library.reload(scope) }
    P03LibraryContent(
        state = state,
        onDimension = library::selectDimension,
        onLetter = library::selectLetter,
        onClearConditions = library::clearConditions,
        onOpenScope = actions.onOpenScope,
        onRetry = library::retry,
        onOpenPack = actions.onOpenPack,
        onOpenIdiom = actions.onOpenIdiom,
    )
}

/**
 * P03 成语库页主体（信息架构 v1.2 P03 ②–⑦）。
 *
 * 只做拼装：范围卡 + 维度切换 + 列表 + 字母索引 + 空态；颜色 / 字号 / 间距全部取令牌。
 */
@Composable
fun P03LibraryContent(
    state: P03LibraryUiState,
    onDimension: (LibraryDimension) -> Unit = {},
    onLetter: (String) -> Unit = {},
    onClearConditions: () -> Unit = {},
    onOpenScope: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenPack: () -> Unit = {},
    onOpenIdiom: (String) -> Unit = {},
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(start = Tokens.Space.pageX.dp, top = Tokens.Space.contentTop.dp),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
        ) {
            RangeCard(state, onOpenScope)
            if (state.updateFailed) {
                LibraryUpdateFailureBar(onOpenPack)
            }
            DimensionSwitch(state, onDimension)
            when {
                state.failed ->
                    CBar(
                        kind = CBarKind.Warn,
                        title = "成语库读取失败，请重试",
                        subtitle = "内置题库仍可继续浏览；重试只重新读取本机数据。",
                        actionText = "重试",
                        onAction = onRetry,
                    )
                state.loading -> LibrarySkeleton()
                state.page.isEmpty ->
                    LibraryEmpty(
                        state = state,
                        onClearConditions = onClearConditions,
                        onOpenScope = onOpenScope,
                    )
                else -> LibraryList(state, onOpenIdiom)
            }
            Text(
                text = "共 ${state.page.count} 条 · 已到末尾",
                color = colors.text3,
                fontSize = Tokens.Font.n11.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Tokens.Space.ghostY.dp, bottom = Tokens.Space.block.dp),
            )
        }
        Box(
            Modifier.fillMaxHeight().padding(end = Tokens.Space.lsubTop.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.letters.isNotEmpty()) {
                CLetterIndex(
                    letters = state.letters,
                    selected = state.selectedLetter,
                    onSelect = onLetter,
                )
            }
        }
    }
}

@Composable
private fun LibraryUpdateFailureBar(onOpenPack: () -> Unit) {
    CBar(
        kind = CBarKind.Warn,
        title = "题库更新失败，当前题库仍可离线浏览",
        subtitle = "请到题库版本页手动重试。",
        actionText = "去题库版本",
        onAction = onOpenPack,
    )
}

@Composable
private fun RangeCard(
    state: P03LibraryUiState,
    onOpenScope: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CCard {
        // 方案 A 同排（用户 2026-11-19 拍板；草图 P03范围卡同排方案-v0.1.html 已于 2026-11-20 清理）：范围文字与「调整省份」链接同一行；
        // 空间不足时范围文字折行、链接保持右对齐单行不换行。
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
        ) {
            Text(
                text = rangeText(state, colors.text),
                color = colors.text2,
                fontSize = Tokens.Font.n13.sp,
                modifier = Modifier.weight(1f),
            )
            CLink(text = "调整省份", onClick = onOpenScope)
        }
        // 省份仅为筛选维度：中性说明行，无橙字、无警示（信息架构 v1.4 P03 ②）。
        if (state.scope.provincial && !state.scopeEmpty) {
            Text(
                text = "省份用于筛选省考内容，可自行增减",
                color = colors.text3,
                fontSize = Tokens.Font.n12.sp,
            )
        }
        if (state.scopeEmpty) {
            Text(
                text = "不选范围时成语库和例题库会是空的，可以稍后在『我的』里改。",
                color = colors.text3,
                fontSize = Tokens.Font.n12.sp,
                modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
            )
        }
    }
}

/** 范围行：`范围：国考 · 省考 · 事业编`，范围三段加重（省份仅为筛选维度，无警示片段）。 */
private fun rangeText(
    state: P03LibraryUiState,
    strongColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        append("范围：")
        withStyle(SpanStyle(color = strongColor, fontWeight = FontWeight.W600)) {
            append(state.scope.description)
        }
    }

@Composable
private fun DimensionSwitch(
    state: P03LibraryUiState,
    onDimension: (LibraryDimension) -> Unit,
) {
    CSegmentedControl(
        items = LibraryDimension.entries.map { it.label },
        selectedIndex = state.dimension.ordinal,
        onSelect = { onDimension(LibraryDimension.entries[it]) },
    )
}

@Composable
private fun LibrarySkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp)) {
        repeat(SKELETON_ROWS) {
            CSkeletonBlock(
                height = Tokens.Space.hitMin.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LibraryList(
    state: P03LibraryUiState,
    onOpenIdiom: (String) -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp)) {
        state.page.sections.forEach { section ->
            Column {
                if (section.letter != null) {
                    Text(
                        text = section.letter,
                        color = colors.text3,
                        fontSize = Tokens.Font.n10.sp,
                        modifier =
                            Modifier.padding(
                                top = Tokens.Space.lmetaTop.dp,
                                bottom = Tokens.Space.lsubTop.dp,
                            ),
                    )
                }
                CList {
                    section.items.forEachIndexed { index, idiom ->
                        CListRow(
                            name = idiom.word,
                            pinyin = idiom.pinyin,
                            showDivider = index != section.items.lastIndex,
                            meta = {
                                CTag("考频 ${idiom.frequency}")
                                // A6 学习状态位：按 idiom_state 三态展示，缺行补未学；只读不切换。
                                val tag = statusTag(state.statusOf(idiom.id).status)
                                CTag(tag.text, style = tag.style)
                            },
                            onClick = { onOpenIdiom(idiom.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmpty(
    state: P03LibraryUiState,
    onClearConditions: () -> Unit,
    onOpenScope: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Tokens.Space.block.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CEmptyState(
            title = "这个条件下还没有成语",
            description = emptyDescription(state),
            actionText = "清除条件，看全部",
            onAction = onClearConditions,
        )
        Spacer(Modifier.height(Tokens.Space.ghostY.dp))
        CButton(
            text = "去改考试范围",
            onClick = onOpenScope,
            variant = CButtonVariant.Ghost,
            modifier = Modifier.padding(horizontal = Tokens.Space.pageX.dp),
        )
    }
}

/** 空态说明（信息架构 v1.2 §5.4 P03 行）：写清范围、字母与收录量的关系。 */
private fun emptyDescription(state: P03LibraryUiState): String {
    val letter = state.selectedLetter
    return if (letter != null) {
        "当前范围是「${state.scope.description}」，首字母「$letter」下没有收录。" +
            "范围越窄、字母越靠后，能看到的就越少。"
    } else {
        "当前范围是「${state.scope.description}」。" +
            "范围越窄，能看到的就越少；改范围或多选几类考试即可看到更多成语。"
    }
}
