package com.chenyubang.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chenyubang.feature.mistake.P08MistakeActions
import com.chenyubang.feature.review.ReviewSummary

/** P08 入口动作；队列在点击瞬间变空时停在 P08 给页内中性提示条，正常态按钮已按待复习数置灰。 */
@Composable
internal fun mistakeActions(
    router: RouterState,
    onRoute: (RouterState) -> Unit,
): P08MistakeActions {
    var pageMessage by remember { mutableStateOf<String?>(null) }
    return P08MistakeActions(
        onViewModeChange = { onRoute(router.copy(mistakesView = it)) },
        onOpenIdiom = { id -> onRoute(router.push(Route.IdiomMistakes, id)) },
        onOpenMistake = { id -> onRoute(router.push(Route.Mistake, id)) },
        onCapture = { onRoute(router.startCapture()) },
        onStartReview = { count ->
            if (count <= 0) {
                pageMessage = "今天没有待复习的错题"
            } else {
                onRoute(router.startReview(count))
            }
        },
        pageMessage = pageMessage,
        onDismissMessage = { pageMessage = null },
    )
}

/** P11 / P12 全屏浮层的接线集中在此，避免主分发函数过长。 */
@Composable
internal fun ReviewRouteContent(
    entry: RouteEntry,
    router: RouterState,
    deps: RouteDeps,
    onRoute: (RouterState) -> Unit,
    back: () -> Unit,
) {
    when (entry.route) {
        Route.Queue ->
            ReviewQueueDestination(
                scopeIdiomId = router.review?.idiomId,
                instance = entry.instance,
                database = deps.database,
                actions =
                    ReviewQueueActions(
                        onClose = back,
                        onFinished = { summary -> onRoute(router.finishReview(summary.answerCount, summary)) },
                        onBackToSource = { onRoute(router.exitReview()) },
                    ),
            )
        else ->
            ReviewSummaryDestination(
                // 0 题兜底只留给进程重建等真异常；正常「再复习一轮」已保留上一轮小结。
                summary =
                    router.reviewSummary
                        ?: ReviewSummary(questionCount = 0, answerCount = 0, correctCount = 0, idioms = emptyList()),
                scopeIdiomId = router.review?.idiomId,
                database = deps.database,
                actions =
                    ReviewSummaryActions(
                        onBack = { onRoute(router.back()) },
                        onBackToSource = { onRoute(router.exitReview()) },
                        onAgain = { count -> onRoute(router.retryReview(count)) },
                        onOpenIdiom = { id -> onRoute(router.push(Route.Idiom, id)) },
                    ),
            )
    }
}
