package com.chenyubang.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.tokens.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * P20 成语匹配与修正（信息架构 v1.4 P20 / 开发规范 v1.7）：
 * 候选可多选（≥1）、手动挑成语追加到已选末尾、答案 / 错因 / 标签 / 是否做错 / 来源信息可填；
 * 保存走 [performSave] 单事务落库，失败留在本页并给红错误条。
 */
@Composable
fun P20FormScreen(
    draft: OcrDraft,
    deps: P20FormDeps,
    onBack: () -> Unit,
    onSaved: (String, List<IdiomRef>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = P20Matcher.mergedText(draft)
    val model: P20FormViewModel = viewModel(key = "p20-form/$text")
    val state by model.state.collectAsState()

    LaunchedEffect(text) {
        val result = withContext(Dispatchers.IO) { runCatching { deps.database.idiomMatchDao().allIdioms() } }
        if (result.isFailure) {
            model.candidate.setInfo(P20Matcher.READ_FAILED)
        } else {
            model.candidate.bootstrap(text, result.getOrDefault(emptyList()))
        }
    }

    P20FormContent(
        state = state,
        actions = buildActions(draft, deps, model, onBack, onSaved),
        modifier = modifier,
    )
}

/** 把 controller 与保存桥接打包成一组事件，降低 Composable 必填参数个数。 */
private fun buildActions(
    draft: OcrDraft,
    deps: P20FormDeps,
    model: P20FormViewModel,
    onBack: () -> Unit,
    onSaved: (String, List<IdiomRef>) -> Unit,
): P20CaptureActions =
    P20CaptureActions(
        onEdit = onBack,
        onSelect = model.candidate::select,
        onCycle = model.candidate::cycle,
        onAdd = model.candidate::add,
        onToggleDelete = model.candidate::toggleDeleteMode,
        onDelete = model.candidate::remove,
        onOpenManual = { model.candidate.setManualPicker(true) },
        onDismissManual = { model.candidate.setManualPicker(false) },
        onManualPick = model.candidate::pickManual,
        onAnswer = model.form::setAnswer,
        onReason = model.form::setReason,
        onTagDraft = model.form::setTagDraft,
        onAddTag = model.form::addTag,
        onRemoveTag = model.form::removeTag,
        onToggleMistake = model.form::toggleMistake,
        onExamType = model.form::setExamType,
        onYear = model.form::setYear,
        onSave = saveAction(deps, model, draft, onSaved),
        onDismissInfo = { model.candidate.setInfo(null) },
        onDismissSaveError = { model.form.setSaveState(saving = false) },
        manualPicker = deps.manualPicker,
    )

/** 保存按钮：先校验可保存与原图，失败留页；成功交给路由跳 P21。 */
private fun saveAction(
    deps: P20FormDeps,
    model: P20FormViewModel,
    draft: OcrDraft,
    onSaved: (String, List<IdiomRef>) -> Unit,
): () -> Unit =
    {
        val current = model.state.value
        when {
            !current.canSave -> Unit
            deps.imagePath.isBlank() -> model.candidate.setInfo(P20Matcher.NO_IMAGE)
            else -> model.viewModelScope.launch { performSave(draft, deps, current, model, onSaved) }
        }
    }

/** 纯渲染层：快照测试直接喂状态，无需 DB / 路由。 */
@Composable
internal fun P20FormContent(
    state: P20CaptureState,
    actions: P20CaptureActions,
    modifier: Modifier = Modifier,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(modifier.fillMaxSize().background(colors.page)) {
        FormHeader(onBack = actions.onEdit, colors = colors)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Tokens.Space.pageX.dp,
                    end = Tokens.Space.pageX.dp,
                    top = Tokens.Space.contentTop.dp,
                    bottom = Tokens.Space.pageX.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
        ) {
            P20FormReviewCard(text = state.reviewText, onEdit = actions.onEdit, colors = colors)
            CandidateCard(state = state, actions = actions, colors = colors)
            FormFieldsCard(state = state, actions = actions, colors = colors)
            SourceCard(state = state, actions = actions, colors = colors)
            state.info?.let { message ->
                CBar(kind = CBarKind.Info, title = message, actionText = "知道了", onAction = actions.onDismissInfo)
            }
            state.form.saveError?.let { message ->
                CBar(
                    kind = CBarKind.Error,
                    title = message,
                    actionText = "知道了",
                    onAction = actions.onDismissSaveError,
                )
            }
            CButton(
                "保存到我的题库",
                onClick = actions.onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSave,
                loading = state.form.saving,
            )
        }
    }
    if (state.showManualPicker) {
        P20FormManualPickerDialog(colors, actions.onDismissManual, actions.manualPicker, actions.onManualPick)
    }
}
