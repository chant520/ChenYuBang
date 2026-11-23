package com.chenyubang.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.feature.example.P07ExampleDetailRoute
import com.chenyubang.feature.idiom.P03LibraryRoute
import com.chenyubang.feature.idiom.P04SearchRoute
import com.chenyubang.feature.idiom.P05IdiomDetailRoute
import com.chenyubang.feature.me.ExamScopeMode
import com.chenyubang.feature.me.ExamScopeRequest
import com.chenyubang.feature.me.ExamScopeSaver
import com.chenyubang.feature.me.P14ExamScopeRoute
import com.chenyubang.feature.mistake.MistakeViewMode
import com.chenyubang.feature.mistake.P08MistakeActions
import com.chenyubang.feature.mistake.P08MistakeListRoute
import com.chenyubang.feature.mistake.P09IdiomMistakesRoute
import com.chenyubang.feature.mistake.P10UserExampleRoute
import com.chenyubang.feature.review.P11ReviewQueueContent
import com.chenyubang.feature.review.P12ReviewSummaryContent
import com.chenyubang.feature.review.ReviewFlow
import com.chenyubang.feature.review.ReviewSummary
import com.chenyubang.feature.review.ReviewViewModel

/**
 * 已接入页面的目的地装配：只做「feature 页面 ← app 状态/写入口」的接线，
 * 不放业务逻辑，也不新增编排（规范 §3：app 负责编排与 Data 注入）。
 */

@Composable
internal fun ScopeDestination(
    scope: ExamScopeSelection,
    availableProvinces: List<String>,
    saver: ExamScopeSaver,
    onSaved: (ExamScopeSelection) -> Unit,
    onBack: () -> Unit,
) {
    P14ExamScopeRoute(
        request = ExamScopeRequest(ExamScopeMode.Settings, scope, availableProvinces, saver),
        onSaved = onSaved,
        onCancel = onBack,
    )
}

@Composable
internal fun LibraryDestination(
    scope: ExamScopeSelection,
    database: AppDatabase,
    onOpenScope: () -> Unit,
    onOpenIdiom: (String) -> Unit,
) {
    P03LibraryRoute(
        scope = scope,
        database = database,
        onOpenScope = onOpenScope,
        onOpenIdiom = onOpenIdiom,
    )
}

@Composable
internal fun SearchDestination(
    database: AppDatabase,
    onClose: () -> Unit,
    onOpenIdiom: (String) -> Unit,
) {
    P04SearchRoute(
        database = database,
        onClose = onClose,
        onOpenIdiom = onOpenIdiom,
    )
}

@Composable
internal fun IdiomDestination(
    idiomId: String,
    database: AppDatabase,
    onBack: () -> Unit,
    onOpenMistakes: () -> Unit,
    onOpenExample: (String) -> Unit,
    onReviewIdiom: (Int) -> Unit = {},
) {
    P05IdiomDetailRoute(
        idiomId = idiomId,
        database = database,
        onBack = onBack,
        onOpenMistakes = onOpenMistakes,
        onOpenExample = onOpenExample,
        onReviewIdiom = onReviewIdiom,
    )
}

@Composable
internal fun ExampleDestination(
    exampleId: String,
    database: AppDatabase,
    onBack: () -> Unit,
    onOpenIdiom: (String) -> Unit,
) {
    P07ExampleDetailRoute(
        exampleId = exampleId,
        database = database,
        onBack = onBack,
        onOpenIdiom = onOpenIdiom,
    )
}

@Composable
internal fun MistakesDestination(
    viewMode: MistakeViewMode,
    revision: Long,
    database: AppDatabase,
    actions: P08MistakeActions,
) {
    P08MistakeListRoute(viewMode = viewMode, revision = revision, database = database, actions = actions)
}

@Composable
internal fun IdiomMistakesDestination(
    idiomId: String,
    database: AppDatabase,
    onBack: () -> Unit,
    onOpenIdiom: (String) -> Unit,
    onOpenExample: (String) -> Unit,
    revision: Long = 0,
) {
    P09IdiomMistakesRoute(
        idiomId = idiomId,
        database = database,
        onBack = onBack,
        onOpenIdiom = onOpenIdiom,
        onOpenExample = onOpenExample,
        revision = revision,
    )
}

@Composable
internal fun UserExampleDestination(
    exampleId: String,
    database: AppDatabase,
    onBack: () -> Unit,
    onOpenIdiom: (String) -> Unit,
    onDataChanged: () -> Unit = {},
) {
    val filesDir = LocalContext.current.filesDir
    P10UserExampleRoute(
        exampleId = exampleId,
        database = database,
        filesDir = filesDir,
        onBack = onBack,
        onOpenIdiom = onOpenIdiom,
        onDataChanged = onDataChanged,
    )
}

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
