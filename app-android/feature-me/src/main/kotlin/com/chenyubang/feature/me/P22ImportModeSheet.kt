package com.chenyubang.feature.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.data.ImportMode
import com.chenyubang.core.ui.CBadge
import com.chenyubang.core.ui.CBadgeKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.COverlay
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.tokens.Tokens

/**
 * P22 导入方式（底部 sheet，路由 sheet/import-mode）。逐字文案见视觉契约第 2.1 节。
 * 覆盖用红徽标「会丢现有数据」、合并用绿标签「保留本机」；取消 / 遮罩不改数据。
 */
@Composable
fun P22ImportModeSheet(
    vm: P15BackupViewModel,
    onFinished: (Boolean) -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light

    COverlay(
        title = "读入这份备份，用哪种方式",
        centered = false,
        onCancel = {
            vm.discardImport()
            onFinished(false)
        },
    ) {
        Text(
            text = "两种方式结果不同，选之前看清楚。",
            color = colors.text2,
            fontSize = Tokens.Font.n12.sp,
            lineHeight = (Tokens.Font.n12 * DESC_LINE_HEIGHT).sp,
        )

        CCard(
            soft = true,
            modifier = Modifier.clickable { vm.confirmImport(ImportMode.OVERWRITE, onFinished) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "覆盖",
                    color = colors.head,
                    fontSize = Tokens.Font.n13.sp,
                    fontWeight = FontWeight.W700,
                )
                Spacer(Modifier.size(Tokens.Space.chkGap.dp))
                CBadge("会丢现有数据", CBadgeKind.Error)
            }
            Spacer(Modifier.size(Tokens.Space.lsubTop.dp))
            Text(
                text = "用备份文件替换本机现有记录。本机现在的错题、收藏、学习状态都会被清掉，之后无法找回。",
                color = colors.text2,
                fontSize = Tokens.Font.n13.sp,
                lineHeight = (Tokens.Font.n13 * DESC_LINE_HEIGHT).sp,
            )
        }

        CCard(
            soft = true,
            modifier = Modifier.clickable { vm.confirmImport(ImportMode.MERGE, onFinished) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "合并",
                    color = colors.head,
                    fontSize = Tokens.Font.n13.sp,
                    fontWeight = FontWeight.W700,
                )
                Spacer(Modifier.size(Tokens.Space.chkGap.dp))
                CTag("保留本机")
            }
            Spacer(Modifier.size(Tokens.Space.lsubTop.dp))
            Text(
                text = "把文件里的记录并入本机，同一道题以本机记录为准，重复的不会叠加。",
                color = colors.text2,
                fontSize = Tokens.Font.n13.sp,
                lineHeight = (Tokens.Font.n13 * DESC_LINE_HEIGHT).sp,
            )
        }

        CButton(
            text = "取消",
            onClick = {
                vm.discardImport()
                onFinished(false)
            },
            variant = CButtonVariant.Ghost,
        )
    }
}

private const val DESC_LINE_HEIGHT = 1.7f
