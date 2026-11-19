package com.chenyubang.feature.idiom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CInput
import com.chenyubang.core.ui.CList
import com.chenyubang.core.ui.CListRow
import com.chenyubang.core.ui.CSkeletonBlock
import com.chenyubang.core.ui.tokens.Tokens

/** 骨架行数：与首屏可见结果行数对齐，避免加载态跳动。 */
private const val SEARCH_SKELETON_ROWS = 3

/** P04 路由入口：装配只读 FTS 搜索 ViewModel。 */
@Composable
fun P04SearchRoute(
    database: AppDatabase,
    onClose: () -> Unit,
    onOpenIdiom: (String) -> Unit,
) {
    val factory =
        remember(database) {
            viewModelFactory {
                initializer {
                    P04SearchViewModel(search = { query -> database.idiomDao().search(query) })
                }
            }
        }
    val model: P04SearchViewModel = viewModel(key = "p04-search", factory = factory)
    val state by model.state.collectAsState()
    P04SearchContent(
        state = state,
        onQuery = model::onQueryChange,
        onClear = model::clear,
        onClose = onClose,
        onOpenIdiom = onOpenIdiom,
    )
}

/** P04 搜索全屏浮层（信息架构 v1.3 P04 ①–⑤）：顶部关闭 + 自动聚焦搜索框 + 结果 / 空态。 */
@Composable
fun P04SearchContent(
    state: P04SearchUiState,
    onQuery: (String) -> Unit = {},
    onClear: () -> Unit = {},
    onClose: () -> Unit = {},
    onOpenIdiom: (String) -> Unit = {},
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        SearchHeader(colors = colors, onClose = onClose)
        CInput(
            value = state.query,
            onValueChange = onQuery,
            placeholder = "完整成语 / 单字 / 全拼 / 首字母",
            autoFocus = true,
            leadingIcon = {
                CChromeGlyph(CChromeIcon.Search, colors.text3, Modifier.size(Tokens.Icon.n15.dp))
            },
            trailing = {
                if (state.query.isNotEmpty()) {
                    ClearButton(colors = colors, onClear = onClear)
                }
            },
        )
        Text(
            text = IdiomSearchUi.INPUT_HINT,
            color = colors.text3,
            fontSize = Tokens.Font.n12.sp,
        )
        when {
            state.query.isBlank() -> Unit
            state.failed ->
                CBar(
                    kind = CBarKind.Warn,
                    title = "搜索失败，请重试",
                    subtitle = "改一个字或稍后再试；内置题库的成语仍可浏览。",
                )
            state.loading -> SearchSkeleton()
            state.showNoResult -> NoResult(colors = colors)
            else -> SearchResults(results = state.results, colors = colors, onOpenIdiom = onOpenIdiom)
        }
    }
}

@Composable
private fun SearchHeader(
    colors: Tokens.ColorSet,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(Tokens.Space.hitMin.dp)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            CChromeGlyph(CChromeIcon.Close, colors.text2, Modifier.size(Tokens.Icon.n21.dp))
        }
        Text(
            text = "搜索",
            color = colors.head,
            fontSize = Tokens.Font.n15.sp,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(start = Tokens.Space.lmetaGap.dp),
        )
    }
}

@Composable
private fun ClearButton(
    colors: Tokens.ColorSet,
    onClear: () -> Unit,
) {
    Box(
        Modifier
            .size(Tokens.Space.hitMin.dp)
            .clickable(onClick = onClear),
        contentAlignment = Alignment.Center,
    ) {
        CChromeGlyph(CChromeIcon.Close, colors.text3, Modifier.size(Tokens.Icon.n14.dp))
    }
}

@Composable
private fun SearchSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp)) {
        repeat(SEARCH_SKELETON_ROWS) {
            CSkeletonBlock(
                height = Tokens.Space.hitMin.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 查无结果：明确文案 + 输入方式提示；不清空已输入内容（D 稿 P04 空态）。 */
@Composable
private fun NoResult(colors: Tokens.ColorSet) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Space.block.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CEmptyState(title = IdiomSearchUi.NO_RESULT_TITLE, description = IdiomSearchUi.NO_RESULT_HINT)
        Text(
            text = "已输入的内容不会自动清空，改字即时出新结果",
            color = colors.text3,
            fontSize = Tokens.Font.n12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Space.ghostY.dp),
        )
    }
}

@Composable
private fun SearchResults(
    results: List<SearchIdiom>,
    colors: Tokens.ColorSet,
    onOpenIdiom: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp)) {
        CList {
            results.forEachIndexed { index, idiom ->
                CListRow(
                    name = idiom.word,
                    pinyin = idiom.pinyin,
                    sub = idiom.senseFirst,
                    showDivider = index != results.lastIndex,
                    onClick = { onOpenIdiom(idiom.id) },
                )
            }
        }
        Text(
            text = "${results.size} 条结果",
            color = colors.text3,
            fontSize = Tokens.Font.n11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
