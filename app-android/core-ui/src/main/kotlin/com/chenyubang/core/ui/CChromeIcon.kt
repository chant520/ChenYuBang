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
    Search("M17.5 11a6.5 6.5 0 1 1-13 0a6.5 6.5 0 1 1 13 0M20 20l-4.6-4.6"),
    Chevron("M9 5l7 7-7 7"),
    Back("M15 5l-7 7 7 7"),

    // P04 关闭/清空（高保真稿 A P04 顶部 X 与输入框清空 X，同为内联 SVG）。
    Close("M6 6l12 12M18 6l-12 12"),

    // P05 读音播放预留位图标（高保真稿 A P05 成语名右侧笔形图标；朗读属未来项，本期只展示）。
    Sound("M4 13v6h6l10-10-6-6zM9 5l6 6"),
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
