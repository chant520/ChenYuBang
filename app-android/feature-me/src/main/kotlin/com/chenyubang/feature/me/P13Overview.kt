package com.chenyubang.feature.me

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.MistakeOverview
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CList
import com.chenyubang.core.ui.CListRow
import com.chenyubang.core.ui.tokens.Tokens
import kotlinx.coroutines.flow.first

/**
 * P13「我的」E2 数据概览（4+1 纯数字）+ 设置分组首行「我的收藏 N」入口。
 *
 * 数字格不可点、不跳页；全空显示 0。取数 revision 变化时回读五项。
 * 本 Composable 本体不含拍照/备份等其它设置行，交由调用方拼装。
 */
@Composable
fun P13OverviewRoute(
    database: AppDatabase,
    revision: Long = 0,
    onOpenFavorites: () -> Unit = {},
) {
    val factory =
        remember(database) {
            viewModelFactory {
                initializer {
                    P13OverviewViewModel(
                        loadLearned = { database.idiomStateDao().countLearned() },
                        loadMastered = { database.idiomStateDao().countMastered() },
                        loadFavorite = { database.idiomStateDao().countFavorite() },
                        loadMistakeOverview = {
                            val o: MistakeOverview = database.mistakeQueryDao().overview().first()
                            o
                        },
                    )
                }
            }
        }
    val model: P13OverviewViewModel = viewModel(key = "p13-overview", factory = factory)
    val stats by model.state.collectAsState()
    LaunchedEffect(revision) { model.refresh() }
    P13OverviewContent(stats = stats, onOpenFavorites = onOpenFavorites)
}

/** E2 概览主体（供快照直接渲染，不依赖数据库）。 */
@Composable
fun P13OverviewContent(
    stats: P13OverviewStats,
    onOpenFavorites: () -> Unit = {},
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp)) {
        CCard {
            CLabel(text = "数据概览")
            Spacer(Modifier.height(Tokens.Space.lmetaTop.dp))
            // 上四格：已学成语 / 收藏 / 错题 / 待复习。
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NumberCell("已学成语", stats.learned, colors.text, hot = false)
                NumberCell("收藏", stats.favorites, colors.text, hot = false)
                NumberCell("错题", stats.mistakes, colors.text, hot = false)
                NumberCell("待复习", stats.pending, colors.hot, hot = true)
            }
            Spacer(Modifier.height(Tokens.Space.lmetaTop.dp))
            // 第五格：已掌握，单独一行并入同一卡片。
            NumberCell("已掌握", stats.mastered, colors.text, hot = false)
        }
        CList {
            CListRow(
                name = "我的收藏",
                pinyin = "",
                trailing = "${stats.favorites}",
                onClick = onOpenFavorites,
            )
        }
    }
}

/** 单个数字格：数字 20/700 + 标签 11，纯展示不可点。 */
@Composable
private fun NumberCell(
    label: String,
    value: Int,
    valueColor: Color,
    hot: Boolean,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            color = valueColor,
            fontSize = Tokens.Font.n20.sp,
            fontWeight = FontWeight.W700,
        )
        Text(
            text = label,
            color = if (hot) colors.hot else colors.text3,
            fontSize = Tokens.Font.n11.sp,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
    }
}
