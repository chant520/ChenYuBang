package com.chenyubang.app

import androidx.compose.runtime.Composable
import com.chenyubang.feature.example.P06ExamplesActions
import com.chenyubang.feature.idiom.P03LibraryActions
import com.chenyubang.feature.idiom.P03LibraryEntry

@Composable
internal fun LibraryRouteContent(
    entry: RouteEntry,
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    LibraryDestination(
        scope = deps.scope,
        database = deps.database,
        entry =
            P03LibraryEntry(
                instance = entry.instance,
                epoch = router.tabEntryEpoch.getValue(AppTab.Idiom),
            ),
        packUpdates = deps.packUpdates,
        actions =
            P03LibraryActions(
                onOpenScope = { onRoute(router.push(Route.Scope)) },
                onOpenPack = { onRoute(router.push(Route.Pack)) },
                onOpenIdiom = { id -> onRoute(router.push(Route.Idiom, id)) },
            ),
    )
}

@Composable
internal fun ExamplesRouteContent(
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
) {
    ExamplesDestination(
        database = deps.database,
        entryEpoch = router.tabEntryEpoch.getValue(router.tab),
        packUpdates = deps.packUpdates,
        actions =
            P06ExamplesActions(
                onOpenPack = { onRoute(router.push(Route.Pack)) },
                onOpenScope = { onRoute(router.push(Route.Scope)) },
                onOpenExample = { id -> onRoute(router.push(Route.Example, id)) },
            ),
    )
}
