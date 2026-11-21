package com.chenyubang.app

import androidx.compose.runtime.Composable
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
) {
    P05IdiomDetailRoute(
        idiomId = idiomId,
        database = database,
        onBack = onBack,
        onOpenMistakes = onOpenMistakes,
        onOpenExample = onOpenExample,
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
    database: AppDatabase,
    actions: P08MistakeActions,
) {
    P08MistakeListRoute(viewMode = viewMode, database = database, actions = actions)
}

@Composable
internal fun IdiomMistakesDestination(
    idiomId: String,
    database: AppDatabase,
    onBack: () -> Unit,
    onOpenIdiom: (String) -> Unit,
) {
    P09IdiomMistakesRoute(
        idiomId = idiomId,
        database = database,
        onBack = onBack,
        onOpenIdiom = onOpenIdiom,
    )
}
