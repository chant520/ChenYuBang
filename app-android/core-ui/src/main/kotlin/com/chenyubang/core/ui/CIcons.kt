package com.chenyubang.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chenyubang.core.ui.tokens.Tokens

private const val VIEW_BOX = 24f

private fun paths(kind: CIconKind): List<String> =
    when (kind) {
        CIconKind.Plus -> listOf("M12 5v14M5 12h14")
        CIconKind.Check -> listOf("M5 12l4.5 4.5L19 7")
        CIconKind.Warn -> listOf("M12 4l8 15H4z", "M12 9v4", "M12 16.5v.5")
        CIconKind.Error -> listOf("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M9 9l6 6M15 9l-6 6")
        CIconKind.Info -> listOf("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M12 11v5", "M12 8v.5")
        CIconKind.Camera -> listOf("M3 7h4l2-3h6l2 3h4v13H3zM16 13a4 4 0 1 1-8 0a4 4 0 1 1 8 0")
    }

private fun buildVector(
    kind: CIconKind,
    stroke: Float,
): ImageVector {
    val builder =
        ImageVector.Builder(
            defaultWidth = Tokens.Icon.n24.dp,
            defaultHeight = Tokens.Icon.n24.dp,
            viewportWidth = VIEW_BOX,
            viewportHeight = VIEW_BOX,
        )
    paths(kind).forEach { d ->
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }
    return builder.build()
}

/** 图标与文字同色；尺寸只取规范八档，描边宽度按 §6 表传入。 */
@Composable
fun CIcon(
    kind: CIconKind,
    size: Dp,
    stroke: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val vector = remember(kind, stroke) { buildVector(kind, stroke) }
    Icon(
        imageVector = vector,
        contentDescription = null,
        modifier = modifier.size(size),
        tint = color,
    )
}
