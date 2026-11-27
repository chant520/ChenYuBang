package com.chenyubang.feature.idiom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.IdiomLearningState
import com.chenyubang.core.data.IdiomLearningStatus
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CLink
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.CTagStyle
import com.chenyubang.core.ui.tokens.Tokens

/**
 * P05 路由入口：装配只读详情 ViewModel。读音朗读为预留位，本期不接。
 * onOpenMistakes 走 A5 数字进 P09；onOpenMistakeBook 是独立的「去错题本」P08 入口。
 */
@Composable
fun P05IdiomDetailRoute(
    idiomId: String,
    database: AppDatabase,
    onBack: () -> Unit,
    onOpenMistakes: () -> Unit,
    onOpenMistakeBook: () -> Unit = {},
    onOpenExample: (String) -> Unit,
    onReviewIdiom: (Int) -> Unit = {},
    revision: Long = 0,
) {
    val factory =
        remember(database) {
            viewModelFactory {
                initializer {
                    P05IdiomDetailViewModel(
                        loadIdiom = { id -> database.idiomDao().byId(id) },
                        loadExamples = { id -> database.exampleDao().listByIdiom(id) },
                        countUserExamples = { id -> database.mistakeQueryDao().countUserExamplesByIdiom(id) },
                        loadState = { id -> database.idiomStateDao().state(id) },
                        writeStatus = { id, status -> database.idiomStateDao().setStatus(id, status) },
                        writeFavorite = { id, favorite -> database.idiomStateDao().setFavorite(id, favorite) },
                        loadPendingCount = { id -> database.reviewDao().pendingCount(id) },
                    )
                }
            }
        }
    val model: P05IdiomDetailViewModel = viewModel(key = "p05-idiom-detail", factory = factory)
    val state by model.state.collectAsState()
    LaunchedEffect(idiomId) { model.load(idiomId) }
    LaunchedEffect(revision) {
        if (revision > 0) model.refreshPendingCount()
    }
    P05IdiomDetailContent(
        state = state,
        onBack = onBack,
        onOpenMistakes = onOpenMistakes,
        onOpenMistakeBook = onOpenMistakeBook,
        onOpenExample = onOpenExample,
        onRetry = { model.load(idiomId) },
        canReview = state.pendingCount > 0,
        onReviewIdiom = { model.requestReview(onReviewIdiom) },
        onUpdateStatus = model::updateStatus,
        onUpdateFavorite = model::updateFavorite,
    )
}

/**
 * P05 成语详情长页（信息架构 v1.4 P05 ①–⑪）。
 *
 * 五项齐全：读音 / 释义 / 感情色彩 / 易错点 / 近反义辨析；出处一并展示。缺字段一律「暂无」。
 * 相关题目（系统题库）列出该成语关联真题，点击进 P07（1.6）；A5「我的录入题 N」取真实计数，
 * N>0 可点进 P09，标错者由 P09 沿用错题样式与标记展示。
 */
@Composable
fun P05IdiomDetailContent(
    state: P05IdiomDetailUiState,
    onBack: () -> Unit = {},
    onOpenMistakes: () -> Unit = {},
    onOpenMistakeBook: () -> Unit = {},
    onOpenExample: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    canReview: Boolean = false,
    onReviewIdiom: () -> Unit = {},
    onUpdateStatus: (IdiomLearningStatus) -> Unit = {},
    onUpdateFavorite: (Boolean) -> Unit = {},
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        DetailHeader(
            onBack = onBack,
            colors = colors,
            learningState = state.state,
            onUpdateStatus = onUpdateStatus,
            onUpdateFavorite = onUpdateFavorite,
        )
        if (state.updateFailed) CBar(CBarKind.Warn, "学习状态保存失败，请重试", subtitle = "已保留页面原有状态，不影响已录入内容。")
        when {
            state.loading ->
                CBar(
                    kind = CBarKind.Info,
                    title = "正在读取成语详情…",
                    subtitle = "内容来自随安装包内置的题库。",
                )
            state.missing ->
                CEmptyState(
                    title = "没有找到这个成语",
                    description = "该词条不在当前题库中，可返回成语库换一个查看。",
                )
            state.failed ->
                CBar(
                    kind = CBarKind.Warn,
                    title = "详情读取失败，请重试",
                    subtitle = "只重新读取本机数据，不影响已录入的内容。",
                    actionText = "重试",
                    onAction = onRetry,
                )
            state.detail != null ->
                detailBody(
                    detail = state.detail,
                    examples = state.examples,
                    colors = colors,
                    actions =
                        IdiomDetailActions(
                            userExampleCount = state.userExampleCount,
                            onOpenMistakes = onOpenMistakes,
                            onOpenMistakeBook = onOpenMistakeBook,
                            onOpenExample = onOpenExample,
                            canReview = canReview,
                            onReviewIdiom = onReviewIdiom,
                        ),
                )
        }
    }
}

