package com.chenyubang.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val DASH_LEN = 3

/** 虚线描边（CSS border:1px dashed 的平台等效）。 */
internal fun Modifier.dashedBorder(
    color: Color,
    radius: Dp,
    width: Dp = 1.dp,
): Modifier =
    drawBehind {
        val dash = DASH_LEN.dp.toPx()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius.toPx()),
            style =
                Stroke(
                    width = width.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                ),
        )
    }

/** 行底 1px 分隔线（规范 §5.13 / §5.15，末行不画）。 */
internal fun Modifier.rowDivider(
    color: Color,
    show: Boolean,
): Modifier =
    if (!show) {
        this
    } else {
        drawBehind {
            val stroke = 1.dp.toPx()
            drawRect(
                color = color,
                topLeft = Offset(0f, size.height - stroke),
                size = Size(size.width, stroke),
            )
        }
    }
