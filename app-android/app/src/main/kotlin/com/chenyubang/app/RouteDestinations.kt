package com.chenyubang.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.data.PackUpdateController
import com.chenyubang.feature.example.ExampleRefreshTrigger
import com.chenyubang.feature.example.P06ExamplesActions
import com.chenyubang.feature.example.P06ExamplesRoute
import com.chenyubang.feature.example.P07ExampleDetailRoute
import com.chenyubang.feature.idiom.LibraryRefreshTrigger
import com.chenyubang.feature.idiom.P03LibraryActions
import com.chenyubang.feature.idiom.P03LibraryEntry
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
    entry: P03LibraryEntry,
    packUpdates: PackUpdateController,
    actions: P03LibraryActions,
) {
    val refresh = remember(packUpdates) { LibraryRefreshTrigger { packUpdates.check() } }
    P03LibraryRoute(
        scope = scope,
        database = database,
        entry = entry,
        refreshTrigger = refresh,
        actions = actions,
    )
}

@Composable
internal fun ExamplesDestination(
    database: AppDatabase,
    entryEpoch: Int,
    packUpdates: PackUpdateController,
    actions: P06ExamplesActions,
) {
    val refresh = remember(packUpdates) { ExampleRefreshTrigger { packUpdates.check() } }
    P06ExamplesRoute(
        database = database,
        entryEpoch = entryEpoch,
        refreshTrigger = refresh,
        actions = actions,
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
    revision: Long = 0,
) {
    P05IdiomDetailRoute(
        idiomId = idiomId,
        database = database,
        onBack = onBack,
        onOpenMistakes = onOpenMistakes,
        onOpenExample = onOpenExample,
        onReviewIdiom = onReviewIdiom,
        revision = revision,
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