@Composable
private fun DetailHeader(
    onBack: () -> Unit,
    colors: Tokens.ColorSet,
    learningState: IdiomLearningState,
    onUpdateStatus: (IdiomLearningStatus) -> Unit,
    onUpdateFavorite: (Boolean) -> Unit,
) {
    var statusPickerOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(Tokens.Space.hitMin.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            CChromeGlyph(CChromeIcon.Back, colors.text2, Modifier.size(Tokens.Icon.n21.dp))
        }
        Text(
            text = "成语详情",
            color = colors.head,
            fontSize = Tokens.FontRole.pageTitle.sp,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(start = Tokens.Space.lmetaGap.dp),
        )
        // idiomId 为空表示无状态输入（历史基线路径）：保持只读「收藏（预留）」外观，不改既有帧。
        // 生产路由 load 后会带回真实 idiomId，走下方 A6/A7 可交互区。
        if (learningState.idiomId.isBlank()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                CTag(text = "收藏（预留）", style = CTagStyle.Ghost)
            }
        } else {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                // A6 自评入口：收藏左侧的 ghost 标签，点按弹三选一（见 StatusPickerOverlay）。
                Box(Modifier.clickable { statusPickerOpen = true }) {
                    CTag(
                        text = "学习状态：${statusTag(learningState.status).text}",
                        style = CTagStyle.Ghost,
                    )
                }
                Spacer(Modifier.padding(end = Tokens.Space.lmetaGap.dp))
                // A7 收藏：未收藏 ghost「收藏」，已收藏实心「已收藏」，点按即切换、无 toast。
                Box(Modifier.clickable { onUpdateFavorite(!learningState.favorite) }) {
                    CTag(
                        text = if (learningState.favorite) "已收藏" else "收藏",
                        style = if (learningState.favorite) CTagStyle.Solid else CTagStyle.Ghost,
                    )
                }
            }
        }
    }
    if (statusPickerOpen) {
        StatusPickerOverlay(
            current = learningState.status,
            onSelect = { status ->
                onUpdateStatus(status)
                statusPickerOpen = false
            },
            onCancel = { statusPickerOpen = false },
        )
    }
}

/** P05 正文区的导航与单成语复习入口，打包避免 detailBody 参数过多。 */
data class IdiomDetailActions(
    val userExampleCount: Int,
    val onOpenMistakes: () -> Unit,
    val onOpenMistakeBook: () -> Unit,
    val onOpenExample: (String) -> Unit,
    val canReview: Boolean,
    val onReviewIdiom: () -> Unit,
)

@Composable
private fun detailBody(
    detail: IdiomDetailView,
    examples: List<IdiomExampleView>,
    colors: Tokens.ColorSet,
    actions: IdiomDetailActions,
) {
    // 成语名 + 拼音 + 读音（朗读是未来项，这里只展示图标与拼音，不触发播放）。
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.word,
                color = colors.head,
                fontSize = Tokens.FontRole.contentTitle.sp,
                fontWeight = FontWeight.W700,
            )
            Text(
                text = detail.pinyin,
                color = colors.text3,
                fontSize = Tokens.FontRole.caption.sp,
                modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
            )
        }
        CChromeGlyph(CChromeIcon.Sound, colors.text3, Modifier.size(Tokens.Icon.n21.dp))
    }
    Row {
        CTag(text = detail.color)
    }

    InfoCard(label = "释义", value = detail.sense, strong = true, colors = colors)
    InfoCard(label = "出处", value = detail.origin, colors = colors)

    CCard {
        CLabel(text = "易错点")
        if (detail.pitfalls.isEmpty()) {
            BodyText(text = IdiomDetail.PLACEHOLDER, colors = colors)
        } else {
            detail.pitfalls.forEach { item -> BodyText(text = item, colors = colors) }
        }
    }

    CCard {
        CLabel(text = "近义与反义")
        BodyText(text = "近义：${IdiomDetail.presentList(detail.synonyms)}", colors = colors)
        BodyText(text = "反义：${IdiomDetail.presentList(detail.antonyms)}", colors = colors)
    }

    RelatedExamplesCard(
        examples = examples,
        userExampleCount = actions.userExampleCount,
        colors = colors,
        onOpenExample = actions.onOpenExample,
    )
    MyExamplesCard(
        count = actions.userExampleCount,
        onOpenMistakes = actions.onOpenMistakes,
        onOpenMistakeBook = actions.onOpenMistakeBook,
        colors = colors,
    )

    // D1 单成语复习入口（决策 H）：该成语无待复习错题时保持置灰并说明原因；可点进 P11 单成语模式。
    CButton(text = "复习这个成语", onClick = actions.onReviewIdiom, enabled = actions.canReview)
    if (!actions.canReview) {
        Text(
            text = "这个成语下还没有待复习的错题",
            color = colors.text3,
            fontSize = Tokens.FontRole.supporting.sp,
        )
    }
}

