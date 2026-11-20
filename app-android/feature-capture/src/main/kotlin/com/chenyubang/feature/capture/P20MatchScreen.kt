package com.chenyubang.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CCheckRow
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CLink
import com.chenyubang.core.ui.tokens.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PICKER_HEIGHT_FRACTION = 0.9f

/**
 * P20 成语匹配与修正（工作包 2.4；信息架构 v1.4 P20 / 高保真稿 C P20 864–935 行）。
 *
 * 只做：文字回看（改文字回 P19）+ 候选默认勾最匹配 + 换/加/删 + 零候选空态与 P04 手动回填。
 * 下半部分（答案/错因/标签/是否做错/来源）保留版式空壳、不接收输入；保存只弹中性提示。
 * 不写 user_example / mistake，不跳 P21（Route.Done）。
 *
 * [manualPicker] 由 app 层注入 P04 搜索内容：选中 id 回调即回填并关浮层，全程不动 router 栈。
 */
@Composable
fun P20MatchScreen(
    draft: OcrDraft,
    database: AppDatabase,
    onBack: () -> Unit,
    manualPicker: @Composable (onPick: (String) -> Unit, onDismiss: () -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val text = P20Matcher.mergedText(draft)
    val model: P20MatchViewModel = viewModel(key = "p20-match/$text")
    val state by model.state.collectAsState()

    LaunchedEffect(text) {
        val idioms =
            withContext(Dispatchers.IO) {
                runCatching { database.idiomMatchDao().allIdioms() }.getOrDefault(emptyList())
            }
        model.bootstrap(text, idioms)
    }

    P20MatchContent(
        state = state,
        actions =
            P20MatchActions(
                onEdit = onBack,
                onSelect = model::select,
                onCycle = model::cycle,
                onAdd = model::add,
                onToggleDelete = model::toggleDeleteMode,
                onDelete = model::remove,
                onOpenManual = { model.setManualPicker(true) },
                onDismissManual = { model.setManualPicker(false) },
                onManualPick = model::pickManual,
                onSave = { model.showInfo(P20Matcher.SAVE_INFO) },
                onDismissInfo = model::dismissInfo,
                manualPicker = manualPicker,
            ),
        modifier = modifier,
    )
}

/** 纯渲染层：快照测试直接喂状态，无需 DB / 路由。 */
@Composable
internal fun P20MatchContent(
    state: P20MatchUiState,
    actions: P20MatchActions,
    modifier: Modifier = Modifier,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(modifier.fillMaxSize().background(colors.page)) {
        MatchHeader(onBack = actions.onEdit, colors = colors)
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
            ReviewCard(text = state.reviewText, onEdit = actions.onEdit, colors = colors)
            CandidateCard(state = state, actions = actions, colors = colors)
            PlaceholderCards(colors)
            state.info?.let { message ->
                CBar(
                    kind = CBarKind.Info,
                    title = message,
                    actionText = "知道了",
                    onAction = actions.onDismissInfo,
                )
            }
            CButton("保存到我的题库", onClick = actions.onSave, modifier = Modifier.fillMaxWidth())
        }
    }
    if (state.showManualPicker) {
        ManualPickerDialog(
            colors = colors,
            onDismiss = actions.onDismissManual,
            manualPicker = actions.manualPicker,
            onPick = actions.onManualPick,
        )
    }
}

@Composable
private fun ReviewCard(
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
        Text(text = text, color = colors.text, fontSize = Tokens.Font.n13.sp)
    }
}

@Composable
private fun CandidateCard(
    state: P20MatchUiState,
    actions: P20MatchActions,
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
    }
}

