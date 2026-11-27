package com.chenyubang.feature.example

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CEmptyState
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.CTagStyle
import com.chenyubang.core.ui.tokens.Tokens

/** P07 路由入口：装配只读例题详情 ViewModel（工作包 1.5）。 */
@Composable
fun P07ExampleDetailRoute(
    exampleId: String,
    database: AppDatabase,
    onBack: () -> Unit,
    onOpenIdiom: (String) -> Unit,
) {
    val factory =
        remember(database) {
            viewModelFactory {
                initializer {
                    P07ExampleDetailViewModel(
                        loadExample = { id -> database.exampleDao().byId(id) },
                        loadLinks = { id -> database.exampleDao().listByExample(id) },
                        loadIdiomWords = { database.idiomWordDao().allWords() },
                    )
                }
            }
        }
    val model: P07ExampleDetailViewModel = viewModel(key = "p07-example-detail", factory = factory)
    val state by model.state.collectAsState()
    val relatedIdiomIds by model.relatedIdiomIds.collectAsState()
    val wordIndex by model.wordIndex.collectAsState()
    LaunchedEffect(exampleId) { model.load(exampleId) }
    P07ExampleDetailContent(
        state = state,
        relatedIdiomIds = relatedIdiomIds,
        wordIndex = wordIndex,
        onBack = onBack,
        onOpenIdiom = onOpenIdiom,
        onRetry = { model.load(exampleId) },
    )
}

/**
 * P07 例题详情长页（信息架构 v1.4 P07 ①–⑧）。
 *
 * ①顶部区：返回、页名「例题详情」、标记预留位（B7 进阶）②出处行 ③题干（成语可点跳 P05）
 * ④四个选项（成语同样可点）⑤做题判分（预留位，B6 进阶，本期不出现先选后判）
 * ⑥正确答案 + 一句解析（只说明为什么选这项）⑦解析里的成语同样可点
 * ⑧关联成语区：本题关联的全部成语，一题多成语时并排展示（B3），每个都进 P05（验收 6 / 跳转 15）。
 * 未关联任何成语时该区显示「暂未关联成语」，不给按钮（§5.4），题干与解析照常显示。
 * 缺字段一律「暂无」。本期不做做题判分（二期 B6）。
 */
@Composable
fun P07ExampleDetailContent(
    state: P07ExampleDetailUiState,
    relatedIdiomIds: List<String>,
    wordIndex: Map<String, String>,
    onBack: () -> Unit = {},
    onOpenIdiom: (String) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        DetailHeader(onBack = onBack, colors = colors)
        when {
            state.loading ->
                CBar(
                    kind = CBarKind.Info,
                    title = "正在读取例题详情…",
                    subtitle = "内容来自随安装包内置的题库。",
                )
            state.missing ->
                CEmptyState(
                    title = "没有找到这道例题",
                    description = "该题不在当前题库中，可返回上一个页面换一道看。",
                )
            state.failed ->
                CBar(
                    kind = CBarKind.Warn,
                    title = "例题读取失败，请重试",
                    subtitle = "只重新读取本机数据，不影响已录入的内容。",
                    actionText = "重试",
                    onAction = onRetry,
                )
            state.detail != null ->
                ExampleBody(
                    detail = state.detail,
                    relatedIdiomIds = relatedIdiomIds,
                    wordIndex = wordIndex,
                    colors = colors,
                    onOpenIdiom = onOpenIdiom,
                )
        }
    }
}

@Composable
private fun DetailHeader(
    onBack: () -> Unit,
    colors: Tokens.ColorSet,
) {
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
            text = "例题详情",
            color = colors.head,
            fontSize = Tokens.FontRole.pageTitle.sp,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(start = Tokens.Space.lmetaGap.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            CTag(text = "标记（预留）", style = CTagStyle.Ghost)
        }
    }
}

@Composable
private fun ExampleBody(
    detail: ExampleDetailView,
    relatedIdiomIds: List<String>,
    wordIndex: Map<String, String>,
    colors: Tokens.ColorSet,
    onOpenIdiom: (String) -> Unit,
) {
    // ②出处行：年份·地区·考试类型·题型
    CCard {
        BodyText(text = detail.sourceLine, colors = colors)
    }

    // ③题干（成语可点）+ ④四个选项（成语同样可点）
    CCard {
        CLabel(text = "题干")
        AnnotatedBody(
            text = detail.stem,
            wordIndex = wordIndex,
            colors = colors,
            onOpenIdiom = onOpenIdiom,
            strong = true,
        )
        OptionRow(letter = 'A', option = detail.optionA, wordIndex, colors, onOpenIdiom)
        OptionRow(letter = 'B', option = detail.optionB, wordIndex, colors, onOpenIdiom)
        OptionRow(letter = 'C', option = detail.optionC, wordIndex, colors, onOpenIdiom)
        OptionRow(letter = 'D', option = detail.optionD, wordIndex, colors, onOpenIdiom)
    }

    // ⑥正确答案 + 一句解析（只说明为什么选这项，决策 5）
    CCard {
        CLabel(text = "正确答案与解析")
        Text(
            text = "正确答案：${detail.answer}",
            color = colors.text,
            fontSize = Tokens.FontRole.body.sp,
            fontWeight = FontWeight.W600,
        )
        AnnotatedBody(
            text = detail.explain,
            wordIndex = wordIndex,
            colors = colors,
            onOpenIdiom = onOpenIdiom,
            topPad = true,
        )
    }

    // ⑧关联成语区：一题多成语并排展示全部关联成语，每个可点进 P05；未关联显示「暂未关联成语」。
    CCard {
        CLabel(text = "本题关联成语")
        if (relatedIdiomIds.isEmpty()) {
            BodyText(text = "暂未关联成语", colors = colors)
        } else {
            RelatedIdiomTags(relatedIdiomIds, wordIndex, onOpenIdiom)
        }
    }

    // ⑤做题判分：二期（B6 进阶），本期不出现先选后判。
}

