package com.chenyubang.feature.me

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.ClearData
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CInlineError
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.COverlay
import com.chenyubang.core.ui.tokens.Tokens

/**
 * P23 清空数据确认（居中对话框，路由 dialog/clear）。逐字文案见视觉契约第 4 节。
 * 本页唯一红是「确认清空」红填充按钮；点取消 / 遮罩关闭且数据不动。
 */
@Composable
fun P23ClearDialogRoute(
    database: AppDatabase,
    clearData: ClearData,
    onCleared: () -> Unit,
    onCancel: () -> Unit,
) {
    val factory =
        remember(database, clearData) {
            viewModelFactory { initializer { P23ClearViewModel(database, clearData) } }
        }
    val vm: P23ClearViewModel = viewModel(key = "p23-clear", factory = factory)
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.loadCounts() }

    P23ClearDialogContent(
        state = state,
        onConfirm = { vm.clear(onCleared) },
        onCancel = onCancel,
    )
}

/** 可测性最小重构：Route 与快照共用同一主体，视觉/文案/令牌与业务逻辑均不变。 */
@Composable
internal fun P23ClearDialogContent(
    state: P23ClearUiState,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    COverlay(title = "清空全部学习数据", centered = true, onCancel = onCancel) {
        Text(
            text = "这一步不可撤销。清掉的是你录的东西，不是题库本身。",
            color = colors.text2,
            fontSize = Tokens.FontRole.supporting.sp,
            lineHeight = (Tokens.FontRole.supporting * DESC_LINE_HEIGHT).sp,
        )

        CCard {
            CLabel("会清空")
            Spacer(Modifier.size(Tokens.Space.lsubTop.dp))
            Text(
                text = "错题 ${state.mistakes} 道 · 录入题 ${state.userExamples} 条 · 收藏 ${state.favorites} 个 · 学习状态",
                color = colors.text2,
                fontSize = Tokens.FontRole.body.sp,
            )
        }

        CCard(soft = true) {
            CLabel("不受影响")
            Spacer(Modifier.size(Tokens.Space.lsubTop.dp))
            Text(
                text = "成语库与例题库照常可用，考试范围设置保留",
                color = colors.text2,
                fontSize = Tokens.FontRole.body.sp,
            )
        }

        if (state.failed) {
            CInlineError(P23_CLEAR_FAILURE)
        }
        if (state.cleared) {
            CBar(kind = CBarKind.Info, title = P23_CLEAR_SUCCESS)
        }

        CButton(
            text = if (state.clearing) P23_CLEARING else "确认清空",
            onClick = onConfirm,
            variant = CButtonVariant.DangerFilled,
            loading = state.clearing,
        )
        CButton(text = "取消", onClick = onCancel, variant = CButtonVariant.Ghost)
    }
}

private const val DESC_LINE_HEIGHT = 1.7f
