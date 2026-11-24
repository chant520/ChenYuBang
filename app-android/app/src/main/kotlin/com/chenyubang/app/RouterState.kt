package com.chenyubang.app

import com.chenyubang.feature.capture.CropFrame
import com.chenyubang.feature.capture.IdiomRef
import com.chenyubang.feature.capture.OcrDraft
import com.chenyubang.feature.mistake.MistakeViewMode
import com.chenyubang.feature.review.ReviewSummary
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

/** P21 只出不回：Done 页禁用系统返回键；其余路由沿用栈深度 / 浮层口径（参照 RouteHeader）。 */
internal fun systemBackEnabled(router: RouterState): Boolean =
    router.current.route != Route.Done && (router.stack.size > 1 || router.modal != null)

data class RouterState(
    val tab: AppTab = AppTab.Idiom,
    val stacks: Map<AppTab, List<RouteEntry>> = AppTab.entries.associateWith { listOf(RouteEntry(it.root)) },
    val modal: Route? = null,
    val review: ReviewSession? = null,
    /** P11 交出的本轮小结，供 P12 渲染；回栈底或重开一轮时清空。 */
    val reviewSummary: ReviewSummary? = null,
    val captureBaseDepth: Int? = null,
    /** P21 归属列表（候选顺序，手动增补排末尾）；去成语详情取第一条。 */
    val capturedIdioms: List<IdiomRef> = emptyList(),
    /** P17 拍下/选中的本机图片路径；取消或保存完成后清空（信息架构 v1.4 §4.3）。 */
    val captureImagePath: String? = null,
    /** P18 导出的裁切图路径；P19 起只读这份图，框外内容不参与识别。 */
    val captureCropPath: String? = null,
    /** P18 交出的框选与摆正参数；P19 返回 P18 时据此还原（信息架构 v1.4 第 29 条）。 */
    val captureCrop: CropFrame? = null,
    /** P19「下一步」带出的题干与选项草稿；P20 返回时回填（信息架构 v1.4 第 30 条）。 */
    val captureDraft: OcrDraft? = null,
    val sequence: Long = 1,
    val rootTopEpoch: Map<AppTab, Int> = AppTab.entries.associateWith { 0 },
    /** 只标识真实切到主标签根页；P06 据此进页检查，不参与滚动位置保留。 */
    val tabEntryEpoch: Map<AppTab, Int> = AppTab.entries.associateWith { 0 },
    /** P08 当前视图（分组 / 时间 / 考试类型）；跟着路由一起保存，返回不重置，默认分组。 */
    val mistakesView: MistakeViewMode = MistakeViewMode.ByIdiom,
    /** 用户数据变动计数：保存 / 删除 / 拍照录入成功后自增，来源页据此重新读库。 */
    val contentRevision: Long = 0,
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
                tabEntryEpoch = tabEntryEpoch + (tab to (tabEntryEpoch.getValue(tab) + 1)),
            )
        } else {
            copy(
                tab = target,
                tabEntryEpoch = tabEntryEpoch + (target to (tabEntryEpoch.getValue(target) + 1)),
            )
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
            current.route == Route.Crop ->
                withStack(stack.dropLast(1)).copy(
                    captureImagePath = null,
                    captureCropPath = null,
                    captureCrop = null,
                )
            current.route == Route.Done -> cancelCapture()
            stack.size > 1 -> withStack(stack.dropLast(1))
            else -> this
        }

    fun bumpContentRevision(): RouterState = copy(contentRevision = contentRevision + 1)

    internal fun append(entry: RouteEntry): RouterState = withStack(stack + entry).copy(sequence = sequence + 1)

    internal fun replaceTop(entry: RouteEntry): RouterState =
        withStack(stack.dropLast(1) + entry).copy(sequence = sequence + 1)

    internal fun withStack(entries: List<RouteEntry>): RouterState = copy(stacks = stacks + (tab to entries))

    companion object {
        private const val serialVersionUID = 1L
    }
}
