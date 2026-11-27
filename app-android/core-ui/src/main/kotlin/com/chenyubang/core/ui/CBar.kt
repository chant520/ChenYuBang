package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

private const val BAR_TITLE_LINE_HEIGHT = 1.55f
private const val BAR_SUB_LINE_HEIGHT = 1.65f
private const val BAR_ICON_STROKE = 1.8f
private const val INLINE_ICON_STROKE = 1.9f
private const val INLINE_GAP = 6

// 全页稿 v2 `.warnbar svg` / `.errbar svg` margin-top:2px、`.ws` / `.es` margin-top:4px
private const val BAR_ICON_TOP = 2
private const val BAR_SUB_GAP = 4

private class BarPalette(
    val background: Color,
    val foreground: Color,
    val line: Color,
    val icon: CIconKind,
)

private fun barPalette(
    c: Tokens.ColorSet,
    kind: CBarKind,
): BarPalette =
    when (kind) {
        CBarKind.Info -> BarPalette(c.tintbg, c.tintfg, c.tintline, CIconKind.Info)
        CBarKind.Warn -> BarPalette(c.warnbg, c.warnfg, c.warnline, CIconKind.Warn)
        CBarKind.Error -> BarPalette(c.errbg, c.errfg, c.errline, CIconKind.Error)
    }

/** 提示条（规范 §5.4 / §5.5）：图标 + 标题 + 可选副文案 + 可选操作链接。 */
@Composable
fun CBar(
    kind: CBarKind,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = LocalColorSet.current
    val shape = RoundedCornerShape(Tokens.Radius.r6.dp)
    val palette = barPalette(c, kind)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(palette.background, shape)
                .border(1.dp, palette.line, shape)
                .padding(horizontal = Tokens.Space.barX.dp, vertical = Tokens.Space.barY.dp),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.barGap.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CIcon(
            kind = palette.icon,
            size = Tokens.Icon.n18.dp,
            stroke = BAR_ICON_STROKE,
            color = palette.foreground,
            modifier = Modifier.padding(top = BAR_ICON_TOP.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BAR_SUB_GAP.dp),
        ) {
            Text(
                text = title,
                color = palette.foreground,
                fontSize = Tokens.FontRole.sectionTitle.sp,
                fontWeight = FontWeight.W700,
                lineHeight = (Tokens.FontRole.sectionTitle * BAR_TITLE_LINE_HEIGHT).sp,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = c.text2,
                    fontSize = Tokens.FontRole.caption.sp,
                    lineHeight = (Tokens.FontRole.caption * BAR_SUB_LINE_HEIGHT).sp,
                )
            }
        }
        if (actionText != null && onAction != null) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Text(
                text = actionText,
                color = palette.foreground,
                fontSize = Tokens.FontRole.button.sp,
                fontWeight = FontWeight.W700,
                textDecoration = TextDecoration.Underline,
                modifier =
                    Modifier
                        .pressDim(pressed)
                        .clickable(interactionSource = interaction, indication = null, onClick = onAction),
            )
        }
    }
}

/** 行内警示（规范 §5.4）：12/600 warnfg + 13px 图标。 */
@Composable
fun CInlineWarning(
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalColorSet.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(INLINE_GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CIcon(kind = CIconKind.Warn, size = Tokens.Icon.n13.dp, stroke = INLINE_ICON_STROKE, color = c.warnfg)
        Text(text = text, color = c.warnfg, fontSize = Tokens.FontRole.supporting.sp, fontWeight = FontWeight.W600)
    }
}

/** 行内错误（规范 §5.5）：12/600 errfg + 13px 图标。 */
@Composable
fun CInlineError(
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalColorSet.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(INLINE_GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CIcon(kind = CIconKind.Error, size = Tokens.Icon.n13.dp, stroke = INLINE_ICON_STROKE, color = c.errfg)
        Text(text = text, color = c.errfg, fontSize = Tokens.FontRole.supporting.sp, fontWeight = FontWeight.W600)
    }
}
