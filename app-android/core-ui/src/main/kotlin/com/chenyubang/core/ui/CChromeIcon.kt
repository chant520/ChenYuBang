package com.chenyubang.core.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.chenyubang.core.ui.tokens.Tokens

enum class CChromeIcon(val path: String) {
    Book("M4 4h6l2 2 2-2h6v16h-6l-2 2-2-2H4zM12 6v16"),
    Example("M6 3h12v18H6zM9 7h6M9 11h6M9 15h4"),
    Mistake("M5 3h14v18H5zM8 8l2 2 5-5M8 14h8M8 18h6"),
    Me("M16 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0M4 21v-2a8 8 0 0 1 16 0v2"),
    Camera("M3 7h4l2-3h6l2 3h4v13H3zM16 13a4 4 0 1 1-8 0a4 4 0 1 1 8 0"),
    Back("M15 5l-7 7 7 7"),
}

@Composable
fun CChromeGlyph(
    kind: CChromeIcon,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val vector =
        remember(kind) {
            ImageVector.Builder(
                defaultWidth = Tokens.Icon.n18.dp,
                defaultHeight = Tokens.Icon.n18.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).addPath(
                pathData = PathParser().parsePathString(kind.path).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
            ).build()
        }
    Icon(vector, null, modifier, tint = color)
}
