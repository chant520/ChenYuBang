package com.chenyubang.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.chenyubang.core.data.ExamScopeSelection

@Composable
fun FirstLaunchGate(
    initial: ExamScopeSelection,
    availableProvinces: List<String>,
    onSave: suspend (ExamScopeSelection) -> Unit,
    onComplete: (ExamScopeSelection) -> Unit,
) {
    BackHandler {}
    ExamScopeEditor(initial, availableProvinces, true, onSave, onComplete)
}
