package com.chenyubang.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CCheckRow
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CInput
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CLink
import com.chenyubang.core.ui.CSegmentedControl
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.tokens.Tokens

internal val EXAM_OPTIONS = listOf("国考", "省考", "事业编", "教师编")
internal const val TEACHER_INDEX = 3
internal val ANSWER_OPTIONS = listOf("A", "B", "C", "D")

@Composable
internal fun P20FormReviewCard(
    text: String,
    onEdit: () -> Unit,
    colors: Tokens.ColorSet,
) {
    CCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CLabel("识别文字回看", Modifier.weight(1f))
            CLink("改文字", onEdit)
        }
        Spacer(Modifier.height(Tokens.Space.lt1Gap.dp))
        Text(text = text.ifEmpty { P20Matcher.NO_TEXT }, color = colors.text, fontSize = Tokens.Font.n13.sp)
    }
}

@Composable
internal fun CandidateCard(
    state: P20CaptureState,
    actions: P20CaptureActions,
    colors: Tokens.ColorSet,
) {
    CCard {
        CLabel("系统匹配的成语候选")
        Spacer(Modifier.height(Tokens.Space.lmetaTop.dp))
        when {
            state.loading -> Text("正在匹配…", color = colors.text3, fontSize = Tokens.Font.n12.sp)
            state.empty ->
                CEmptyState(
                    title = P20Matcher.EMPTY_TITLE,
                    description = "",
                    actionText = P20Matcher.MANUAL_ACTION,
                    onAction = actions.onOpenManual,
                )
            else -> CandidateRows(state, actions, colors)
        }
        if (state.showsCheckedChips) {
            Spacer(Modifier.height(Tokens.Space.lmetaTop.dp))
            CLabel("已选归属")
            Row(
                Modifier.fillMaxWidth().padding(top = Tokens.Space.lsubTop.dp),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
            ) {
                state.checked.forEach { ref ->
                    Box(
                        Modifier
                            .clickable { actions.onSelect(ref.id) }
                            .semantics { contentDescription = "取消${ref.word}" },
                    ) {
                        CTag(ref.word)
                    }
                }
            }
        }
        Spacer(Modifier.height(Tokens.Space.ghostY.dp))
        CButton(P20Matcher.MANUAL_ACTION, actions.onOpenManual, Modifier.fillMaxWidth(), CButtonVariant.Ghost)
    }
}

@Composable
internal fun CandidateRows(
    state: P20CaptureState,
    actions: P20CaptureActions,
    colors: Tokens.ColorSet,
) {
    state.candidates.forEachIndexed { index, _ -> CandidateRow(state, index, actions, colors) }
    Spacer(Modifier.height(Tokens.Space.ghostY.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp)) {
        CButton("换一组", actions.onCycle, Modifier.weight(1f), CButtonVariant.Ghost, state.canCycle)
        CButton("加一个", actions.onAdd, Modifier.weight(1f), CButtonVariant.Ghost, state.canAdd)
        CButton(
            text = if (state.deleteMode) "完成" else "删一个",
            onClick = actions.onToggleDelete,
            modifier = Modifier.weight(1f),
            variant = CButtonVariant.Ghost,
            enabled = state.candidates.isNotEmpty(),
        )
    }
    if (!state.canSave && !state.loading) {
        Text(
            P20Matcher.AT_LEAST_ONE,
            color = colors.text3,
            fontSize = Tokens.Font.n11.sp,
            modifier = Modifier.padding(top = Tokens.Space.ghostY.dp),
        )
    }
}

@Composable
internal fun CandidateRow(
    state: P20CaptureState,
    index: Int,
    actions: P20CaptureActions,
    colors: Tokens.ColorSet,
) {
    val candidate = state.candidates[index]
    Row(verticalAlignment = Alignment.CenterVertically) {
        CCheckRow(
            label = candidate.word,
            checked = candidate.id in state.checkedIds,
            onCheckedChange = { actions.onSelect(candidate.id) },
            modifier = Modifier.weight(1f),
            enabled = !state.deleteMode,
            trailing = candidate.tier.label,
            showDivider = index != state.candidates.lastIndex,
        )
        if (state.deleteMode) {
            Box(
                Modifier
                    .size(Tokens.Space.hitMin.dp)
                    .clickable(onClick = { actions.onDelete(candidate.id) })
                    .semantics { contentDescription = "删除${candidate.word}" },
                contentAlignment = Alignment.Center,
            ) {
                CChromeGlyph(CChromeIcon.Close, colors.text2, Modifier.size(Tokens.Icon.n18.dp))
            }
        }
    }
}

@Composable
internal fun FormFieldsCard(
    state: P20CaptureState,
    actions: P20CaptureActions,
    colors: Tokens.ColorSet,
) {
    AnswerAndTagsCard(state, actions)
    MistakeToggleCard(state, actions, colors)
}

