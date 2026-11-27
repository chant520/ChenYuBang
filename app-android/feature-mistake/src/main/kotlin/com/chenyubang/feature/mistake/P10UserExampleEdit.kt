package com.chenyubang.feature.mistake

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CInput
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.tokens.Tokens

private val OPTION_LABELS = listOf("选项 A", "选项 B", "选项 C", "选项 D")

/** P10 编辑态表单：只渲染可改字段；原图由调用方只读渲染，错因/标签整段仅标错题出现。 */
@Composable
internal fun P10EditForm(
    state: P10UserExampleUiState,
    actions: P10UserExampleActions,
) {
    val draft = state.edit.draft ?: return
    val saving = state.edit.saving
    val errors = state.edit.errors
    P10StemOptionsCard(draft, saving, actions)
    P10AnswerEditCard(draft, errors, saving, actions)
    if (state.isMistake) P10MistakeEditCard(draft, saving, actions)
    P10SourceEditCard(draft, errors, saving, actions)
    P10IdiomEditCard(state, draft, saving, actions)
}

@Composable
private fun P10StemOptionsCard(
    draft: P10EditDraft,
    saving: Boolean,
    actions: P10UserExampleActions,
) {
    CCard {
        CLabel("题干")
        CInput(
            value = draft.stem,
            onValueChange = actions.onStem,
            placeholder = "题目原文",
            enabled = !saving,
        )
        OPTION_LABELS.forEachIndexed { index, label ->
            CLabel(label, Modifier.padding(top = Tokens.Space.lmetaTop.dp))
            CInput(
                value = draft.options.getOrNull(index).orEmpty(),
                onValueChange = { actions.onOption(index, it) },
                placeholder = label,
                enabled = !saving,
            )
        }
    }
}

@Composable
private fun P10AnswerEditCard(
    draft: P10EditDraft,
    errors: P10EditFieldErrors,
    saving: Boolean,
    actions: P10UserExampleActions,
) {
    CCard {
        CLabel("我的答案（可选）")
        CInput(
            value = draft.answer,
            onValueChange = actions.onAnswer,
            placeholder = "选 A / B / C / D",
            enabled = !saving,
            error = errors.answer,
            errorHint = P10EditTransitions.HINT_ANSWER.takeIf { errors.answer },
        )
    }
}

