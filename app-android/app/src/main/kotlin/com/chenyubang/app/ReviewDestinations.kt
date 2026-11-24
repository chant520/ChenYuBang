package com.chenyubang.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.feature.review.P11ReviewQueueContent
import com.chenyubang.feature.review.P12ReviewSummaryContent
import com.chenyubang.feature.review.ReviewFlow
import com.chenyubang.feature.review.ReviewSummary
import com.chenyubang.feature.review.ReviewViewModel

internal data class ReviewQueueActions(
    val onClose: () -> Unit,
    val onFinished: (ReviewSummary) -> Unit,
    val onBackToSource: () -> Unit,
)

/** P11 复习队列：每次进入新建一轮 ViewModel，不保留中间状态；范围词异步回填。 */
@Composable
internal fun ReviewQueueDestination(
    scopeIdiomId: String?,
    instance: Long,
    database: AppDatabase,
    actions: ReviewQueueActions,
) {
    val factory =
        remember(database, scopeIdiomId) {
            viewModelFactory {
                initializer {
                    ReviewViewModel(
                        scopeIdiomId = scopeIdiomId,
                        loadQueue = { database.reviewDao().queue(it) },
                        record = { mistakeId, result, at -> database.reviewDao().record(mistakeId, result, at) },
                    )
                }
            }
        }
    val model: ReviewViewModel = viewModel(key = "review-queue-$instance", factory = factory)
    val state by model.state.collectAsState()
    var handedOff by remember { mutableStateOf(false) }
    val handOff: (ReviewSummary) -> Unit = { summary ->
        if (!handedOff) {
            handedOff = true
            actions.onFinished(summary)
        }
    }
    LaunchedEffect(scopeIdiomId) {
        val word = scopeIdiomId?.let { database.idiomDao().byId(it)?.word }
        if (word != null) model.setScopeWord(word)
    }
    P11ReviewQueueContent(
        state = state,
        onPick = { model.pick(it) },
        onNext = { model.next() },
        onClose = actions.onClose,
        onBackToSource = actions.onBackToSource,
        onRetry = { model.load() },
        onRetryRecord = { model.retryRecord() },
        onFinish = { handOff(ReviewFlow.summary(state)) },
    )
}

internal data class ReviewSummaryActions(
    val onBack: () -> Unit,
    val onBackToSource: () -> Unit,
    val onAgain: (Int) -> Unit,
    val onOpenIdiom: (String) -> Unit,
)

/** P12 复习小结：题量 / 正确数 / 涉及成语直接来自 P11 交出的本轮汇总。 */
@Composable
internal fun ReviewSummaryDestination(
    summary: ReviewSummary,
    scopeIdiomId: String?,
    database: AppDatabase,
    actions: ReviewSummaryActions,
) {
    var pendingCount by remember { mutableStateOf(0) }
    LaunchedEffect(scopeIdiomId) { pendingCount = database.reviewDao().pendingCount(scopeIdiomId) }
    P12ReviewSummaryContent(
        summary = summary,
        onBack = actions.onBack,
        onBackToSource = actions.onBackToSource,
        onAgain = { if (pendingCount > 0) actions.onAgain(pendingCount) },
        onOpenIdiom = actions.onOpenIdiom,
    )
}