@Composable
private fun AnswerAndTagsCard(
    state: P20CaptureState,
    actions: P20CaptureActions,
) {
    val form = state.form
    CCard {
        CLabel("我的答案（可选）")
        CSegmentedControl(
            items = ANSWER_OPTIONS,
            selectedIndex = ANSWER_OPTIONS.indexOf(form.answer),
            onSelect = { actions.onAnswer(ANSWER_OPTIONS[it]) },
        )
        CLabel("我的错因", Modifier.padding(top = Tokens.Space.lmetaTop.dp))
        CInput(
            value = form.reason,
            onValueChange = actions.onReason,
            placeholder = "仅标错后可填",
            enabled = form.isMistake,
        )
        CLabel("自定义标签", Modifier.padding(top = Tokens.Space.lmetaTop.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CInput(
                value = form.tagDraft,
                onValueChange = actions.onTagDraft,
                placeholder = "1–8 字，标错后可加",
                enabled = form.isMistake,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Tokens.Space.lmetaGap.dp))
            CButton(
                "加标签",
                onClick = { actions.onAddTag() },
                variant = CButtonVariant.Ghost,
                enabled = form.isMistake,
            )
        }
        if (form.tags.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = Tokens.Space.lsubTop.dp),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
            ) {
                form.tags.forEach { tag ->
                    Box(
                        Modifier
                            .clickable { actions.onRemoveTag(tag) }
                            .semantics { contentDescription = "删除标签$tag" },
                    ) {
                        CTag(tag)
                    }
                }
            }
        }
    }
}

@Composable
private fun MistakeToggleCard(
    state: P20CaptureState,
    actions: P20CaptureActions,
    colors: Tokens.ColorSet,
) {
    val form = state.form
    CCard {
        CCheckRow(
            label = "我这道题做错了，标记为错题",
            checked = form.isMistake,
            onCheckedChange = { actions.onToggleMistake() },
        )
        Text(
            "仅勾选后才进错题本；不勾只存入我的题库。",
            color = colors.text3,
            fontSize = Tokens.Font.n11.sp,
            modifier = Modifier.padding(top = Tokens.Space.ghostY.dp),
        )
    }
}

@Composable
internal fun SourceCard(
    state: P20CaptureState,
    actions: P20CaptureActions,
    colors: Tokens.ColorSet,
) {
    CCard {
        CLabel("考试类型（可空）")
        CSegmentedControl(
            items = EXAM_OPTIONS,
            selectedIndex = EXAM_OPTIONS.indexOf(state.form.examType),
            onSelect = { index ->
                if (index == TEACHER_INDEX) return@CSegmentedControl
                val value = EXAM_OPTIONS[index]
                actions.onExamType(if (state.form.examType == value) null else value)
            },
            disabledIndices = setOf(TEACHER_INDEX),
        )
        CLabel("年份（2000–2100，可空）", Modifier.padding(top = Tokens.Space.lmetaTop.dp))
        CInput(
            value = state.form.yearText,
            onValueChange = actions.onYear,
            placeholder = "如 2025",
            error = state.form.yearInvalid,
            errorHint = "年份需在 2000–2100",
        )
        Text(
            "教师编题库到位后开放；来源信息只存在本机。",
            color = colors.text3,
            fontSize = Tokens.Font.n11.sp,
            modifier = Modifier.padding(top = Tokens.Space.ghostY.dp),
        )
    }
}

@Composable
internal fun FormHeader(
    onBack: () -> Unit,
    colors: Tokens.ColorSet,
) {
    Column(Modifier.fillMaxWidth().background(colors.page)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = Tokens.Space.pageX.dp,
                    end = Tokens.Space.pageX.dp,
                    top = Tokens.Space.cropHeadTop.dp,
                    bottom = Tokens.Space.cropHeadBottom.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(Tokens.Space.hitMin.dp)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "返回识别结果" },
                contentAlignment = Alignment.Center,
            ) {
                CChromeGlyph(CChromeIcon.Back, colors.text2, Modifier.size(Tokens.Icon.n21.dp))
            }
            Spacer(Modifier.width(Tokens.Space.camTopGap.dp))
            Text(
                "匹配与修正",
                color = colors.head,
                fontSize = Tokens.Font.n15.sp,
                fontWeight = FontWeight.W700,
                modifier = Modifier.weight(1f),
            )
            Text("第 4 步 / 共 5 步", color = colors.text3, fontSize = Tokens.Font.n11.sp)
        }
        Box(Modifier.fillMaxWidth().height(Tokens.Space.cropEdge.dp).background(colors.line))
    }
}

@Composable
internal fun P20FormManualPickerDialog(
    colors: Tokens.ColorSet,
    onDismiss: () -> Unit,
    manualPicker: @Composable (onPick: (String) -> Unit, onDismiss: () -> Unit) -> Unit,
    onPick: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(colors.page)) {
            manualPicker(onPick, onDismiss)
        }
    }
}
