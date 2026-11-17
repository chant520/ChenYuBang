package com.chenyubang.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

@Composable
fun CButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CButtonVariant = CButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val background =
        when {
            !enabled -> c.disbg
            variant == CButtonVariant.Danger -> c.errfill
            variant == CButtonVariant.Ghost -> Color.Transparent
            else -> c.btnbg
        }
    val foreground =
        when {
            !enabled -> c.disfg
            variant == CButtonVariant.Danger -> c.errfillfg
            variant == CButtonVariant.Ghost -> c.tintfg
            else -> c.btnfg
        }
    val border =
        when {
            !enabled -> BorderStroke(1.dp, c.disline)
            variant == CButtonVariant.Ghost -> BorderStroke(1.dp, c.line)
            else -> null
        }

    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = Tokens.Space.hitMin.dp),
        shape = RoundedCornerShape(Tokens.Radius.r6.dp),
        border = border,
        contentPadding =
            PaddingValues(
                vertical = Tokens.Space.btnY.dp,
                horizontal = Tokens.Space.pageX.dp,
            ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = background,
                contentColor = foreground,
                disabledContainerColor = background,
                disabledContentColor = foreground,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.tagY.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Tokens.Icon.n18.dp),
                    color = foreground,
                    strokeWidth = 1.5.dp,
                )
            }
            Text(text = text, fontSize = Tokens.Font.n15.sp)
        }
    }
}
