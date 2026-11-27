package com.chenyubang.feature.me

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CCheckRow
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.COverlay
import com.chenyubang.core.ui.tokens.Tokens

/** P14 路由入口（`settings/scope`）：读写 `exam_scope`，写完后由 app 重建可见范围。 */
@Composable
fun P14ExamScopeRoute(
    request: ExamScopeRequest,
    onSaved: (ExamScopeSelection) -> Unit,
    onCancel: () -> Unit,
) {
    val factory =
        remember(request.saver) {
            viewModelFactory { initializer { P14ExamScopeViewModel(request.saver) } }
        }
    val scope: P14ExamScopeViewModel = viewModel(key = "p14-exam-scope", factory = factory)
    val state by scope.state.collectAsState()
    LaunchedEffect(request.initial, request.availableProvinces) {
        scope.reset(request.initial, request.availableProvinces)
    }
    var confirmDiscard by remember { mutableStateOf(false) }
    val onboarding = request.mode == ExamScopeMode.Onboarding
    // 有未保存改动时先问是否放弃（IA 4.2 第 5 / 35 行、5.2）。P01 引导不可返，不挂这个拦截。
    BackHandler(enabled = !onboarding && state.dirty) { confirmDiscard = true }
    P14ExamScopeContent(
        state = state,
        mode = request.mode,
        onToggleType = scope::toggleType,
        onToggleProvince = scope::toggleProvince,
        onSave = { scope.save(complete = onboarding, onSaved = onSaved) },
        onSkip = { scope.save(complete = true, onSaved = onSaved) },
        onBack = onCancel,
    )
    if (confirmDiscard) {
        DiscardOverlay(
            onDismiss = { confirmDiscard = false },
            onDiscard = {
                confirmDiscard = false
                onCancel()
            },
        )
    }
}

@Composable
private fun DiscardOverlay(
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    COverlay(title = "放弃修改？", centered = false, onCancel = onDismiss) {
        Text(
            text = "未保存的范围修改会丢弃，已录的错题、收藏与学习状态不受影响。",
            color = colors.text2,
            fontSize = Tokens.FontRole.supporting.sp,
        )
        CButton("放弃修改", onDiscard)
        CButton("继续编辑", onDismiss, variant = CButtonVariant.Ghost)
    }
}

/**
 * P14 考试范围设置页主体（信息架构 v1.4 P14 ②–⑧）。
 *
 * - 国考 / 省考（可选省份）/ 事业编勾选；教师编禁用并写「题库到位后开放」。
 * - 省考勾选时省份默认全选、省考内容正常显示，省份仅为筛选维度，无「未选省份」异常态。
 * - 生效说明与「不影响已录错题」说明按 IA P14 ⑥⑦。
 */
@Composable
fun P14ExamScopeContent(
    state: P14ExamScopeUiState,
    mode: ExamScopeMode,
    onToggleType: (ExamScopeType) -> Unit = {},
    onToggleProvince: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onSkip: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        Modifier.fillMaxSize().background(colors.page).verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        if (mode == ExamScopeMode.Onboarding) BrandBlock()
        ScopeCard(mode, state, onToggleType, onToggleProvince)
        EffectiveScopeCard()
        if (state.draft.isEmpty) EmptyScopeHint()
        if (state.failed) {
            Text(
                text = "范围保存失败，请重试；当前选择已保留。",
                color = colors.warnfg,
                fontSize = Tokens.FontRole.supporting.sp,
            )
        }
        ScopeActions(mode, state.saving, onSave, onSkip, onBack)
    }
}

