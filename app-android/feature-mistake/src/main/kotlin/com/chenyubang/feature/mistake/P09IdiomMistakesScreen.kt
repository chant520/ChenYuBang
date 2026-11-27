package com.chenyubang.feature.mistake

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.ui.CBadge
import com.chenyubang.core.ui.CBadgeKind
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.CTagStyle
import com.chenyubang.core.ui.tokens.Tokens

/** P09 路由入口：装配成语 + 该成语下全部录入题。 */
@Composable
fun P09IdiomMistakesRoute(
    idiomId: String,
    database: AppDatabase,
    onBack: () -> Unit,
    onOpenIdiom: (String) -> Unit,
    onOpenExample: (String) -> Unit,
    revision: Long = 0,
) {
    val factory =
        remember(database) {
            viewModelFactory {
                initializer {
                    P09IdiomMistakesViewModel(
                        loadIdiom = { id -> database.idiomDao().byId(id) },
                        loadExamples = { id -> database.mistakeQueryDao().userExamplesByIdiom(id) },
                    )
                }
            }
        }
    val model: P09IdiomMistakesViewModel = viewModel(key = "p09-idiom-mistakes", factory = factory)
    val state by model.state.collectAsState()
    LaunchedEffect(idiomId, revision) { model.load(idiomId) }
    P09IdiomMistakesContent(
        state = state,
        onBack = onBack,
        onOpenIdiom = onOpenIdiom,
        onRetry = { model.load(idiomId) },
        onOpenExample = onOpenExample,
    )
}

/**
 * P09 某成语下我录入的题（信息架构 v1.4 第 291 行）。
 *
 * 列表来自 user_example_idiom，包含未标错的录入题；标错的才展示错题徽标与掌握状态。
 * 顶部返回与成语名自绘（self-chrome），整体沿用 P05 的加载 / missing / failed 三态。
 */
@Composable
fun P09IdiomMistakesContent(
    state: P09IdiomMistakesUiState,
    onBack: () -> Unit,
    onOpenIdiom: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenExample: (String) -> Unit = {},
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
    ) {
        item { P09Header(word = state.word, onBack = onBack) }
        p09StateBody(state, onBack, onOpenIdiom, onRetry, onOpenExample)
    }
}

/** P09 状态分发：缺失/失败/加载与录入题列表。 */
private fun LazyListScope.p09StateBody(
    state: P09IdiomMistakesUiState,
    onBack: () -> Unit,
    onOpenIdiom: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenExample: (String) -> Unit,
) {
    when {
        state.missing -> {
            sectionGap()
            item {
                CEmptyState(
                    title = "没有找到这个成语",
                    description = "它可能已随题库更新被替换，回错题本看看别的成语。",
                    actionText = "返回错题本",
                    onAction = onBack,
                )
            }
        }
        state.failed -> {
            sectionGap()
            item {
                CBar(
                    kind = CBarKind.Warn,
                    title = "录入题读取失败，请重试",
                    subtitle = "只读本机题库，不联网。",
                    actionText = "重试",
                    onAction = onRetry,
                )
            }
        }
        state.loading -> {
            sectionGap()
            item { CBar(CBarKind.Info, "正在读取录入题", subtitle = "只读本机题库，不联网。") }
        }
        else -> p09LoadedBody(state, onBack, onOpenIdiom, onOpenExample)
    }
}

/** P09 正常态：释义卡、录入题列表与末尾提示。 */
private fun LazyListScope.p09LoadedBody(
    state: P09IdiomMistakesUiState,
    onBack: () -> Unit,
    onOpenIdiom: (String) -> Unit,
    onOpenExample: (String) -> Unit,
) {
    if (state.senseLine.isNotEmpty()) {
        sectionGap()
        item {
            P09SenseCard(senseLine = state.senseLine, onOpenIdiom = { onOpenIdiom(state.idiomId) })
        }
    }
    sectionGap()
    item { CLabel(text = "我录入的题（标错的按错题展示）") }
    if (state.rows.isEmpty()) {
        sectionGap()
        item {
            CEmptyState(
                title = "这个成语下还没有录入题",
                description = "之前录的题已经删掉或已归到别的成语下。",
                actionText = "返回错题本",
                onAction = onBack,
            )
        }
    } else {
        sectionGap()
        state.rows.forEachIndexed { index, row ->
            item(key = row.id) {
                UserExampleRow(row = row, onClick = { onOpenExample(row.id) })
                if (index != state.rows.lastIndex) RowDivider()
            }
        }
        sectionGap()
        item {
            Text(
                text = state.rows.size.toString() + " 道 · 已到末尾",
                color = if (isSystemInDarkTheme()) Tokens.Dark.text3 else Tokens.Light.text3,
                fontSize = Tokens.FontRole.caption.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.lmetaTop.dp),
            )
        }
    }
}

/** 与原 Column 的 spacedBy(block) 等价的区块间距；录入题行内分隔由行自身绘制。 */
private fun LazyListScope.sectionGap() {
    item { Spacer(Modifier.height(Tokens.Space.block.dp)) }
}

@Composable
private fun P09Header(
    word: String,
    onBack: () -> Unit,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(Tokens.Space.hitMin.dp).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            CChromeGlyph(kind = CChromeIcon.Back, color = c.text2)
        }
        Text(
            text = word.ifBlank { "录入题" },
            color = c.head,
            fontSize = Tokens.FontRole.pageTitle.sp,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(start = Tokens.Space.lt1Gap.dp),
        )
    }
}

@Composable
private fun P09SenseCard(
    senseLine: String,
    onOpenIdiom: () -> Unit,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CCard(modifier = Modifier.clickable(onClick = onOpenIdiom)) {
        Row(
            modifier = Modifier.padding(Tokens.Space.block.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = senseLine,
                color = c.text3,
                fontSize = Tokens.FontRole.supporting.sp,
                lineHeight = Tokens.FontRole.supporting.sp * 1.65f,
                modifier = Modifier.weight(1f),
            )
            CChromeGlyph(
                kind = CChromeIcon.Chevron,
                color = c.accent,
                modifier = Modifier.padding(start = Tokens.Space.lt1Gap.dp).size(Tokens.Icon.n15.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserExampleRow(
    row: IdiomUserExampleView,
    onClick: () -> Unit,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Tokens.Space.lrowX.dp, vertical = Tokens.Space.lrowY.dp),
    ) {
        Text(
            text = row.stem,
            color = c.text2,
            fontSize = Tokens.FontRole.supporting.sp,
            lineHeight = Tokens.FontRole.supporting.sp * 1.6f,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.lmetaTop.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
        ) {
            val mark = row.mistake
            if (mark == null) {
                CTag(text = "我录入", style = CTagStyle.Ghost)
                Text(text = row.recordedAt, color = c.text3, fontSize = Tokens.FontRole.caption.sp)
                Text(text = "未标错", color = c.text3, fontSize = Tokens.FontRole.caption.sp)
            } else {
                CBadge(text = "错题", kind = CBadgeKind.Error)
                Text(text = row.recordedAt, color = c.text3, fontSize = Tokens.FontRole.caption.sp)
                CTag(
                    text = mark.state,
                    style = if (mark.state == MISTAKE_STATE_PENDING) CTagStyle.Solid else CTagStyle.Ghost,
                )
                mark.tagNames.forEach { tag ->
                    Text(text = tag, color = c.text3, fontSize = Tokens.FontRole.label.sp)
                }
            }
        }
    }
}

@Composable
private fun RowDivider() {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    HorizontalDivider(color = c.line2, thickness = 1.dp)
}
