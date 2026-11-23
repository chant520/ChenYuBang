package com.chenyubang.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.tokens.Tokens

/**
 * P12 复习小结（全屏浮层）：承载验收第 13 条的题量 / 正确数 / 涉及成语列表。
 * 主按钮按来源回各自栈底并清空队列状态；「再复习一轮」范围与本轮相同，不把单成语扩大到全局。
 */
@Composable
fun P12ReviewSummaryContent(
    summary: ReviewSummary,
    onBack: () -> Unit,
    onBackToSource: () -> Unit,
    onAgain: () -> Unit,
    onOpenIdiom: (String) -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.page)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        summaryTopBar(onBack, colors)
        Text(text = summary.scopeLine, color = colors.text3, fontSize = Tokens.Font.n12.sp)
        if (summary.questionCount == 0) {
            // E17 兜底态：正常路径不可达（队列为空进不了 P11）。
            summaryEmpty(onBackToSource)
        } else {
            summaryResultCard(summary, colors)
            summaryIdiomsCard(summary, colors, onOpenIdiom)
            CButton(
                text = if (summary.scopeIdiomId == null) "回错题本" else "回成语详情",
                onClick = onBackToSource,
            )
            CButton(text = "再复习一轮", onClick = onAgain, variant = CButtonVariant.Ghost)
        }
    }
}

@Composable
private fun summaryTopBar(
    onBack: () -> Unit,
    colors: Tokens.ColorSet,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(Tokens.Space.hitMin.dp)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "返回复习队列" },
            contentAlignment = Alignment.Center,
        ) {
            CChromeGlyph(CChromeIcon.Back, colors.text2, Modifier.size(Tokens.Icon.n21.dp))
        }
        Text(
            text = "复习小结",
            color = colors.head,
            fontSize = Tokens.Font.n15.sp,
            fontWeight = FontWeight.W700,
        )
    }
}

@Composable
private fun summaryResultCard(
    summary: ReviewSummary,
    colors: Tokens.ColorSet,
) {
    CCard {
        CLabel(text = "本轮结果")
        Text(
            text = "题量 ${summary.questionCount} 道",
            color = colors.text,
            fontSize = Tokens.Font.n14.sp,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
        Text(
            text = "答对 ${summary.correctCount} 道",
            color = colors.text2,
            fontSize = Tokens.Font.n13.sp,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
        Text(
            text = "答错 ${summary.wrongCount} 道",
            color = colors.text2,
            fontSize = Tokens.Font.n13.sp,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
    }
}

@Composable
private fun summaryIdiomsCard(
    summary: ReviewSummary,
    colors: Tokens.ColorSet,
    onOpenIdiom: (String) -> Unit,
) {
    CCard {
        CLabel(text = "涉及成语")
        if (summary.idioms.isEmpty()) {
            Text(
                text = "本轮没有涉及成语",
                color = colors.text3,
                fontSize = Tokens.Font.n12.sp,
                modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
            )
        } else {
            summary.idioms.forEach { idiom ->
                Text(
                    text = idiom.word,
                    color = colors.tintfg,
                    fontSize = Tokens.Font.n13.sp,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenIdiom(idiom.id) }
                            .padding(top = Tokens.Space.lmetaTop.dp),
                )
            }
        }
    }
}

@Composable
private fun summaryEmpty(onBackToSource: () -> Unit) {
    CEmptyState(
        title = "这一轮没有题目",
        description = "这一轮没有可复习的错题，可以回到来源页。",
        actionText = "回上一页",
        onAction = onBackToSource,
    )
}
