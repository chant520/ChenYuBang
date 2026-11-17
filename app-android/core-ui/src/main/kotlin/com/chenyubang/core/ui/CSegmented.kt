package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

/** 分段控件（规范 §5.14）：项等分，选中 segon/segonfg，禁用项不可点。 */
@Composable
fun CSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    disabledIndices: Set<Int> = emptySet(),
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val outer = RoundedCornerShape(Tokens.Radius.r5.dp)
    val item = RoundedCornerShape(Tokens.Radius.r4.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(c.seg, outer)
                .border(1.dp, c.line, outer)
                .padding(Tokens.Space.segPad.dp),
    ) {
        items.forEachIndexed { index, label ->
            key(index) {
                // 规范 §5.14 禁用项「底不变」：选中底与字重保留，只换字色为 disfg
                val selected = index == selectedIndex
                val disabled = index in disabledIndices
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val foreground =
                    when {
                        disabled -> c.disfg
                        selected -> c.segonfg
                        else -> c.text2
                    }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(item)
                            .background(if (selected) c.segon else Color.Transparent, item)
                            .pressDim(pressed && !disabled)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                enabled = !disabled,
                                onClick = { onSelect(index) },
                            )
                            .padding(vertical = Tokens.Space.segY.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = foreground,
                        fontSize = Tokens.Font.n11.sp,
                        fontWeight = if (selected) FontWeight.W700 else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
