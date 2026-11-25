package com.chenyubang.feature.review

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.IdiomEntity
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.COverlay

/** P24 每次进入从 P13 新建 ViewModel；退出确认与系统返回都丢弃本轮内存状态。 */
@Composable
fun P24SelfTestRoute(
    database: AppDatabase,
    instance: Long,
    onExit: () -> Unit,
) {
    val factory =
        remember(database) {
            viewModelFactory {
                initializer {
                    SelfTestViewModel(
                        loadData = {
                            val scoped = database.idiomDao().libraryIdioms().map(IdiomEntity::toCandidate)
                            val library = database.idiomMatchDao().allIdioms().map(IdiomEntity::toCandidate)
                            scoped to library
                        },
                    )
                }
            }
        }
    val model = rememberP24SelfTestViewModel(instance, factory)
    val state by model.state.collectAsState()
    var confirmExit by remember { mutableStateOf(false) }
    val unfinished = state is SelfTestUiState.Question
    val requestExit = { if (unfinished) confirmExit = true else onExit() }
    BackHandler(enabled = true, onBack = requestExit)
    P24SelfTestContent(
        state = state,
        onPick = model::pick,
        onNext = model::next,
        onRetry = model::retry,
        onRetryRound = model::retry,
        onExit = requestExit,
    )
    if (confirmExit) {
        SelfTestExitOverlay(
            onContinue = { confirmExit = false },
            onConfirm = {
                confirmExit = false
                onExit()
            },
        )
    }
}

internal fun p24SelfTestViewModelKey(instance: Long): String = "p24-self-test-$instance"

@Composable
internal fun rememberP24SelfTestViewModel(
    instance: Long,
    factory: ViewModelProvider.Factory,
): SelfTestViewModel = viewModel(key = p24SelfTestViewModelKey(instance), factory = factory)

@Composable
internal fun SelfTestExitOverlay(
    onContinue: () -> Unit,
    onConfirm: () -> Unit,
) {
    COverlay("退出本轮自测？", centered = true, onCancel = onContinue) {
        CButton("继续答题", onContinue, variant = CButtonVariant.Ghost)
        CButton("退出", onConfirm, variant = CButtonVariant.DangerOutline)
    }
}

private fun IdiomEntity.toCandidate(): SelfTestCandidate = SelfTestCandidate(id = id, word = word, sense = sense)
