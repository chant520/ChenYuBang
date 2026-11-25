package com.chenyubang.feature.mistake

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.UserExampleEditor
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CImagePlaceholder
import com.chenyubang.core.ui.CPlaceholderKind
import com.chenyubang.core.ui.CSpinner
import com.chenyubang.core.ui.tokens.Tokens
import java.io.File

/** app 层接线：DAO 与 filesDir 注入，页面不直接依赖 Android 数据库。 */
@Composable
@Suppress("LongMethod")
fun P10UserExampleRoute(
    exampleId: String,
    database: AppDatabase,
    filesDir: File,
    onBack: () -> Unit,
    onOpenIdiom: (String) -> Unit,
    onDataChanged: () -> Unit = {},
) {
    val latestOnDataChanged by rememberUpdatedState(onDataChanged)
    val latestOnBack by rememberUpdatedState(onBack)
    val editor =
        remember(database, filesDir) {
            UserExampleEditor(database, File(filesDir, "images"))
        }
    val factory =
        remember(database, filesDir) {
            viewModelFactory {
                initializer {
                    P10UserExampleViewModel(
                        loadDetail = { loadP10Detail(database, it) },
                        isImageReadable = { isP10ImageReadable(filesDir, it) },
                        loadIdiomCandidates = {
                            database.idiomMatchDao().allIdioms().map { P10IdiomRef(it.id, it.word) }
                        },
                        saveEdit = { editor.edit(it) },
                        deleteExample = { id, target ->
                            if (target == P10DeleteTarget.Marked) editor.deleteMarked(id) else editor.deleteUnmarked(id)
                        },
                        setReviewState = database.reviewDao()::setState,
                        onDataChanged = { latestOnDataChanged() },
                        onExit = { latestOnBack() },
                    )
                }
            }
        }
    val model: P10UserExampleViewModel = viewModel(key = "p10-user-example", factory = factory)
    val state by model.state.collectAsState()
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    BackHandler(
        enabled = state.edit.editing || state.edit.deleteConfirm != null || state.updatingReviewState,
    ) { model.handleBack() }
    LaunchedEffect(exampleId) { model.load(exampleId) }
    val imagePath = state.detail?.imagePath
    LaunchedEffect(imagePath) {
        image = imagePath?.let { decodeP10Image(filesDir, it) }
    }
    P10UserExampleContent(
        state = state,
        image = image,
        actions =
            P10UserExampleActions(
                onBack = onBack,
                onRetry = { model.load(exampleId) },
                onToggleAnswer = { model.toggleAnswer() },
                onPickOption = { model.pick(it) },
                onStartReroll = { model.startReroll() },
                onCheck = { model.check() },
                onExitReroll = { model.exitReroll() },
                onOpenIdiom = onOpenIdiom,
                onEdit = { model.startEdit() },
                onCancelEdit = { model.cancelEdit() },
                onSave = { model.save() },
                onStem = { model.changeDraft { draft -> draft.copy(stem = it) } },
                onOption = { index, value -> model.setOption(index, value) },
                onAnswer = { model.changeDraft { draft -> draft.copy(answer = it) } },
                onReason = { model.changeDraft { draft -> draft.copy(reason = it) } },
                onTagDraft = { model.changeDraft { draft -> draft.copy(tagDraft = it) } },
                onAddTag = { model.addTag() },
                onRemoveTag = { tag -> model.changeDraft { draft -> draft.copy(tags = draft.tags - tag) } },
                onExamType = { model.changeDraft { draft -> draft.copy(examType = it) } },
                onYear = { model.changeDraft { draft -> draft.copy(year = it) } },
                onAddIdiom = { model.addIdiom(it) },
                onRemoveIdiom = { id -> model.changeDraft { draft -> draft.copy(idiomIds = draft.idiomIds - id) } },
                onAddNextIdiom = { model.addNextIdiom() },
                onIdiomQuery = { model.setIdiomQuery(it) },
                onRequestDelete = { model.requestDelete(it) },
                onDismissDelete = { model.dismissDelete() },
                onConfirmDelete = { model.confirmDelete() },
                onDismissMessage = { model.dismissMessage() },
                onChangeReviewState = { model.changeReviewState(it) },
            ),
    )
}

/** P10 纯内容：快照测试直接喂 state + actions，无 DB / 路由依赖。 */
@Composable
fun P10UserExampleContent(
    state: P10UserExampleUiState,
    image: ImageBitmap? = null,
    actions: P10UserExampleActions = P10UserExampleActions(),
) {
    val c = if (androidx.compose.foundation.isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    var zoomed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(c.page)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            P10UserExampleBody(state, image, actions) { zoomed = true }
        }
        if (zoomed && image != null) {
            COverlayWrapper(image) { zoomed = false }
        }
        state.edit.deleteConfirm?.let { P10DeleteConfirmOverlay(it, actions, state.edit.deleting) }
    }
}

