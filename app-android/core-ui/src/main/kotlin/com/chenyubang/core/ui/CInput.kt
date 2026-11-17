package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

private const val ERROR_HINT_LINE_HEIGHT = 1.55f
private const val FOCUS_RING_WIDTH = 2
private const val ERROR_HINT_TOP = 4

private fun Modifier.focusRing(
    color: Color,
    radius: Dp,
): Modifier =
    drawBehind {
        val ring = FOCUS_RING_WIDTH.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-ring / 2f, -ring / 2f),
            size = Size(size.width + ring, size.height + ring),
            cornerRadius = CornerRadius(radius.toPx() + ring / 2f),
            style = Stroke(width = ring),
        )
    }

/** 输入框（规范 §5.3）：默认 / 聚焦 / 校验错误 / 禁用，三态几何一致不跳布局。 */
@Composable
fun CInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    error: Boolean = false,
    errorHint: String? = null,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Tokens.Radius.r5.dp)
    val background = if (enabled) c.inputbg else c.disbg
    val foreground =
        when {
            !enabled -> c.disfg
            error -> c.errfg
            else -> c.text
        }
    val border =
        when {
            !enabled -> c.disline
            error -> c.errline
            focused -> c.accent
            else -> c.line
        }
    val verticalPad = if (enabled) Tokens.Space.inputY else Tokens.Space.inputDisY
    val fontSize = if (enabled) Tokens.Font.n12.sp else Tokens.Font.n125.sp
    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = foreground, fontSize = fontSize),
            cursorBrush = SolidColor(c.accent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Space.inputMinH.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .then(
                        if (focused && enabled && !error) {
                            Modifier.focusRing(c.tintbg, Tokens.Radius.r5.dp)
                        } else {
                            Modifier
                        },
                    )
                    .background(background, shape)
                    .border(1.dp, border, shape)
                    .padding(horizontal = Tokens.Space.inputX.dp, vertical = verticalPad.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(text = placeholder, color = c.text3, fontSize = fontSize)
                    }
                    inner()
                }
            },
        )
        if (error && errorHint != null) {
            ErrorHint(text = errorHint, color = c.errfg)
        }
    }
}

@Composable
private fun ErrorHint(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        fontSize = Tokens.Font.n115.sp,
        lineHeight = (Tokens.Font.n115 * ERROR_HINT_LINE_HEIGHT).sp,
        modifier = Modifier.padding(top = ERROR_HINT_TOP.dp),
    )
}
