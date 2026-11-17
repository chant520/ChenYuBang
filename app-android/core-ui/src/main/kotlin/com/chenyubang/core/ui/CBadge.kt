package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

/** 徽标（规范 §5.6）：静态状态标识。 */
@Composable
fun CBadge(
    text: String,
    kind: CBadgeKind,
    modifier: Modifier = Modifier,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val shape = RoundedCornerShape(Tokens.Radius.r3.dp)
    val background =
        when (kind) {
            CBadgeKind.Warn -> c.warnbg
            CBadgeKind.Error -> c.errbg
            CBadgeKind.Info -> c.tintbg
            CBadgeKind.Neutral -> c.disbg
        }
    val foreground =
        when (kind) {
            CBadgeKind.Warn -> c.warnfg
            CBadgeKind.Error -> c.errfg
            CBadgeKind.Info -> c.tintfg
            CBadgeKind.Neutral -> c.disfg
        }
    val line =
        when (kind) {
            CBadgeKind.Warn -> c.warnline
            CBadgeKind.Error -> c.errline
            CBadgeKind.Info -> c.tintline
            CBadgeKind.Neutral -> c.disline
        }
    Box(
        modifier =
            modifier
                .background(background, shape)
                .border(1.dp, line, shape)
                .padding(horizontal = Tokens.Space.badgeX.dp, vertical = Tokens.Space.badgeY.dp),
    ) {
        Text(text = text, color = foreground, fontSize = Tokens.Font.n10.sp)
    }
}
