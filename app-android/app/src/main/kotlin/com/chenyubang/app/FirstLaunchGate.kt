package com.chenyubang.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.feature.me.ExamScopeMode
import com.chenyubang.feature.me.ExamScopeRequest
import com.chenyubang.feature.me.ExamScopeSaver
import com.chenyubang.feature.me.P14ExamScopeRoute

/**
 * P01 启动页（首次引导）。与 P14 考试范围设置共用 `feature-me` 的同一控件、同一份数据
 * （信息架构 v1.4 决策 F / 1.9）：三项默认已勾选、省考勾选且省份默认全选（省份仅为筛选维度），
 * 「进入」与「跳过」同效，都写 `exam_scope` 后进 P02。
 */
@Composable
fun FirstLaunchGate(
    initial: ExamScopeSelection,
    availableProvinces: List<String>,
    saver: ExamScopeSaver,
    onComplete: (ExamScopeSelection) -> Unit,
) {
    BackHandler {}
    P14ExamScopeRoute(
        request = ExamScopeRequest(ExamScopeMode.Onboarding, initial, availableProvinces, saver),
        onSaved = onComplete,
        onCancel = {},
    )
}
