package com.chenyubang.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.PackUpdateController
import com.chenyubang.core.data.PackVersionState
import com.chenyubang.core.data.PackVersionStatus
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CTag
import com.chenyubang.core.ui.tokens.Tokens

/** P16 题库版本与更新（路由 settings/pack）。主态逐字文案见视觉契约第 3.1 节。 */
@Composable
fun P16PackRoute(packUpdates: PackUpdateController) {
    val factory =
        remember(packUpdates) {
            viewModelFactory { initializer { P16PackViewModel(packUpdates) } }
        }
    val vm: P16PackViewModel = viewModel(key = "p16-pack", factory = factory)
    val state by vm.state.collectAsState()
    val current = state ?: return
    P16PackContent(
        state = current,
        onCheck = vm::check,
        onContinueOffline = vm::continueOffline,
    )
}

/** 可测性最小重构：Route 与快照共用同一主体，视觉/文案/令牌与业务逻辑均不变。 */
@Composable
internal fun P16PackContent(
    state: PackVersionState,
    onCheck: () -> Unit = {},
    onContinueOffline: () -> Unit = {},
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.page)
            .verticalScroll(rememberScrollState())
            .paddingScreen(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        if (state.isTest) {
            MockVersionBlock(state)
        } else {
            RealVersionBlock(state)
        }
        UpdateArea(state, onCheck, onContinueOffline)
        Spacer(Modifier.size(Tokens.Space.fabBottom.dp))
    }
}

@Composable
private fun RealVersionBlock(state: PackVersionState) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CLabel("当前题库版本")
    Text(
        text = state.version,
        color = colors.head,
        fontSize = Tokens.FontRole.metric.sp,
        fontWeight = FontWeight.W700,
        lineHeight = Tokens.FontRole.metric.sp,
    )
    Text(
        text = "当前安装包，装好即可离线用",
        color = colors.text3,
        fontSize = Tokens.FontRole.caption.sp,
    )

    CLabel("收录说明")
    Text(
        text = countsLine(state.idiomCount, state.exampleCount),
        color = colors.text2,
        fontSize = Tokens.FontRole.body.sp,
    )
    Text(
        text = "覆盖国考 / 省考 / 事业编，按范围筛选后可用的条数会随之变化。",
        color = colors.text2,
        fontSize = Tokens.FontRole.body.sp,
        lineHeight = (Tokens.FontRole.body * NOTE_LINE_HEIGHT).sp,
    )
}

/** TEST mock 条数单独显示，不与真实题库条数混写、不冒充真实真题（视觉契约第 3.2 节）。 */
@Composable
private fun MockVersionBlock(state: PackVersionState) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CLabel("当前题库版本")
    Text(
        text = state.version,
        color = colors.head,
        fontSize = Tokens.FontRole.metric.sp,
        fontWeight = FontWeight.W700,
        lineHeight = Tokens.FontRole.metric.sp,
    )
    Text(
        text = countsLine(state.mockIdiomCount, state.mockExampleCount),
        color = colors.text2,
        fontSize = Tokens.FontRole.body.sp,
    )
    Text(
        text = state.reason.orEmpty(),
        color = colors.text3,
        fontSize = Tokens.FontRole.caption.sp,
    )
}

