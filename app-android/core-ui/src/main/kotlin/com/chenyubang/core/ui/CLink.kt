package com.chenyubang.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

/**
 * 文字链接（全页稿 v2 `.linkbtn`）：12px、`--accent`、下划线。用于「调整省份」等页内跳转。
 *
 * 命中区按规范 §9.3 用纵向内边距补足到 ≥44：视觉字号不变。
 */
@Composable
fun CLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text = text,
        color = c.accent,
        fontSize = Tokens.Font.n12.sp,
        textDecoration = TextDecoration.Underline,
        modifier =
            modifier
                .pressDim(pressed)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(vertical = Tokens.Space.ghostY.dp),
    )
}
