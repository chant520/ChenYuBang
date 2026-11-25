package com.chenyubang.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.tokens.Tokens

private val OPTION_LETTERS = listOf("A", "B", "C", "D")

/** P25 成语自测主体。退出意图统一交给路由 / BackHandler，业务状态机不持有导航。 */
@Composable
fun P25SelfTestContent(
    state: SelfTestUiState,
    onPick: (String) -> Unit = {},
    onNext: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRetryRound: () -> Unit = {},
    onExit: () -> Unit = {},
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
        SelfTestTopBar(onExit)
        when (state) {
            SelfTestUiState.Loading -> CBar(CBarKind.Info, title = "正在准备自测", subtitle = "只读本机题库，不联网。")
            SelfTestUiState.EmptyScope ->
                CEmptyState(
                    title = "当前考试范围没有可自测的成语",
                    description = "请先调整考试范围，再回来开始自测。",
                    actionText = "返回我的",
                    onAction = onExit,
                )
            SelfTestUiState.InsufficientCandidates ->
                CEmptyState(
                    title = "可自测的候选项不足",
                    description = "全库至少需要 4 条词与释义都唯一的成语，当前不足以组成四选一。",
                    actionText = "返回我的",
                    onAction = onExit,
                )
            is SelfTestUiState.Error ->
                CBar(
                    kind = CBarKind.Error,
                    title = "自测题库读取失败，请重试",
                    subtitle = "本轮进度不会写入学习记录。",
                    actionText = "重试",
                    onAction = onRetry,
                )
            is SelfTestUiState.Question -> SelfTestQuestionContent(state, colors, onPick, onNext)
            is SelfTestUiState.Summary -> SelfTestSummaryContent(state.result, onRetryRound, onExit)
        }
    }
}

@Composable
private fun SelfTestTopBar(onExit: () -> Unit) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(Tokens.Space.hitMin.dp)
                .clickable(onClick = onExit)
                .semantics { contentDescription = "退出成语自测" },
            contentAlignment = Alignment.Center,
        ) {
            CChromeGlyph(CChromeIcon.Close, colors.text2, Modifier.size(Tokens.Icon.n21.dp))
        }
        Text(
            text = "成语自测",
            color = colors.head,
            fontSize = Tokens.Font.n15.sp,
            fontWeight = FontWeight.W700,
        )
    }
}

@Composable
private fun SelfTestQuestionContent(
    state: SelfTestUiState.Question,
    colors: Tokens.ColorSet,
    onPick: (String) -> Unit,
    onNext: () -> Unit,
) {
    Text(
        text = "第 ${state.questionNumber} 题 / 共 ${SelfTestEngine.QUESTION_COUNT} 题",
        color = colors.text2,
        fontSize = Tokens.Font.n12.sp,
        fontWeight = FontWeight.W600,
    )
    Text(text = state.current.type.label, color = colors.text3, fontSize = Tokens.Font.n11.sp)
    Text(
        text = state.current.prompt,
        color = colors.text,
        fontSize = Tokens.Font.n15.sp,
        fontWeight = FontWeight.W600,
        lineHeight = (Tokens.Font.n15 * 1.6f).sp,
    )
    val answer = state.answer
    state.current.options.forEachIndexed { index, option ->
        SelfTestOptionRow(
            letter = OPTION_LETTERS[index],
            text = option.text,
            state = optionState(answer, option.idiomId, state.current.targetId),
            onClick = { onPick(option.idiomId) },
        )
    }
    if (answer != null) {
        CBar(
            kind = if (answer.isCorrect) CBarKind.Info else CBarKind.Error,
            title = if (answer.isCorrect) "回答正确" else "回答错误",
            subtitle = if (answer.isCorrect) null else "正确答案：${correctOptionText(state.current)}",
        )
        CButton(text = state.nextLabel, onClick = onNext)
    }
}

private enum class SelfTestOptionStatus(val label: String?) {
    Idle(null),
    Correct("正确答案"),
    Wrong("你的选择"),
    Locked(null),
}

