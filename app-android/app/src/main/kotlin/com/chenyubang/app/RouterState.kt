package com.chenyubang.app

import java.io.Serializable

data class RouteEntry(val route: Route, val id: String? = null, val instance: Long = 0) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

data class ReviewSession(val source: RouteEntry, val baseDepth: Int, val idiomId: String?) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

data class RouterState(
    val tab: AppTab = AppTab.Idiom,
    val stacks: Map<AppTab, List<RouteEntry>> = AppTab.entries.associateWith { listOf(RouteEntry(it.root)) },
    val modal: Route? = null,
    val review: ReviewSession? = null,
    val captureBaseDepth: Int? = null,
    val capturedIdiomId: String? = null,
    /** P17 拍下/选中的本机图片路径；取消或保存完成后清空（信息架构 v1.4 §4.3）。 */
    val captureImagePath: String? = null,
    val sequence: Long = 1,
    val rootTopEpoch: Map<AppTab, Int> = AppTab.entries.associateWith { 0 },
) : Serializable {
    val stack: List<RouteEntry> get() = stacks.getValue(tab)
    val current: RouteEntry get() = stack.last()
    val showTabs: Boolean get() = modal == null && stack.none { it.route.presentation == Presentation.FullScreen }
    val showFab: Boolean get() = showTabs && current.route in RouteRegistry.fabWhitelist

    fun selectTab(target: AppTab): RouterState {
        if (!showTabs) return this
        return if (target == tab) {
            copy(
                stacks = stacks + (tab to listOf(stack.first())),
                rootTopEpoch = rootTopEpoch + (tab to (rootTopEpoch.getValue(tab) + 1)),
            )
        } else {
            copy(tab = target)
        }
    }

    fun push(
        route: Route,
        id: String? = null,
    ): RouterState {
        require(route.presentation == Presentation.Push || route == Route.Search)
        require(!route.key.contains("{id}") || !id.isNullOrBlank())
        return append(RouteEntry(route, id, sequence))
    }

    fun goToMistakes(): RouterState =
        copy(
            tab = AppTab.Mistake,
            stacks = stacks + (AppTab.Mistake to listOf(stacks.getValue(AppTab.Mistake).first())),
            rootTopEpoch = rootTopEpoch + (AppTab.Mistake to (rootTopEpoch.getValue(AppTab.Mistake) + 1)),
        )

    fun present(route: Route): RouterState {
        require(
            (route == Route.ImportMode && current.route == Route.Backup) ||
                (route == Route.Clear && current.route == Route.Me),
        )
        return copy(modal = route)
    }

    fun dismissModal(): RouterState = copy(modal = null)

    /** 确认队列退出由 Host 负责；重试队列返回小结，小结返回来源栈底。 */
    fun back(): RouterState =
        when {
            modal != null -> dismissModal()
            current.route == Route.Summary -> exitReview()
            current.route == Route.Queue && stack.getOrNull(stack.lastIndex - 1)?.route == Route.Summary ->
                withStack(stack.dropLast(1))
            current.route == Route.Queue -> exitReview()
            current.route == Route.Camera -> cancelCapture()
            current.route == Route.Done -> cancelCapture()
            stack.size > 1 -> withStack(stack.dropLast(1))
            else -> this
        }

    internal fun append(entry: RouteEntry): RouterState = withStack(stack + entry).copy(sequence = sequence + 1)

    internal fun replaceTop(entry: RouteEntry): RouterState =
        withStack(stack.dropLast(1) + entry).copy(sequence = sequence + 1)

    internal fun withStack(entries: List<RouteEntry>): RouterState = copy(stacks = stacks + (tab to entries))

    companion object {
        private const val serialVersionUID = 1L
    }
}
