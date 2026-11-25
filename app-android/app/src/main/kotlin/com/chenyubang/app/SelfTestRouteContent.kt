package com.chenyubang.app

import androidx.compose.runtime.Composable
import com.chenyubang.feature.review.P24SelfTestRoute

/** P13 设置入口与 P24 全屏自测的宿主接线。 */
@Composable
internal fun SelfTestRouteContent(
    entry: RouteEntry,
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    P24SelfTestRoute(
        database = deps.database,
        instance = entry.instance,
        onExit = { onRoute(router.back()) },
    )
}
