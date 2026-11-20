package com.chenyubang.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CIcon
import com.chenyubang.core.ui.CIconKind
import com.chenyubang.core.ui.tokens.Tokens

/**
 * P21 保存成功（信息架构 v1.4 P21 / 验收第 11 条）：无返回，给出三个去处。
 * 多条归属时「去成语详情」进 checkedIdioms 第一条（由路由取第一条 id）。
 */
@Composable
fun P21DoneScreen(
    idioms: List<IdiomRef>,
    onOpenIdiom: () -> Unit,
    onOpenMistakes: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        modifier
            .fillMaxSize()
            .background(colors.page)
            .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(SUCCESS_ICON_SIZE.dp)
                    .background(colors.tintbg, CircleShape)
                    .semantics { contentDescription = "保存成功" },
                contentAlignment = Alignment.Center,
            ) {
                CIcon(
                    kind = CIconKind.Check,
                    size = Tokens.Icon.n26.dp,
                    stroke = SUCCESS_ICON_STROKE,
                    color = colors.tintfg,
                )
            }
            Spacer(Modifier.height(Tokens.Space.block.dp))
            Text(
                "已存入我的题库",
                color = colors.head,
                fontSize = Tokens.Font.n20.sp,
                fontWeight = FontWeight.W700,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Tokens.Space.lsubTop.dp))
            Text(
                attribution(idioms),
                color = colors.text2,
                fontSize = Tokens.Font.n13.sp,
                textAlign = TextAlign.Center,
            )
        }
        CButton("去成语详情", onOpenIdiom, Modifier.fillMaxWidth())
        CButton("去错题本", onOpenMistakes, Modifier.fillMaxWidth(), CButtonVariant.Ghost)
        CButton("继续录下一题", onContinue, Modifier.fillMaxWidth(), CButtonVariant.Ghost)
        Text(
            "只存在本机，不会上传",
            color = colors.text3,
            fontSize = Tokens.Font.n11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = Tokens.Space.fabBottom.dp),
        )
    }
}

/** 一条直显；多条按 checkedIdioms 顺序以「、」连接。 */
internal fun attribution(idioms: List<IdiomRef>): String =
    if (idioms.isEmpty()) {
        ""
    } else {
        "这道题归到了 ${idioms.joinToString("、") { it.word }} 下"
    }

private const val SUCCESS_ICON_SIZE = 64
private const val SUCCESS_ICON_STROKE = 2.4f
