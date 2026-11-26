package com.chenyubang.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.COverlay
import com.chenyubang.core.ui.CTabBar
import com.chenyubang.core.ui.tokens.Tokens
import com.chenyubang.feature.me.MeActions
import com.chenyubang.feature.me.P13MeContent

/** 路由外壳的固定部件（初始化态 / 退出确认 / 底部标签栏），从 RouterShell 拆出以保持可读。 */

@Composable
internal fun InitializingScreen(
    failed: Boolean,
    onRetry: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(Modifier.padding(Tokens.Space.pageX.dp)) {
        Text(
            if (failed) "本机数据或内置题库初始化失败，请重试" else "正在初始化本机数据与内置题库",
            color = if (failed) colors.warnfg else colors.text2,
            fontSize = Tokens.Font.n13.sp,
        )
        if (failed) CButton("重试初始化", onRetry)
    }
}

@Composable
internal fun AppTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Box(
        Modifier.fillMaxWidth()
            .background(colors.tabbg)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
    ) {
        CTabBar(AppTab.entries.map { it.label }, selectedIndex, onSelect = onSelect)
    }
}

@Composable
internal fun MeRouteContent(
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    P13MeContent(
        scope = deps.scope,
        actions =
            MeActions(
                onOpenScope = { onRoute(router.push(Route.Scope)) },
                onOpenSelfTest = { onRoute(router.startSelfTest()) },
                onOpenBackup = { onRoute(router.push(Route.Backup)) },
                onOpenPack = { onRoute(router.push(Route.Pack)) },
                onClear = { onRoute(router.present(Route.Clear)) },
                onCapture = { onRoute(router.startCapture()) },
            ),
    )
}

@Composable
internal fun ExitReviewOverlay(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    COverlay("放弃本轮复习？", true, onDismiss) {
        Text("退出会丢本轮进度", color = colors.text2, fontSize = Tokens.Font.n12.sp)
        CButton("确认退出", onConfirm)
        CButton("继续复习", onDismiss, variant = CButtonVariant.Ghost)
    }
}