@Composable
private fun P10MistakeEditCard(
    draft: P10EditDraft,
    saving: Boolean,
    actions: P10UserExampleActions,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CCard {
        CLabel("我的错因")
        CInput(
            value = draft.reason,
            onValueChange = actions.onReason,
            placeholder = "如：望文生义",
            enabled = !saving,
        )
        CLabel("自定义标签", Modifier.padding(top = Tokens.Space.lmetaTop.dp))
        P10TagEditor(draft, saving, actions)
        Text(
            P10EditTransitions.HINT_TAG,
            color = c.text3,
            fontSize = Tokens.FontRole.caption.sp,
            modifier = Modifier.padding(top = Tokens.Space.lmetaTop.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun P10TagEditor(
    draft: P10EditDraft,
    saving: Boolean,
    actions: P10UserExampleActions,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CInput(
            value = draft.tagDraft,
            onValueChange = actions.onTagDraft,
            placeholder = "如：易混词",
            enabled = !saving,
            modifier = Modifier.weight(1f),
        )
        if (!saving) {
            Spacer(Modifier.width(Tokens.Space.lmetaGap.dp))
            Box(Modifier.width(Tokens.Space.hitMin.dp)) {
                CButton("加入", actions.onAddTag, variant = CButtonVariant.Ghost)
            }
        }
    }
    if (draft.tags.isNotEmpty()) {
        FlowRow(
            Modifier.padding(top = Tokens.Space.lsubTop.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
        ) {
            draft.tags.forEach { tag ->
                CTag(
                    tag,
                    Modifier
                        .clickable(enabled = !saving) { actions.onRemoveTag(tag) }
                        .semantics { contentDescription = "删除标签$tag" },
                )
            }
        }
    }
}

@Composable
private fun P10SourceEditCard(
    draft: P10EditDraft,
    errors: P10EditFieldErrors,
    saving: Boolean,
    actions: P10UserExampleActions,
) {
    CCard {
        CLabel("来源信息（可空）")
        CInput(
            value = draft.examType,
            onValueChange = actions.onExamType,
            placeholder = "考试类型：国考",
            enabled = !saving,
        )
        CInput(
            value = draft.year,
            onValueChange = actions.onYear,
            placeholder = "年份：2023",
            enabled = !saving,
            error = errors.year,
            errorHint = P10EditTransitions.HINT_YEAR.takeIf { errors.year },
            modifier = Modifier.padding(top = Tokens.Space.lmetaTop.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun P10IdiomEditCard(
    state: P10UserExampleUiState,
    draft: P10EditDraft,
    saving: Boolean,
    actions: P10UserExampleActions,
) {
    CCard {
        CLabel("归属成语")
        FlowRow(
            Modifier.padding(top = Tokens.Space.lmetaTop.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
        ) {
            val words = state.detail?.idioms?.associate { it.id to it.word }.orEmpty()
            draft.idiomIds.forEach { id ->
                val word = words[id] ?: id
                CTag(
                    word,
                    Modifier
                        .clickable(enabled = !saving) { actions.onRemoveIdiom(id) }
                        .semantics { contentDescription = "移除归属$word" },
                )
            }
        }
        if (!saving) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp)) {
                CButton(
                    "添加归属成语",
                    { actions.onPickIdiom({ ref -> actions.onAddIdiom(ref.id) }, {}) },
                    Modifier.weight(1f),
                    CButtonVariant.Ghost,
                )
                CButton("+ 加一个", actions.onAddNextIdiom, Modifier.weight(1f), CButtonVariant.Ghost)
            }
        }
    }
}

/** 页面级提示（中性）与写库失败（红）：都带「知道了」，草稿留在原地不清空。 */
@Composable
internal fun P10EditMessageBar(
    state: P10UserExampleUiState,
    actions: P10UserExampleActions,
) {
    val edit = state.edit
    when {
        edit.errorMessage != null ->
            CBar(CBarKind.Error, edit.errorMessage!!, actionText = "知道了", onAction = actions.onDismissMessage)
        edit.pageMessage != null ->
            CBar(CBarKind.Info, edit.pageMessage!!, actionText = "知道了", onAction = actions.onDismissMessage)
    }
}

/** 删除二次确认：中部卡片 + 危险按钮；覆起后可见层只有这一处红。 */
@Composable
internal fun P10DeleteConfirmOverlay(
    target: P10DeleteTarget,
    actions: P10UserExampleActions,
    deleting: Boolean = false,
) {
    val c = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val title = if (target == P10DeleteTarget.Marked) "删除这道错题？" else "删除这道录入题？"
    val body =
        if (target == P10DeleteTarget.Marked) {
            "只会把这道题移出错题本，这道录入题仍保留在对应成语的「我的录入题」里。"
        } else {
            "这道题与它归属成语的关联会一并删除，本机保存的原图也会一起删除，删除后无法恢复。"
        }
    val shape = RoundedCornerShape(Tokens.Radius.r6.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(c.modal)
            .clickable(enabled = !deleting, onClick = actions.onDismissDelete),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = Tokens.Space.modalWidth.dp)
                .clickable(onClick = {})
                .background(c.surface, shape)
                .border(1.dp, c.line, shape)
                .padding(Tokens.Space.contentTop.dp),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
        ) {
            Text(title, color = c.head, fontSize = Tokens.FontRole.sectionTitle.sp, fontWeight = FontWeight.Bold)
            Text(
                body,
                color = c.text2,
                fontSize = Tokens.FontRole.supporting.sp,
                lineHeight = (Tokens.FontRole.supporting * 1.7f).sp,
            )
            CButton(
                "删除",
                actions.onConfirmDelete,
                variant = CButtonVariant.DangerFilled,
                enabled = !deleting,
                loading = deleting,
            )
            CButton("取消", actions.onDismissDelete, variant = CButtonVariant.Ghost, enabled = !deleting)
        }
    }
}
