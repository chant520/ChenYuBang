package com.chenyubang.feature.mistake

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.CBadge
import com.chenyubang.core.ui.CBadgeKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CLink
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.CTagStyle
import com.chenyubang.core.ui.tokens.Tokens

private val letters = listOf("A", "B", "C", "D")
private const val DISABLED_ALPHA = 0.4f

@Composable
internal fun P10QuestionCard(
    detail: P10UserExampleDetail,
    state: P10UserExampleUiState,
    actions: P10UserExampleActions,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "题干",
                color = c.text3,
                fontSize = Tokens.Font.n11.sp,
                fontWeight = FontWeight.W700,
                modifier = Modifier.weight(1f),
            )
            if (state.isMistake) CBadge("错题", CBadgeKind.Error)
        }
        Text(
            detail.stem.ifEmpty { P10Logic.PLACEHOLDER },
            color = c.text,
            fontSize = Tokens.Font.n13.sp,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
        detail.options.forEachIndexed { index, value ->
            val selected = state.rerolling && state.picked == index
            Text(
                "${letters[index]}. ${P10Logic.optionText(value)}",
                color = c.text2,
                fontSize = Tokens.Font.n13.sp,
                modifier =
                    Modifier
                        .padding(top = Tokens.Space.lmetaTop.dp)
                        .background(if (selected) c.tintbg else Color.Transparent)
                        .clickable(enabled = state.rerolling) { actions.onPickOption(index) }
                        .padding(horizontal = Tokens.Space.lmetaGap.dp, vertical = Tokens.Space.lmetaTop.dp),
            )
        }
    }
}

@Composable
internal fun P10MaskRow(
    state: P10UserExampleUiState,
    actions: P10UserExampleActions,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CCard {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                if (state.answerHidden) "答案已遮住" else "答案已显示",
                color = c.text2,
                fontSize = Tokens.Font.n12.sp,
                modifier = Modifier.weight(1f),
            )
            CLink(
                text = if (state.answerHidden) "显示答案" else "隐藏答案",
                onClick = { if (state.canToggleMask) actions.onToggleAnswer() },
                modifier =
                    Modifier
                        .alpha(if (state.canToggleMask) 1f else DISABLED_ALPHA)
                        .semantics {
                            contentDescription = if (state.answerHidden) "显示我的答案" else "隐藏我的答案"
                        },
            )
        }
    }
}

@Composable
internal fun P10RerollActions(
    state: P10UserExampleUiState,
    actions: P10UserExampleActions,
) {
    if (state.rerolling) {
        if (state.checked) P10ResultCard(state)
        Row {
            CButton("对答案", actions.onCheck, Modifier.weight(1f), enabled = state.picked != null)
            CButton("退出重做", actions.onExitReroll, Modifier.weight(1f), variant = CButtonVariant.Ghost)
        }
    } else {
        CButton("重做这道题", actions.onStartReroll)
    }
}

@Composable
private fun P10ResultCard(state: P10UserExampleUiState) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CCard {
        CLabel("本次作答")
        Text(
            P10Logic.rerollResult(state.firstPick, state.picked),
            color = c.text,
            fontSize = Tokens.Font.n13.sp,
            fontWeight = FontWeight.W600,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
        Text(
            P10Logic.REROLL_NOTE,
            color = c.text3,
            fontSize = Tokens.Font.n11.sp,
            modifier = Modifier.padding(top = Tokens.Space.lmetaTop.dp),
        )
    }
}

@Composable
internal fun P10SourceCard(detail: P10UserExampleDetail) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CCard {
        CLabel("出处")
        Text(
            P10Logic.sourceLine(detail.examType, detail.year),
            color = c.text2,
            fontSize = Tokens.Font.n13.sp,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun P10IdiomCard(
    idioms: List<P10IdiomRef>,
    onOpenIdiom: (String) -> Unit,
) {
    CCard {
        CLabel("关联成语")
        if (idioms.isEmpty()) {
            val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
            Text("暂未关联成语", color = c.text2, fontSize = Tokens.Font.n13.sp)
        } else {
            FlowRow(Modifier.padding(top = Tokens.Space.lmetaTop.dp)) {
                idioms.forEach { idiom ->
                    CTag(
                        idiom.word,
                        Modifier.clickable { onOpenIdiom(idiom.id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun P10AnswerCard(state: P10UserExampleUiState) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val detail = state.detail ?: return
    CCard(soft = true) {
        CLabel("我的答案与错因")
        Text(
            "我的答案：${P10Logic.answerText(detail.myAnswer, state.answerHidden)}",
            color = c.text2,
            fontSize = Tokens.Font.n13.sp,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
        if (state.isMistake && !detail.reason.isNullOrBlank()) {
            Text(
                "错因：${detail.reason}",
                color = c.text2,
                fontSize = Tokens.Font.n13.sp,
                modifier = Modifier.padding(top = Tokens.Space.lmetaTop.dp),
            )
        }
        if (state.isMistake && detail.tagNames.isNotEmpty()) {
            Text(
                "标签：${detail.tagNames.joinToString("、")}",
                color = c.text2,
                fontSize = Tokens.Font.n13.sp,
                modifier = Modifier.padding(top = Tokens.Space.lmetaTop.dp),
            )
        }
    }
}

/**
 * 区块 ⑤–⑩ 公共渲染：页面与「按自然高度单帧」快照共用同一套构件，避免两处渲染漂移。
 * 门控全部取 state 派生量（showsBottomDeleteRow / isMistake），Composable 内不再另行判断口径。
 */
@Composable
internal fun P10UserExampleBlocks(
    state: P10UserExampleUiState,
    actions: P10UserExampleActions,
) {
    if (state.isMistake) {
        P10MaskRow(state, actions)
        P10RerollActions(state, actions)
    }
    val detail = state.detail ?: return
    P10SourceCard(detail)
    P10IdiomCard(detail.idioms, actions.onOpenIdiom)
    P10AnswerCard(state)
    CTag("掌握状态切换（预留）", style = CTagStyle.Ghost)
    if (state.showsBottomDeleteRow) {
        CButton(
            "删除这道错题",
            {},
            modifier = Modifier.fillMaxWidth(),
            variant = CButtonVariant.DangerOutline,
            enabled = false,
        )
        val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
        Text(
            "删除会同时从错题本与对应成语详情里移除，需二次确认。",
            color = c.text3,
            fontSize = Tokens.Font.n11.sp,
        )
    }
}