@Composable
private fun InfoCard(
    label: String,
    value: String,
    colors: Tokens.ColorSet,
    strong: Boolean = false,
) {
    CCard {
        CLabel(text = label)
        Text(
            text = value,
            color = if (strong) colors.text else colors.text2,
            fontSize = Tokens.FontRole.body.sp,
            fontWeight = if (strong) FontWeight.W600 else FontWeight.Normal,
        )
    }
}

@Composable
private fun BodyText(
    text: String,
    colors: Tokens.ColorSet,
) {
    Text(
        text = text,
        color = colors.text2,
        fontSize = Tokens.FontRole.body.sp,
        modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
    )
}

/**
 * A4「相关题目」双来源（信息架构 v1.4 P05 ⑧、高保真稿 A P05 v2.1）。
 * 系统题库：按 example_idiom 列出关联真题，每条题干摘要 + 年份 · 地区 · 考试类型 + 题型，点击进 P07；
 * 暂无时保留「暂无真题，后续补充」不隐藏；我录入的题暂无时保留「还没在这个成语下录入题」不隐藏，
 * 具体条目由 P09 按 UserExampleWithMistake 口径展示（标错者带错题样式与标记）。
 */
@Composable
private fun RelatedExamplesCard(
    examples: List<IdiomExampleView>,
    userExampleCount: Int,
    colors: Tokens.ColorSet,
    onOpenExample: (String) -> Unit,
) {
    CCard {
        CLabel(text = "相关题目")
        SectionCaption(text = "系统题库", colors = colors)
        if (examples.isEmpty()) {
            BodyText(text = "暂无真题，后续补充", colors = colors)
        } else {
            examples.forEach { example ->
                RelatedExampleRow(example = example, colors = colors, onOpenExample = onOpenExample)
            }
        }
        SectionCaption(text = "我录入的题（标错才进错题本）", colors = colors)
        BodyText(
            text =
                if (userExampleCount > 0) {
                    "点下方数字查看全部录入题（标错的按错题展示）"
                } else {
                    "还没在这个成语下录入题"
                },
            colors = colors,
        )
    }
}

@Composable
private fun RelatedExampleRow(
    example: IdiomExampleView,
    colors: Tokens.ColorSet,
    onOpenExample: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpenExample(example.id) }
                .padding(top = Tokens.Space.lmetaTop.dp),
    ) {
        Text(
            text = example.stem,
            color = colors.text2,
            fontSize = Tokens.FontRole.body.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
        ) {
            Text(text = example.sourceLine, color = colors.text3, fontSize = Tokens.FontRole.caption.sp)
            Text(text = example.questionType, color = colors.text3, fontSize = Tokens.FontRole.caption.sp)
        }
    }
}

@Composable
private fun SectionCaption(
    text: String,
    colors: Tokens.ColorSet,
) {
    Text(
        text = text,
        color = colors.text3,
        fontSize = Tokens.FontRole.caption.sp,
        modifier = Modifier.padding(top = Tokens.Space.lmetaTop.dp),
    )
}

/**
 * A5「我的录入题 N」数字入口（N 为该成语下全部录入题数，含未标错）。
 * N>0 数字可点进 P09，标错者由 P09 沿用错题样式与标记；N=0 灰显不可点并保留空态文案。
 * 「去错题本」是独立 P08 导航，不与 A5 数字共用回调；本区不出现拍照入口（决策 D）。
 */
@Composable
private fun MyExamplesCard(
    count: Int,
    onOpenMistakes: () -> Unit,
    onOpenMistakeBook: () -> Unit,
    colors: Tokens.ColorSet,
) {
    CCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (count > 0) Modifier.clickable(onClick = onOpenMistakes) else Modifier,
        ) {
            CLabel(text = "我在这个成语下录入了")
            Text(
                text = " $count 道题",
                color = if (count > 0) colors.head else colors.disfg,
                fontSize = Tokens.FontRole.metric.sp,
                fontWeight = FontWeight.W700,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = if (count > 0) "点数字查看该成语下的录入题" else "还没在这个成语下录入题",
                color = colors.text3,
                fontSize = Tokens.FontRole.supporting.sp,
            )
            CLink(text = "去错题本", onClick = onOpenMistakeBook)
        }
    }
}
