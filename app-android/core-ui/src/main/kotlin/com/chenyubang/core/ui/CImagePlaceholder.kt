package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

private const val PH_GAP = 7
private const val PH_ICON_STROKE = 1.7f
private const val CUT_TOP = 6
private const val CUT_PAD_TOP = 5
private const val CUT_LETTER_SPACING = 0.06f
private const val DASH_LEN = 3

/** 图片占位（规范 §5.7）：中性空态 / 原图丢失 / 骨架 / 长页截断。 */
@Composable
fun CImagePlaceholder(
    kind: CPlaceholderKind,
    modifier: Modifier = Modifier,
    height: Dp = Tokens.Space.imgphH.dp,
    text: String? = null,
    subtitle: String? = null,
    cutLabel: String? = null,
) {
    val c = LocalColorSet.current
    if (kind == CPlaceholderKind.Skeleton) {
        CSkeletonBlock(height = height, modifier = modifier.fillMaxWidth())
        return
    }
    val warn = kind == CPlaceholderKind.Warn
    val background = if (warn) c.warnbg else c.phbg
    val line = if (warn) c.warnline else c.phline
    val shape = RoundedCornerShape(Tokens.Radius.r6.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(background, shape)
                    .dashedBorder(line, Tokens.Radius.r6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (warn) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PH_GAP.dp),
                ) {
                    CIcon(kind = CIconKind.Warn, size = Tokens.Icon.n26.dp, stroke = PH_ICON_STROKE, color = c.warnfg)
                    if (text != null) {
                        Text(
                            text = text,
                            color = c.warnfg,
                            fontSize = Tokens.FontRole.sectionTitle.sp,
                            fontWeight = FontWeight.W700,
                        )
                    }
                    if (subtitle != null) {
                        Text(text = subtitle, color = c.text2, fontSize = Tokens.FontRole.caption.sp)
                    }
                }
            } else if (text != null) {
                Text(text = text, color = c.text3, fontSize = Tokens.FontRole.caption.sp)
            }
        }
        if (kind == CPlaceholderKind.Truncated && cutLabel != null) {
            CutLabel(label = cutLabel, textColor = c.text3, cutColor = c.cut)
        }
    }
}

@Composable
private fun CutLabel(
    label: String,
    textColor: Color,
    cutColor: Color,
) {
    Text(
        text = label,
        color = textColor,
        fontSize = Tokens.FontRole.label.sp,
        letterSpacing = CUT_LETTER_SPACING.em,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = CUT_TOP.dp)
                .drawBehind {
                    val dash = DASH_LEN.dp.toPx()
                    drawLine(
                        color = cutColor,
                        start = Offset.Zero,
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                    )
                }
                .padding(top = CUT_PAD_TOP.dp),
    )
}
