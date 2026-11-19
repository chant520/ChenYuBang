package com.chenyubang.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

// 字段标签字距：全页稿 v2 `.lbl` letter-spacing:.1em
private const val LABEL_LETTER_SPACING = 0.1f

/** 字段标签（全页稿 v2 `.lbl`）：11px/700、字距 .1em、`--text3`。 */
@Composable
fun CLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Text(
        text = text,
        color = c.text3,
        fontSize = Tokens.Font.n11.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = LABEL_LETTER_SPACING.em,
        modifier = modifier,
    )
}