@Composable
private fun UpdateArea(
    state: PackVersionState,
    onCheck: () -> Unit,
    onContinueOffline: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    when (state.status) {
        PackVersionStatus.CHECKING -> {
            CButton(text = "正在检查题库更新", onClick = {}, loading = true)
            CheckNote()
        }
        PackVersionStatus.DOWNLOADING -> {
            CButton(text = "正在下载题库更新（${state.progress}%）", onClick = {}, loading = true)
            CheckNote()
        }
        PackVersionStatus.RECOVERABLE_FAIL -> RecoverableArea(state, onCheck, onContinueOffline)
        PackVersionStatus.UPDATE_OK -> {
            CLabel("更新状态")
            CTag("题库已更新")
            CButton(text = "检查题库更新", onClick = onCheck)
            CheckNote()
        }
        PackVersionStatus.UP_TO_DATE -> {
            CLabel("更新状态")
            CTag("已是最新版本")
            Text(
                text = "失败时会在这里显示「检查失败，请检查网络后重试」，版本号不变，可点重试。",
                color = colors.text3,
                fontSize = Tokens.FontRole.caption.sp,
                lineHeight = (Tokens.FontRole.caption * NOTE_LINE_HEIGHT).sp,
            )
            CButton(text = "检查题库更新", onClick = onCheck)
            CheckNote()
        }
        PackVersionStatus.TEST_MOCK -> {
            CButton(text = "重试", onClick = onCheck)
            CheckNote()
        }
        PackVersionStatus.BUILTIN, PackVersionStatus.OFFLINE -> {
            CLabel("更新状态")
            CTag("当前离线")
            Text(
                text = "当前使用本机题库，可随时检查更新。",
                color = colors.text3,
                fontSize = Tokens.FontRole.caption.sp,
                lineHeight = (Tokens.FontRole.caption * NOTE_LINE_HEIGHT).sp,
            )
            CButton(text = "检查题库更新", onClick = onCheck)
            CheckNote()
        }
    }
    Text(
        text = "联网只用于获取或更新题库内容，不改动本机学习记录。",
        color = colors.text3,
        fontSize = Tokens.FontRole.caption.sp,
    )
    Text(
        text = "每次进入成语库页或例题页时自动尝试拉取一次，失败后可在 P16 重试或继续离线使用。\n随安装包内置的题库作离线底座，内置包也没有时才用标注 TEST 的 mock 兜底。",
        color = colors.text3,
        fontSize = Tokens.FontRole.caption.sp,
        lineHeight = (Tokens.FontRole.caption * NOTE_LINE_HEIGHT).sp,
    )
}

@Composable
private fun RecoverableArea(
    state: PackVersionState,
    onCheck: () -> Unit,
    onContinueOffline: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CBar(
        kind = CBarKind.Warn,
        title = "检查失败，请检查网络后重试",
        subtitle = "当前版本号不变，仍是 ${state.version}；题库本身可离线继续用。",
    )
    CButton(text = "重试", onClick = onCheck)
    CButton(text = "继续离线使用", onClick = onContinueOffline, variant = CButtonVariant.Ghost)
    Text(
        text = "失败不影响本机学习记录",
        color = colors.text3,
        fontSize = Tokens.FontRole.caption.sp,
    )
    CCard(soft = true) {
        CLabel("重试前可以确认")
        Text(
            text = "是否连上了网络；下载中断时，重新点一次通常就能接上。",
            color = colors.text2,
            fontSize = Tokens.FontRole.body.sp,
            lineHeight = (Tokens.FontRole.body * NOTE_LINE_HEIGHT).sp,
        )
        Text(
            text = "只获取或更新题库内容，不改动错题、收藏与学习状态。",
            color = colors.text3,
            fontSize = Tokens.FontRole.caption.sp,
        )
    }
}

@Composable
private fun CheckNote() {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Text(
        text = "检查更新只获取题库，不改动本机学习记录。",
        color = colors.text3,
        fontSize = Tokens.FontRole.caption.sp,
    )
}

/** 「成语 N 条 · 真题 M 条」，数字按稿面 <b> 加粗。 */
private fun countsLine(
    idioms: Int,
    examples: Int,
): AnnotatedString =
    buildAnnotatedString {
        append("成语 ")
        withStyle(SpanStyle(fontWeight = FontWeight.W700)) { append("$idioms") }
        append(" 条 · 真题 ")
        withStyle(SpanStyle(fontWeight = FontWeight.W700)) { append("$examples") }
        append(" 条")
    }

private fun Modifier.paddingScreen(): Modifier =
    this.then(
        Modifier.padding(
            horizontal = Tokens.Space.pageX.dp,
            vertical = Tokens.Space.contentTop.dp,
        ),
    )

private const val NOTE_LINE_HEIGHT = 1.65f
