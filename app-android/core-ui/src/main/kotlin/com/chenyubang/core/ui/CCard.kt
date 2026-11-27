package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.chenyubang.core.ui.tokens.Tokens

/**
 * 卡片（全页稿 v2 `.card` / `.card.soft`）：底 `--surface`（soft 用 `--surface2`）、
 * 描边 1px `--line`、圆角 6、内边距 16。
 *
 * 页面只拼装构件，卡片留白与描边不落回页面代码（规范 §7 / §12.2 禁止 1）。
 */
@Composable
fun CCard(
    modifier: Modifier = Modifier,
    soft: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalColorSet.current
    val shape = RoundedCornerShape(Tokens.Radius.r6.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (soft) c.surface2 else c.surface, shape)
                .border(1.dp, c.line, shape)
                .padding(Tokens.Space.block.dp),
        content = content,
    )
}