@Composable
private fun OptionRow(
    letter: Char,
    option: String,
    wordIndex: Map<String, String>,
    colors: Tokens.ColorSet,
    onOpenIdiom: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Tokens.Space.lmetaTop.dp),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
    ) {
        Text(
            text = letter.toString(),
            color = colors.text3,
            fontSize = Tokens.FontRole.body.sp,
            fontWeight = FontWeight.W600,
        )
        AnnotatedBodyWeight(
            text = option,
            wordIndex = wordIndex,
            colors = colors,
            onOpenIdiom = onOpenIdiom,
            strong = false,
        )
    }
}

/** 关联成语 Tag 并排（可换行），每个可点跳 P05（验收 6）。视觉保持高保真稿 P07 的静态 Tag。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedIdiomTags(
    relatedIdiomIds: List<String>,
    wordIndex: Map<String, String>,
    onOpenIdiom: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(top = Tokens.Space.lmetaTop.dp),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.lmetaGap.dp),
    ) {
        relatedIdiomIds.forEach { idiomId ->
            CTag(
                text = wordIndex[idiomId] ?: ExampleDetail.PLACEHOLDER,
                modifier = Modifier.clickable(onClick = { onOpenIdiom(idiomId) }),
            )
        }
    }
}

/** 把命中成语高亮为可点文本（accent + 下划线），其余为普通正文。 */
@Composable
private fun AnnotatedBody(
    text: String,
    wordIndex: Map<String, String>,
    colors: Tokens.ColorSet,
    onOpenIdiom: (String) -> Unit,
    strong: Boolean = false,
    topPad: Boolean = false,
) {
    AnnotatedBodyWeight(
        text = text,
        wordIndex = wordIndex,
        colors = colors,
        onOpenIdiom = onOpenIdiom,
        strong = strong,
        topPad = topPad,
    )
}

@Composable
private fun AnnotatedBodyWeight(
    text: String,
    wordIndex: Map<String, String>,
    colors: Tokens.ColorSet,
    onOpenIdiom: (String) -> Unit,
    strong: Boolean,
    topPad: Boolean = false,
) {
    val hits = remember(text, wordIndex) { IdiomHighlight.match(text, wordIndex) }
    val annotated = remember(text, hits, colors, onOpenIdiom) { annotatedText(text, hits, colors, onOpenIdiom) }
    val baseColor = if (strong) colors.text else colors.text2
    val baseWeight = if (strong) FontWeight.W600 else FontWeight.Normal
    val baseModifier = if (topPad) Modifier.padding(top = Tokens.Space.lsubTop.dp) else Modifier
    if (hits.isEmpty()) {
        Text(
            text = text,
            color = baseColor,
            fontSize = Tokens.FontRole.body.sp,
            fontWeight = baseWeight,
            modifier = baseModifier,
        )
        return
    }
    Text(
        text = annotated,
        color = baseColor,
        fontSize = Tokens.FontRole.body.sp,
        fontWeight = baseWeight,
        modifier = baseModifier,
    )
}

/** 命中成语段 → accent + 下划线 + 可点链接（LinkAnnotation.Clickable 的 tag 即 idiomId，点按跳 P05），非命中段 → 普通文本。颜色取令牌 accent，不写字面量。 */
private fun annotatedText(
    text: String,
    hits: List<IdiomHit>,
    colors: Tokens.ColorSet,
    onOpenIdiom: (String) -> Unit,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var cursor = 0
    hits.forEach { hit ->
        if (hit.start > cursor) builder.append(text.substring(cursor, hit.start))
        builder.withLink(
            LinkAnnotation.Clickable(
                tag = hit.idiomId,
                styles =
                    TextLinkStyles(
                        style = SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline),
                    ),
                linkInteractionListener = { link -> onOpenIdiom((link as LinkAnnotation.Clickable).tag) },
            ),
        ) {
            append(text.substring(hit.start, hit.end))
        }
        cursor = hit.end
    }
    if (cursor < text.length) builder.append(text.substring(cursor))
    return builder.toAnnotatedString()
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