private fun optionState(
    answer: SelfTestAnswer?,
    optionId: String,
    targetId: String,
): SelfTestOptionStatus =
    when {
        answer == null -> SelfTestOptionStatus.Idle
        optionId == targetId -> SelfTestOptionStatus.Correct
        optionId == answer.selectedIdiomId && !answer.isCorrect -> SelfTestOptionStatus.Wrong
        else -> SelfTestOptionStatus.Locked
    }

@Composable
private fun SelfTestOptionRow(
    letter: String,
    text: String,
    state: SelfTestOptionStatus,
    onClick: () -> Unit,
) {
    SelfTestOptionVisual(
        letter = letter,
        text = text,
        state = state,
        onClick = onClick,
    )
}

@Composable
private fun SelfTestOptionVisual(
    letter: String,
    text: String,
    state: SelfTestOptionStatus,
    onClick: () -> Unit,
) {
    val enabled = state == SelfTestOptionStatus.Idle
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val shape = RoundedCornerShape(Tokens.Radius.r6.dp)
    val interaction = remember { MutableInteractionSource() }
    val palette = optionPalette(state, colors)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.Space.hitMin.dp)
            .background(palette.background, shape)
            .border(1.dp, palette.line, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "$letter，$text${state.label?.let { "，$it" }.orEmpty()}"
                role = Role.RadioButton
                selected = state == SelfTestOptionStatus.Wrong
                if (!enabled) disabled()
            }
            .padding(horizontal = Tokens.Space.lrowX.dp, vertical = Tokens.Space.lrowY.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.lsubTop.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = letter,
                color = if (enabled) colors.text3 else palette.foreground,
                fontSize = Tokens.Font.n13.sp,
                fontWeight = FontWeight.W600,
            )
            Text(
                text = text,
                color = palette.foreground,
                fontSize = Tokens.Font.n13.sp,
                lineHeight = (Tokens.Font.n13 * 1.6f).sp,
                modifier = Modifier.padding(start = Tokens.Space.lt1Gap.dp).weight(1f),
            )
        }
        if (state.label != null) {
            Text(
                text = state.label,
                color = palette.foreground,
                fontSize = Tokens.Font.n12.sp,
                fontWeight = FontWeight.W600,
            )
        }
    }
}

private data class SelfTestOptionPalette(
    val background: androidx.compose.ui.graphics.Color,
    val line: androidx.compose.ui.graphics.Color,
    val foreground: androidx.compose.ui.graphics.Color,
)

private fun optionPalette(
    state: SelfTestOptionStatus,
    colors: Tokens.ColorSet,
): SelfTestOptionPalette =
    when (state) {
        SelfTestOptionStatus.Correct -> SelfTestOptionPalette(colors.tintbg, colors.tintline, colors.tintfg)
        SelfTestOptionStatus.Wrong -> SelfTestOptionPalette(colors.errbg, colors.errline, colors.errfg)
        SelfTestOptionStatus.Idle -> SelfTestOptionPalette(colors.surface, colors.line, colors.text)
        SelfTestOptionStatus.Locked -> SelfTestOptionPalette(colors.surface, colors.line, colors.text3)
    }

@Composable
private fun SelfTestSummaryContent(
    summary: SelfTestSummary,
    onRetryRound: () -> Unit,
    onExit: () -> Unit,
) {
    Text(
        "本轮小结",
        color = if (isSystemInDarkTheme()) Tokens.Dark.head else Tokens.Light.head,
        fontSize = Tokens.Font.n20.sp,
        fontWeight = FontWeight.W700,
    )
    SummaryCount("题量", summary.questionCount)
    SummaryCount("正确数", summary.correctCount)
    SummaryCount("错误数", summary.wrongCount)
    CButton(text = "再测一轮", onClick = onRetryRound)
    CButton(text = "返回我的", onClick = onExit, variant = CButtonVariant.Ghost)
}

@Composable
private fun SummaryCount(
    label: String,
    value: Int,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.text2, fontSize = Tokens.Font.n13.sp)
        Text(value.toString(), color = colors.text, fontSize = Tokens.Font.n15.sp, fontWeight = FontWeight.W700)
    }
}

private fun correctOptionText(question: SelfTestQuestion): String =
    question.options.first { it.idiomId == question.targetId }.text