@Composable
private fun CandidateRows(
    state: P20MatchUiState,
    actions: P20MatchActions,
    colors: Tokens.ColorSet,
) {
    state.candidates.forEachIndexed { index, _ ->
        CandidateRow(state = state, index = index, actions = actions, colors = colors)
    }
    Spacer(Modifier.height(Tokens.Space.ghostY.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp)) {
        CButton("换一个", actions.onCycle, Modifier.weight(1f), CButtonVariant.Ghost, state.canCycle)
        CButton("加一个", actions.onAdd, Modifier.weight(1f), CButtonVariant.Ghost, state.canAdd)
        CButton(
            text = if (state.deleteMode) "完成" else "删一个",
            onClick = actions.onToggleDelete,
            modifier = Modifier.weight(1f),
            variant = CButtonVariant.Ghost,
            enabled = state.candidates.isNotEmpty(),
        )
    }
    val hints =
        buildList {
            if (!state.canCycle) add(P20Matcher.NO_MORE_CANDIDATES)
            if (!state.canAdd) add(P20Matcher.CANDIDATE_LIMIT)
        }
    if (hints.isNotEmpty()) {
        Text(
            text = hints.joinToString("；"),
            color = colors.text3,
            fontSize = Tokens.Font.n11.sp,
            modifier = Modifier.padding(top = Tokens.Space.ghostY.dp),
        )
    }
}

@Composable
private fun CandidateRow(
    state: P20MatchUiState,
    index: Int,
    actions: P20MatchActions,
    colors: Tokens.ColorSet,
) {
    val candidate = state.candidates[index]
    Row(verticalAlignment = Alignment.CenterVertically) {
        CCheckRow(
            label = candidate.word,
            checked = candidate.id == state.checkedId,
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
private fun MatchHeader(
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

/** 下半部分：按高保真稿保留版式，空壳占位、不接收输入。 */
@Composable
private fun PlaceholderCards(colors: Tokens.ColorSet) {
    CCard {
        CLabel("我的答案")
        PlaceholderBox(colors)
        CLabel("我的错因", Modifier.padding(top = Tokens.Space.lmetaTop.dp))
        PlaceholderBox(colors)
        CLabel("自定义标签", Modifier.padding(top = Tokens.Space.lmetaTop.dp))
        PlaceholderBox(colors)
    }
    CCard {
        CLabel("是否做错（可选）")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(Tokens.Space.chkSize.dp)
                    .background(colors.disbg, RoundedCornerShape(Tokens.Radius.r4.dp))
                    .border(1.dp, colors.disline, RoundedCornerShape(Tokens.Radius.r4.dp)),
            )
            Spacer(Modifier.width(Tokens.Space.chkGap.dp))
            Text("我这道题做错了，标记为错题", color = colors.disfg, fontSize = Tokens.Font.n13.sp)
        }
        Text(
            "仅勾选后才进错题本；不勾只存入我的题库。",
            color = colors.text3,
            fontSize = Tokens.Font.n11.sp,
            modifier = Modifier.padding(top = Tokens.Space.ghostY.dp),
        )
    }
    CCard {
        CLabel("来源信息（可空）")
        PlaceholderBox(colors)
        PlaceholderBox(colors, Modifier.padding(top = Tokens.Space.ghostY.dp))
    }
}

@Composable
private fun PlaceholderBox(
    colors: Tokens.ColorSet,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Tokens.Radius.r6.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(Tokens.Space.inputMinH.dp)
            .background(colors.inputbg, shape)
            .border(1.dp, colors.line, shape),
    )
}

/** 局部浮层：P04 搜索内容由 app 层注入；关闭/选中都只改 P20 内部状态，不 push(Route.Search)。 */
@Composable
private fun ManualPickerDialog(
    colors: Tokens.ColorSet,
    onDismiss: () -> Unit,
    manualPicker: @Composable (onPick: (String) -> Unit, onDismiss: () -> Unit) -> Unit,
    onPick: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.modal)
                .clickable(onClick = onDismiss),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(PICKER_HEIGHT_FRACTION)
                    .background(
                        colors.surface,
                        RoundedCornerShape(topStart = Tokens.Radius.r6.dp, topEnd = Tokens.Radius.r6.dp),
                    )
                    .clickable(onClick = {})
                    .padding(
                        start = Tokens.Space.pageX.dp,
                        end = Tokens.Space.pageX.dp,
                        top = Tokens.Space.contentTop.dp,
                        bottom = Tokens.Space.pageX.dp,
                    ),
            ) {
                manualPicker(onPick, onDismiss)
            }
        }
    }
}
