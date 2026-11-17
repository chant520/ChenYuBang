package com.chenyubang.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

/**
 * 空态构件。一份标题 + 一句说明 + 可选动作，与 D 稿 44 屏空态保持同一结构，
 * 不要在页面里另写空态文案（文案唯一来源是信息架构 5.4）。
 */
@Composable
fun CEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Tokens.Space.pageX.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.ghostY.dp),
    ) {
        Text(
            text = title,
            color = c.text2,
            fontSize = Tokens.Font.n15.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            color = c.text3,
            fontSize = Tokens.Font.n12.sp,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            CButton(
                text = actionText,
                onClick = onAction,
                variant = CButtonVariant.Ghost,
                modifier = Modifier.padding(top = Tokens.Space.ghostY.dp),
            )
        }
    }
}
