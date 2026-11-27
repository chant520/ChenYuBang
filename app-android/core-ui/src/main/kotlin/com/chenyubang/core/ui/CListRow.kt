package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

private const val NAME_LETTER_SPACING = 0.01f
private const val SUB_LINE_HEIGHT = 1.6f

/** 列表容器（规范 §5.13）：surface 底 + line 描边 + 圆角 6 + 溢出裁切。 */
@Composable
fun CList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalColorSet.current
    val shape = RoundedCornerShape(Tokens.Radius.r6.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.surface, shape)
                .border(1.dp, c.line, shape),
        content = content,
    )
}

@Composable
private fun ListRowTitle(
    name: String,
    pinyin: String,
    trailing: String?,
    nameColor: Color,
    secondaryColor: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lt1Gap.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = nameColor,
            fontSize = Tokens.FontRole.itemTitle.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = NAME_LETTER_SPACING.em,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = pinyin,
            color = secondaryColor,
            fontSize = Tokens.FontRole.caption.sp,
            modifier = Modifier.alignByBaseline(),
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = trailing,
                color = secondaryColor,
                fontSize = Tokens.FontRole.caption.sp,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** 列表行（规范 §5.13）。末行传 showDivider=false；禁用行元信息请挂中性标签。 */
@Composable
fun CListRow(
    name: String,
    pinyin: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    trailing: String? = null,
    meta: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalColorSet.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val clickable = onClick != null && enabled
    val background =
        when {
            !enabled -> c.disbg
            selected -> c.tintbg
            else -> Color.Transparent
        }
    val nameColor =
        when {
            !enabled -> c.disfg
            selected -> c.accent
            else -> c.text
        }
    val secondaryColor = if (enabled) c.text3 else c.disfg
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                .then(
                    if (clickable) {
                        Modifier
                            .pressDim(pressed)
                            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .rowDivider(c.line2, showDivider)
                .padding(horizontal = Tokens.Space.lrowX.dp, vertical = Tokens.Space.lrowY.dp),
    ) {
        ListRowTitle(
            name = name,
            pinyin = pinyin,
            trailing = trailing,
            nameColor = nameColor,
            secondaryColor = secondaryColor,
        )
        if (sub != null) {
            Text(
                text = sub,
                color = if (enabled) c.text2 else c.disfg,
                fontSize = Tokens.FontRole.supporting.sp,
                lineHeight = (Tokens.FontRole.supporting * SUB_LINE_HEIGHT).sp,
                modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
            )
        }
        if (meta != null) {
            ListRowMeta(content = meta)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListRowMeta(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(top = Tokens.Space.lmetaTop.dp),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
    ) {
        content()
    }
}
