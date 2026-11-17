package com.chenyubang.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.chenyubang.core.ui.tokens.Tokens

private const val SPINNER_PERIOD_MS = 800
private const val SPINNER_SWEEP = 270f
private const val SPINNER_GAP_START = 45f
private const val SPINNER_BORDER = 2f

/** 加载转圈（规范 §5.0 / §6）：14px、2px currentColor、右缺 90°、0.8s 匀速。 */
@Composable
fun CSpinner(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val rotation by rememberInfiniteTransition(label = "spinner")
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(SPINNER_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "spinnerRotation",
        )
    val angle = if (reduceMotion) 0f else rotation
    Canvas(modifier = modifier.size(Tokens.Icon.n14.dp)) {
        val border = SPINNER_BORDER.dp.toPx()
        val inner = size.minDimension - border * 2f
        drawArc(
            color = color,
            startAngle = SPINNER_GAP_START + angle,
            sweepAngle = SPINNER_SWEEP,
            useCenter = false,
            topLeft = Offset(border, border),
            size = Size(inner, inner),
            style = Stroke(width = border, cap = StrokeCap.Butt),
        )
    }
}
