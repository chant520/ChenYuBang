package com.chenyubang.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

// 加载态 spinner 与文案间距：构件板 .btn.loading gap:9px
private const val LOADING_GAP = 9

// 顶部按钮图标-文案间距：构件板 .topbtn gap:6px
private const val TOP_CONTENT_GAP = 6
private const val TOP_ICON_STROKE = 1.7f
private val transparent = Color.Transparent

private class ButtonStyle(
    val background: Color,
    val foreground: Color,
    val border: Color?,
    val fontSize: TextUnit,
    val weight: FontWeight,
    val verticalPad: Int,
)

private fun buttonStyle(
    c: Tokens.ColorSet,
    variant: CButtonVariant,
    enabled: Boolean,
): ButtonStyle =
    when {
        !enabled && variant != CButtonVariant.Top ->
            ButtonStyle(c.disbg, c.disfg, c.disline, Tokens.Font.n135.sp, FontWeight.W700, Tokens.Space.dangerY)
        !enabled ->
            ButtonStyle(c.disbg, c.disfg, null, Tokens.Font.n115.sp, FontWeight.W700, Tokens.Space.topBtnY)
        variant == CButtonVariant.Primary ->
            ButtonStyle(c.btnbg, c.btnfg, c.btnline, Tokens.Font.n14.sp, FontWeight.W700, Tokens.Space.btnY)
        variant == CButtonVariant.DangerFilled ->
            ButtonStyle(c.errfill, c.errfillfg, c.errfill, Tokens.Font.n135.sp, FontWeight.W700, Tokens.Space.dangerY)
        variant == CButtonVariant.DangerOutline ->
            ButtonStyle(transparent, c.errfg, c.errline, Tokens.Font.n135.sp, FontWeight.W700, Tokens.Space.dangerY)
        variant == CButtonVariant.Ghost ->
            ButtonStyle(transparent, c.text2, c.line, Tokens.Font.n115.sp, FontWeight.Normal, Tokens.Space.ghostY)
        else ->
            ButtonStyle(c.btnbg, c.btnfg, null, Tokens.Font.n115.sp, FontWeight.W700, Tokens.Space.topBtnY)
    }

@Composable
private fun ButtonContent(
    text: String,
    loading: Boolean,
    isTop: Boolean,
    icon: CIconKind?,
    style: ButtonStyle,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(if (isTop && !loading) TOP_CONTENT_GAP.dp else LOADING_GAP.dp),
    ) {
        if (loading) {
            CSpinner(color = style.foreground)
        }
        // 加载态 spinner 替换顶部图标（构件板 .topbtn 加载态无 svg）。
        if (isTop && !loading) {
            CIcon(
                kind = icon ?: CIconKind.Plus,
                size = Tokens.Icon.n14.dp,
                stroke = TOP_ICON_STROKE,
                color = style.foreground,
            )
        }
        Text(
            text = text,
            color = style.foreground,
            fontSize = style.fontSize,
            fontWeight = style.weight,
            textAlign = TextAlign.Center,
        )
    }
}

/** 按钮（规范 §5.1 / §5.0）。块级变体通栏，顶部按钮自适应；并排等分由页面编排。 */
@Composable
fun CButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CButtonVariant = CButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: CIconKind? = null,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && !loading
    val isTop = variant == CButtonVariant.Top
    val style = buttonStyle(c, variant, enabled)
    val radius =
        when (variant) {
            CButtonVariant.Ghost, CButtonVariant.Top -> Tokens.Radius.r5
            else -> Tokens.Radius.r6
        }
    val shape = RoundedCornerShape(radius.dp)
    val horizontalPad = if (isTop) Tokens.Space.topBtnX else 0
    Box(
        modifier =
            modifier.then(
                if (isTop) Modifier.minimumInteractiveComponentSize() else Modifier.fillMaxWidth(),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                (if (isTop) Modifier else Modifier.fillMaxWidth())
                    .pressDim(pressed && active)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = active,
                        onClick = onClick,
                    )
                    .background(style.background, shape)
                    .then(
                        if (style.border != null) {
                            Modifier.border(BorderStroke(1.dp, style.border), shape)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = horizontalPad.dp, vertical = style.verticalPad.dp),
            contentAlignment = Alignment.Center,
        ) {
            ButtonContent(text = text, loading = loading, isTop = isTop, icon = icon, style = style)
        }
    }
}
