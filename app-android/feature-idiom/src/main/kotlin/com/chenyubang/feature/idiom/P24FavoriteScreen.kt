package com.chenyubang.feature.idiom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CList
import com.chenyubang.core.ui.CListRow
import com.chenyubang.core.ui.CSkeletonBlock
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.tokens.Tokens

/** 骨架屏行数：与首屏一屏内可见的列表行数对齐。 */
private const val SKELETON_ROWS = 3

/** P24 路由入口：装配只读收藏 ViewModel；取消收藏在 P05 完成，返回按 revision 重新读取。 */
@Composable
fun P24FavoriteRoute(
    database: AppDatabase,
    revision: Long = 0,
    onBack: () -> Unit = {},
    onOpenIdiom: (String) -> Unit = {},
    onGoLibrary: () -> Unit = {},
) {
    val factory =
        remember(database) {
            viewModelFactory {
                initializer {
                    P24FavoriteViewModel(loadFavorite = { database.idiomStateDao().favoriteIdioms() })
                }
            }
        }
    val model: P24FavoriteViewModel = viewModel(key = "p24-favorite", factory = factory)
    val state by model.state.collectAsState()
    LaunchedEffect(revision) { model.load() }
    P24FavoriteContent(
        state = state,
        onBack = onBack,
        onOpenIdiom = onOpenIdiom,
        onGoLibrary = onGoLibrary,
        onRetry = model::load,
    )
}

/** P24「我的收藏」主体：复用 P03 的 CListRow（带三态），点行进 P05；空态三件套 + 去成语库。 */
@Composable
fun P24FavoriteContent(
    state: P24FavoriteUiState,
    onBack: () -> Unit = {},
    onOpenIdiom: (String) -> Unit = {},
    onGoLibrary: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        FavoriteHeader(colors = colors, onBack = onBack)
        FavoriteBody(
            state = state,
            onOpenIdiom = onOpenIdiom,
            onGoLibrary = onGoLibrary,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun FavoriteHeader(
    colors: Tokens.ColorSet,
    onBack: () -> Unit,
) {
    Row(
        Modifier.padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Tokens.Space.hitMin.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            CChromeGlyph(
                CChromeIcon.Back,
                colors.text2,
                Modifier.size(Tokens.Icon.n21.dp),
            )
        }
        Text(
            text = "我的收藏",
            color = colors.head,
            fontSize = Tokens.FontRole.pageTitle.sp,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(start = Tokens.Space.lmetaGap.dp),
        )
    }
}

@Composable
private fun FavoriteBody(
    state: P24FavoriteUiState,
    onOpenIdiom: (String) -> Unit,
    onGoLibrary: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Tokens.Space.pageX.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        when {
            state.failed ->
                CBar(
                    kind = CBarKind.Warn,
                    title = "收藏列表读取失败，请重试",
                    subtitle = "本机收藏数据未改动。",
                    actionText = "重试",
                    onAction = onRetry,
                )
            state.loading ->
                repeat(SKELETON_ROWS) {
                    CSkeletonBlock(height = Tokens.Space.hitMin.dp, modifier = Modifier.fillMaxSize())
                }
            state.isEmpty ->
                CEmptyState(
                    title = "还没收藏成语",
                    description = "在成语库里点右上角收藏，\n就能在这里集中看",
                    actionText = "去成语库",
                    onAction = onGoLibrary,
                )
            else ->
                CList {
                    state.items.forEachIndexed { index, item ->
                        val tag = statusTag(item.status)
                        CListRow(
                            name = item.word,
                            pinyin = item.pinyin,
                            showDivider = index != state.items.lastIndex,
                            meta = {
                                CTag("考频 ${item.frequency}")
                                CTag(tag.text, style = tag.style)
                            },
                            onClick = { onOpenIdiom(item.idiomId) },
                        )
                    }
                }
        }
    }
}
