package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

// 全页稿 v2 P03 字母索引条：字号 10px、行距 1px、选中字母 accent/700，其余 text3。
private const val LETTER_GAP = 1
private const val LETTER_RAIL_WIDTH = 24
private const val LETTER_ROW_HEIGHT = 20

/**
 * 字母索引条（P03 右侧字母快速定位，来源全页稿 A v2）。
 *
 * 设计稿为 10px 字号 + 1px 行距；触摸端把每字母行高抬到 20，避免 10px 字号下无法点中。
 * 选中字母 `--accent` + 700，其余 `--text3`。装饰性排版留在 core-ui，页面只传数据。
 */
@Composable
fun CLetterIndex(
    letters: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        modifier = modifier.width(LETTER_RAIL_WIDTH.dp),
        verticalArrangement = Arrangement.spacedBy(LETTER_GAP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            val active = letter == selected
            val interaction = remember(letter) { MutableInteractionSource() }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(LETTER_ROW_HEIGHT.dp)
                        .background(
                            if (active) c.tintbg else Color.Transparent,
                            RoundedCornerShape(Tokens.Radius.r3.dp),
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClick = { onSelect(letter) },
                        )
                        .semantics { contentDescription = "首字母 $letter" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    color = if (active) c.accent else c.text3,
                    fontSize = Tokens.Font.n10.sp,
                    fontWeight = if (active) FontWeight.W700 else FontWeight.Normal,
                )
            }
        }
    }
}
