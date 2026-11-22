package com.chenyubang.feature.mistake

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CList
import com.chenyubang.core.ui.tokens.Tokens

/** P08 列表内的分组、行与总览格子；从页面文件拆出，避免单文件函数过多。 */

@Composable
internal fun P08Overview(
    total: Int,
    pending: Int,
) {
    CCard {
        Row(
            modifier = Modifier.padding(Tokens.Space.block.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.pageX.dp),
        ) {
            OverviewCell(value = total, label = "错题总数", hot = false)
            OverviewCell(value = pending, label = "待复习", hot = true)
        }
    }
}

@Composable
private fun OverviewCell(
    value: Int,
    label: String,
    hot: Boolean,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column {
        Text(
            text = value.toString(),
            color = if (hot) c.hot else c.head,
            fontSize = Tokens.Font.n30.sp,
            fontWeight = FontWeight.W700,
        )
        Text(
            text = label,
            color = c.text2,
            fontSize = Tokens.Font.n11.sp,
            modifier = Modifier.padding(top = Tokens.Space.lt1Gap.dp),
        )
    }
}

@Composable
internal fun P08IdiomGroups(
    groups: List<MistakeIdiomGroupView>,
    onOpenIdiom: (String) -> Unit,
) {
    CList {
        groups.forEachIndexed { index, group ->
            IdiomGroupRow(group = group, onClick = { onOpenIdiom(group.idiomId) })
            if (index != groups.lastIndex) RowDivider()
        }
    }
}

@Composable
private fun IdiomGroupRow(
    group: MistakeIdiomGroupView,
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.word,
                color = c.text,
                fontSize = Tokens.Font.n14.sp,
                fontWeight = FontWeight.W600,
                modifier = Modifier.weight(1f),
            )
            Text(text = group.count.toString() + " 道", color = c.text3, fontSize = Tokens.Font.n11.sp)
        }
        group.reasonLine?.let { line ->
            Text(
                text = line,
                color = c.text2,
                fontSize = Tokens.Font.n12.sp,
                lineHeight = Tokens.Font.n12.sp * 1.6f,
                modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
            )
        }
    }
}

@Composable
internal fun P08MistakeRows(
    items: List<MistakeItemView>,
    onOpenMistake: (String) -> Unit,
) {
    CList {
        items.forEachIndexed { index, item ->
            MistakeRow(item = item, onClick = { item.userExampleId?.let(onOpenMistake) })
            if (index != items.lastIndex) RowDivider()
        }
    }
}

@Composable
internal fun P08ExamTypeGroups(
    items: List<MistakeItemView>,
    onOpenMistake: (String) -> Unit,
) {
    val groups = items.groupBy { examTypeLabel(it.examType) }.toList()
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp)) {
        groups.forEach { (label, rows) ->
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.lt1Gap.dp)) {
                CLabel(text = label, modifier = Modifier.padding(start = Tokens.Space.lrowX.dp))
                CList {
                    rows.forEachIndexed { index, item ->
                        MistakeRow(item = item, onClick = { item.userExampleId?.let(onOpenMistake) })
                        if (index != rows.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MistakeRow(
    item: MistakeItemView,
    onClick: () -> Unit,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = item.userExampleId != null, onClick = onClick)
                .padding(horizontal = Tokens.Space.lrowX.dp, vertical = Tokens.Space.lrowY.dp),
    ) {
        Text(
            text = item.stem,
            color = c.text2,
            fontSize = Tokens.Font.n12.sp,
            lineHeight = Tokens.Font.n12.sp * 1.6f,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.lmetaTop.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
        ) {
            Text(text = item.recordedAt, color = c.text3, fontSize = Tokens.Font.n11.sp)
            item.idiomWords.forEach { word ->
                Text(text = word, color = c.text3, fontSize = Tokens.Font.n11.sp)
            }
        }
    }
}

@Composable
private fun RowDivider() {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    HorizontalDivider(color = c.line2, thickness = 1.dp)
}
