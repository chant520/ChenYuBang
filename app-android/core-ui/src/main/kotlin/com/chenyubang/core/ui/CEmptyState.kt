package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
 *
 * [icon] 为可选插画位（信息架构 §5.4 行 547 唯一写明插画位的空态），几何照高保真 D 稿 `.eic`：
 * 直径 64、底 surface2、描边 1px line2、内 26 图标描边 1.5 取 text3、与标题间距 16。
 * 默认不渲染（null），其余页零影响。
 */
@Composable
fun CEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    icon: CChromeIcon? = null,
) {
    val c = LocalColorSet.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Tokens.Space.pageX.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.ghostY.dp),
    ) {
        if (icon != null) {
            // 插画位（D 稿 .eic）：直径 64、底 surface2、描边 1px line2、内 26 图标取 text3。
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .background(c.surface2, CircleShape)
                        .border(1.dp, c.line2, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CChromeGlyph(
                    kind = icon,
                    color = c.text3,
                    modifier = Modifier.size(Tokens.Icon.n26.dp),
                )
            }
        }
        Text(
            text = title,
            color = c.text2,
            fontSize = Tokens.FontRole.sectionTitle.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            color = c.text3,
            fontSize = Tokens.FontRole.supporting.sp,
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
