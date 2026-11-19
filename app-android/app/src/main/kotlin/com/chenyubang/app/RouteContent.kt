package com.chenyubang.app

import androidx.compose.runtime.Composable

/** 按当前路由渲染目标页；P04/P05 的返回与跳转与其余路由同口径。 */
@Composable
internal fun RouteContent(
    entry: RouteEntry,
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    when (entry.route) {
        Route.Scope ->
            ScopeDestination(
                scope = deps.scope,
                availableProvinces = deps.availableProvinces,
                saver = deps.scopeSaver,
                onSaved = {
                    deps.onScopeSaved(it)
                    onRoute(router.back())
                },
                onBack = { onRoute(router.back()) },
            )
        Route.Library ->
            LibraryDestination(
                scope = deps.scope,
                database = deps.database,
                onOpenScope = { onRoute(router.push(Route.Scope)) },
                onOpenIdiom = { id -> onRoute(router.push(Route.Idiom, id)) },
            )
        Route.Search ->
            SearchDestination(deps.database, { onRoute(router.back()) }) {
                onRoute(router.push(Route.Idiom, it))
            }
        Route.Idiom ->
            IdiomDestination(
                idiomId = requireNotNull(entry.id),
                database = deps.database,
                onBack = { onRoute(router.back()) },
                onOpenMistakes = { onRoute(router.goToMistakes()) },
                onOpenExample = { id -> onRoute(router.push(Route.Example, id)) },
            )
        Route.Example ->
            ExampleDestination(requireNotNull(entry.id), deps.database, { onRoute(router.back()) }) { id ->
                onRoute(router.push(Route.Idiom, id))
            }
        else -> RoutePlaceholder(router, deps.scope, onRoute)
    }
}
