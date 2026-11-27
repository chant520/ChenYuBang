package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

private const val CHECK_STROKE = 2f

/** 勾选行（规范 §5.15）：整行可点切换；末行传 showDivider=false。 */
@Composable
fun CCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: String? = null,
    warning: String? = null,
    showDivider: Boolean = true,
) {
    val c = LocalColorSet.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val boxShape = RoundedCornerShape(Tokens.Radius.r4.dp)
    val boxBackground =
        when {
            !enabled -> c.disbg
            checked -> c.accent
            else -> Color.Transparent
        }
    val boxLine =
        when {
            !enabled -> c.disline
            checked -> c.accent
            else -> c.line
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .pressDim(pressed && enabled)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = { onCheckedChange(!checked) },
                )
                .rowDivider(c.line2, showDivider)
                .padding(vertical = Tokens.Space.chkY.dp),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.chkGap.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(Tokens.Space.chkSize.dp)
                    .background(boxBackground, boxShape)
                    .border(1.dp, boxLine, boxShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked && enabled) {
                CIcon(kind = CIconKind.Check, size = Tokens.Icon.n12.dp, stroke = CHECK_STROKE, color = c.chkfg)
            }
        }
        Text(
            text = label,
            color = if (enabled) c.text else c.disfg,
            fontSize = Tokens.FontRole.body.sp,
        )
        if (warning != null) {
            Spacer(modifier = Modifier.weight(1f))
            CInlineWarning(text = warning)
        } else if (trailing != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(text = trailing, color = c.text3, fontSize = Tokens.FontRole.caption.sp)
        }
    }
}