/**
 * 页面正文（无滚动、wrap 自然高度）：与 [P10UserExampleContent] 共用同一套构件渲染。
 * 「按自然高度单帧」整页快照直接喂它，覆盖固定视口外的 ⑤–⑩ 与底部删除行，避免半页假覆盖。
 */
@Composable
internal fun P10UserExampleBody(
    state: P10UserExampleUiState,
    image: ImageBitmap? = null,
    actions: P10UserExampleActions = P10UserExampleActions(),
    onZoom: () -> Unit = {},
) {
    val c = if (androidx.compose.foundation.isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(c.page)
                .padding(horizontal = Tokens.Space.pageX.dp)
                .padding(top = Tokens.Space.contentTop.dp, bottom = Tokens.Space.block.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        P10Header(state, actions)
        P10EditMessageBar(state, actions)
        when {
            state.failed ->
                CBar(
                    kind = CBarKind.Warn,
                    title = "录入题读取失败，请重试",
                    subtitle = "只重新读取本机数据，不影响已录入的题目。",
                    actionText = "重试",
                    onAction = actions.onRetry,
                )
            state.loading -> CSpinner(color = c.accent)
            // 双端同文案：iOS P10UserExampleScreen.swift:85-86（页名已拍板「录入题详情」）。
            state.missing || state.detail == null ->
                CEmptyState(
                    title = "没有找到这道录入题",
                    description = "该题已不在本机题库中，可返回上一个页面换一道看。",
                )
            else -> {
                val detail = state.detail!!
                P10ImageBlock(detail, state.imageUnreadable, image, onZoom)
                if (state.edit.editing) {
                    P10EditForm(state, actions)
                } else {
                    P10QuestionCard(detail, state, actions)
                    P10UserExampleBlocks(state, actions)
                }
            }
        }
    }
}

@Composable
private fun COverlayWrapper(
    image: ImageBitmap,
    onCancel: () -> Unit,
) {
    com.chenyubang.core.ui.COverlay(title = "原图", centered = true, onCancel = onCancel) {
        Image(image, contentDescription = "原图放大", modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun P10Header(
    state: P10UserExampleUiState,
    actions: P10UserExampleActions,
) {
    val c = if (androidx.compose.foundation.isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val editing = state.edit.editing
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CChromeGlyph(
            kind = CChromeIcon.Back,
            color = c.text2,
            modifier =
                Modifier
                    .size(21.dp)
                    .clickable(enabled = !state.edit.saving && !state.edit.deleting && !state.updatingReviewState) {
                        if (editing) actions.onCancelEdit() else actions.onBack()
                    }
                    .semantics { contentDescription = "返回" },
        )
        Text(
            "录入题详情",
            color = c.head,
            fontSize = Tokens.Font.n15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = Tokens.Space.lmetaGap.dp).weight(1f),
        )
        if (editing) {
            CButton(
                "取消",
                actions.onCancelEdit,
                Modifier.width(64.dp),
                CButtonVariant.Ghost,
                enabled = !state.edit.saving,
            )
            CButton(
                "保存",
                actions.onSave,
                Modifier.width(64.dp),
                enabled = !state.edit.errors.hasInvalid && !state.edit.saving,
                loading = state.edit.saving,
            )
        } else {
            CButton("编辑", actions.onEdit, Modifier.width(64.dp), CButtonVariant.Ghost, enabled = state.canEdit)
            CButton(
                "删除",
                { actions.onRequestDelete(if (state.isMistake) P10DeleteTarget.Marked else P10DeleteTarget.Unmarked) },
                Modifier.width(64.dp),
                CButtonVariant.Ghost,
                enabled = state.canDelete,
            )
        }
    }
}

@Composable
private fun P10ImageBlock(
    detail: P10UserExampleDetail,
    unreadable: Boolean,
    image: ImageBitmap?,
    onZoom: () -> Unit,
) {
    when (P10Logic.imageSlotKind(detail.imagePath, unreadable, image != null)) {
        P10ImageSlotKind.Warn ->
            CImagePlaceholder(
                kind = CPlaceholderKind.Warn,
                text = "原图已不在",
                subtitle = P10Logic.imageMissingSubtitle(detail.isMistake),
            )
        P10ImageSlotKind.Skeleton -> CImagePlaceholder(kind = CPlaceholderKind.Skeleton)
        P10ImageSlotKind.Image ->
            image?.let {
                Image(
                    it,
                    contentDescription = "录入题原图，点击放大",
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onZoom),
                )
            }
    }
}
