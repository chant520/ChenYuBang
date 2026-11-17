package com.chenyubang.core.ui

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.chenyubang.core.ui.tokens.Tokens

private const val FAB_ICON_STROKE = 1.6f
private const val FAB_BADGE_GAP = 10

// CSS box-shadow 模糊半径 ≈ 2σ，BlurMaskFilter 取 σ
private const val CSS_BLUR_TO_SIGMA = 0.5f

private fun DrawScope.drawFabShadow(layers: List<Tokens.ShadowLayer>) {
    val radius = Tokens.Radius.r6.dp.toPx()
    layers.forEach { layer ->
        val spread = layer.spread.dp.toPx()
        val ox = layer.offsetX.dp.toPx()
        val oy = layer.offsetY.dp.toPx()
        if (layer.blur > 0f) {
            val paint =
                Paint().apply {
                    val framework = asFrameworkPaint()
                    framework.isAntiAlias = true
                    framework.color = layer.color.toArgb()
                    framework.maskFilter =
                        BlurMaskFilter(layer.blur.dp.toPx() * CSS_BLUR_TO_SIGMA, BlurMaskFilter.Blur.NORMAL)
                }
            drawIntoCanvas {
                it.drawRoundRect(
                    ox - spread,
                    oy - spread,
                    size.width + ox + spread,
                    size.height + oy + spread,
                    radius + spread,
                    radius + spread,
                    paint,
                )
            }
        } else {
            drawRoundRect(
                color = layer.color,
                topLeft = Offset(ox - spread / 2f, oy - spread / 2f),
                size = Size(size.width + spread, size.height + spread),
                cornerRadius = CornerRadius(radius + spread / 2f),
                style = Stroke(width = spread),
            )
        }
    }
}

/** 悬浮拍照按钮（规范 §5.8）。定位 right20/bottom88 由页面负责；仅四个主标签页挂载。 */
@Composable
fun CFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badgeText: String? = null,
) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) Tokens.Dark else Tokens.Light
    val layers = if (dark) Tokens.Shadow.fabDark else Tokens.Shadow.fabLight
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Tokens.Radius.r6.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FAB_BADGE_GAP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .pressDim(pressed && enabled)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                    )
                    .size(Tokens.Space.fabSize.dp)
                    .drawBehind { if (enabled) drawFabShadow(layers) }
                    .background(if (enabled) c.fab else c.disbg, shape),
            contentAlignment = Alignment.Center,
        ) {
            CIcon(
                kind = CIconKind.Plus,
                size = Tokens.Icon.n24.dp,
                stroke = FAB_ICON_STROKE,
                color = if (enabled) c.fabicon else c.disfg,
            )
        }
        if (badgeText != null) {
            CBadge(text = badgeText, kind = CBadgeKind.Warn)
        }
    }
}
