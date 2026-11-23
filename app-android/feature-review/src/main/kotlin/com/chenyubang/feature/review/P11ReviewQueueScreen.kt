package com.chenyubang.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.tokens.Tokens

private val LETTERS = listOf("A", "B", "C", "D")

private data class ReviewOptionView(
    val index: Int,
    val text: String?,
    val selected: Boolean,
    val enabled: Boolean,
)

/**
 * P11 复习队列（全屏浮层，IA v1.4 决策 E）：全局与单成语共用本页，页内一行本轮范围。
 * 选完立即判分，判分区逐字沿用 P10 重做串（含首次/本次字母）并附「本题未记录正确答案」；
 * 不显示具体正确答案、不渲染解析。退出与空态的返回落点由宿主按来源栈底接线。
 */
@Composable
fun P11ReviewQueueContent(
    state: ReviewUiState,
    onPick: (Int) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onBackToSource: () -> Unit,
    onRetry: () -> Unit = {},
    onFinish: () -> Unit = {},
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
        reviewQueueTopBar(state, onClose, colors)
        when {
            state.loading ->
                CBar(CBarKind.Info, title = "正在读取复习队列", subtitle = "只读本机错题，不联网。")
            state.failed ->
                CBar(
                    CBarKind.Warn,
                    title = "复习队列读取失败，请重试",
                    subtitle = "再进一次本页会重新读取。",
                    actionText = "重试",
                    onAction = onRetry,
                )
            state.total == 0 -> reviewQueueEmpty(state, onBackToSource)
            else -> reviewQuestionBody(state, onPick, onNext, onFinish, colors)
        }
    }
}

@Composable
private fun reviewQueueTopBar(
    state: ReviewUiState,
    onClose: () -> Unit,
    colors: Tokens.ColorSet,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(Tokens.Space.hitMin.dp)
                .clickable(onClick = onClose)
                .semantics { contentDescription = "退出复习" },
            contentAlignment = Alignment.Center,
        ) {
            CChromeGlyph(CChromeIcon.Close, colors.text2, Modifier.size(Tokens.Icon.n21.dp))
        }
        Text(
            text = if (state.total == 0) "复习队列" else "第 ${state.index + 1} 题 / 共 ${state.total} 题",
            color = colors.head,
            fontSize = Tokens.Font.n15.sp,
            fontWeight = FontWeight.W700,
        )
    }
    Text(text = state.scopeLine, color = colors.text3, fontSize = Tokens.Font.n12.sp)
}

@Composable
private fun reviewQueueEmpty(
    state: ReviewUiState,
    onBackToSource: () -> Unit,
) {
    val single = state.scopeIdiomId != null
    CEmptyState(
        title = if (single) "这个成语下没有待复习的错题" else "今天没有待复习的错题",
        description = if (single) "可以回成语详情看它的真题" else "错题本里标记为做错的题会出现在这里",
        actionText = if (single) "回成语详情" else "回错题本",
        onAction = onBackToSource,
    )
}

@Composable
private fun reviewQuestionBody(
    state: ReviewUiState,
    onPick: (Int) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    colors: Tokens.ColorSet,
) {
    val question = state.current ?: return
    Text(
        question.stem,
        color = colors.text,
        fontSize = Tokens.Font.n15.sp,
        fontWeight = FontWeight.W600,
    )
    question.options.forEachIndexed { index, text ->
        reviewOptionRow(
            view =
                ReviewOptionView(
                    index = index,
                    text = text,
                    selected = state.currentAnswer?.picked == index,
                    enabled = state.currentAnswer == null,
                ),
            onClick = { onPick(index) },
        )
    }
    val verdict = state.currentAnswer?.verdict
    if (verdict != null) {
        Text(
            text = verdict.message,
            color = if (verdict.comparable) colors.tintfg else colors.text3,
            fontSize = Tokens.Font.n13.sp,
            fontWeight = FontWeight.W600,
        )
        if (verdict.comparable) {
            Text(text = "本题未记录正确答案", color = colors.text3, fontSize = Tokens.Font.n12.sp)
        }
        if (state.isLast) {
            CButton(text = "查看小结", onClick = onFinish)
        } else {
            CButton(text = "下一题", onClick = onNext)
        }
    }
}

@Composable
private fun reviewOptionRow(
    view: ReviewOptionView,
    onClick: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val shape = RoundedCornerShape(Tokens.Radius.r6.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (view.selected) colors.tintbg else colors.surface, shape)
            .border(1.dp, if (view.selected) colors.tintline else colors.line, shape)
            .clickable(enabled = view.enabled, onClick = onClick)
            .padding(horizontal = Tokens.Space.lrowX.dp, vertical = Tokens.Space.lrowY.dp),
    ) {
        Text(
            text = "${LETTERS.getOrNull(view.index).orEmpty()}. ${view.text?.takeIf { it.isNotBlank() } ?: "暂无"}",
            color = if (view.selected) colors.tintfg else colors.text2,
            fontSize = Tokens.Font.n135.sp,
        )
    }
}