@Composable
private fun ScopeCard(
    mode: ExamScopeMode,
    state: P14ExamScopeUiState,
    onToggleType: (ExamScopeType) -> Unit,
    onToggleProvince: (String) -> Unit,
) {
    val draft = state.draft
    CCard {
        CLabel(if (mode == ExamScopeMode.Onboarding) "首次打开 · 先定考试范围" else "勾选要考的类别")
        Spacer(Modifier.height(Tokens.Space.lsubTop.dp))
        CCheckRow(
            label = "国考",
            checked = draft.national,
            onCheckedChange = { onToggleType(ExamScopeType.National) },
            enabled = !state.saving,
            trailing = if (draft.national) "已选" else "未选",
        )
        CCheckRow(
            label = "省考",
            checked = draft.provincial,
            onCheckedChange = { onToggleType(ExamScopeType.Provincial) },
            enabled = !state.saving,
            trailing = draft.provincialTrailing(state.availableProvinces),
            showDivider = draft.provincial,
        )
        if (draft.provincial) {
            ProvinceGroup(state, onToggleProvince)
        }
        Spacer(Modifier.height(Tokens.Space.lsubTop.dp))
        CCheckRow(
            label = "事业编",
            checked = draft.institution,
            onCheckedChange = { onToggleType(ExamScopeType.Institution) },
            enabled = !state.saving,
            trailing = if (draft.institution) "已选" else "未选",
            showDivider = false,
        )
        CCheckRow(
            label = "教师编",
            checked = false,
            onCheckedChange = {},
            enabled = false,
            trailing = "题库到位后开放",
            showDivider = false,
        )
    }
}

@Composable
private fun EffectiveScopeCard() {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CCard(soft = true) {
        CLabel("生效范围")
        Spacer(Modifier.height(Tokens.Space.lsubTop.dp))
        Text(
            text = "范围决定成语库与例题库里能看到哪些内容；省份用于筛选省考内容，不改变页面的其他功能。",
            color = colors.text2,
            fontSize = Tokens.FontRole.body.sp,
        )
        Text(
            text = "改范围不会影响已录的错题、收藏与学习状态；P01 引导用的是同一份数据，两边同步。",
            color = colors.text3,
            fontSize = Tokens.FontRole.supporting.sp,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
    }
}

@Composable
private fun EmptyScopeHint() {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Text(
        text = "不选范围时成语库和例题库会是空的，可以稍后在『我的』里改。",
        color = colors.text3,
        fontSize = Tokens.FontRole.supporting.sp,
    )
}

@Composable
private fun ScopeActions(
    mode: ExamScopeMode,
    saving: Boolean,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    CButton(
        text = if (mode == ExamScopeMode.Onboarding) "进入" else "保存",
        onClick = onSave,
        loading = saving,
    )
    if (mode == ExamScopeMode.Onboarding) {
        CButton(
            text = "跳过，沿用默认",
            onClick = onSkip,
            variant = CButtonVariant.Ghost,
            enabled = !saving,
        )
    } else {
        CButton(
            text = "取消",
            onClick = onBack,
            variant = CButtonVariant.Ghost,
            enabled = !saving,
        )
    }
}

@Composable
private fun BrandBlock() {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        Modifier.fillMaxWidth().padding(vertical = Tokens.Space.block.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CChromeGlyph(
            kind = CChromeIcon.Book,
            color = colors.accent,
            modifier = Modifier.size(Tokens.Icon.n26.dp),
        )
        Text(
            text = "成语记忆",
            color = colors.head,
            fontSize = Tokens.FontRole.sectionTitle.sp,
            fontWeight = FontWeight.W700,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
        Text(
            text = "把考公考编的成语错题，记成自己的题库",
            color = colors.text3,
            fontSize = Tokens.FontRole.supporting.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
    }
}

@Composable
private fun ProvinceGroup(
    state: P14ExamScopeUiState,
    onToggleProvince: (String) -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(Modifier.padding(top = Tokens.Space.lmetaTop.dp)) {
        CLabel("省考省份（可多选）")
        Text(
            text = "省份用于筛选省考内容，可自行增减",
            color = colors.text3,
            fontSize = Tokens.FontRole.supporting.sp,
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
        )
        if (state.availableProvinces.isEmpty()) {
            Text(
                text = "当前题库没有省考省份数据，选好题库版本后再来。",
                color = colors.text3,
                fontSize = Tokens.FontRole.supporting.sp,
                modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
            )
        } else {
            state.availableProvinces.forEachIndexed { index, province ->
                CCheckRow(
                    label = province,
                    checked = province in state.draft.provinces,
                    onCheckedChange = { onToggleProvince(province) },
                    enabled = !state.saving,
                    showDivider = index != state.availableProvinces.lastIndex,
                )
            }
        }
    }
}
