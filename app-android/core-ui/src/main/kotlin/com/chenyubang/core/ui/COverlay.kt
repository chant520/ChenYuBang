package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chenyubang.core.ui.tokens.Tokens

/** 自绘 sheet/dialog：遮罩点击及系统返回均调用同一个取消回调，不执行数据动作。 */
@Composable
fun COverlay(
    title: String,
    centered: Boolean,
    onCancel: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalColorSet.current
    val shape =
        if (centered) {
            RoundedCornerShape(Tokens.Radius.r6.dp)
        } else {
            RoundedCornerShape(topStart = Tokens.Radius.r6.dp, topEnd = Tokens.Radius.r6.dp)
        }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(colors.modal).clickable(onClick = onCancel),
            contentAlignment = if (centered) Alignment.Center else Alignment.BottomCenter,
        ) {
            Column(
                (if (centered) Modifier.widthIn(max = Tokens.Space.modalWidth.dp) else Modifier.fillMaxWidth())
                    .clickable(enabled = true, onClick = {})
                    .background(colors.surface, shape).border(1.dp, colors.line, shape)
                    .padding(
                        start = (if (centered) Tokens.Space.contentTop else Tokens.Space.pageX).dp,
                        end = (if (centered) Tokens.Space.contentTop else Tokens.Space.pageX).dp,
                        top = Tokens.Space.contentTop.dp,
                        bottom = (if (centered) Tokens.Space.contentTop else Tokens.Space.pageX).dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
            ) {
                Text(
                    title,
                    color = colors.head,
                    fontSize = Tokens.FontRole.sectionTitle.sp,
                    fontWeight = FontWeight.Bold,
                )
                content()
            }
        }
    }
}
