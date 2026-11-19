package com.chenyubang.feature.me

import com.chenyubang.core.data.ExamScopeSelection

/**
 * P14 路由入参：从哪来（[mode]）、当前值（[initial]）、可选省份（[availableProvinces]）与写入口。
 * 打包传递后入口只剩「参数 + 两个回调」，调用方不必逐个透传。
 */
data class ExamScopeRequest(
    val mode: ExamScopeMode,
    val initial: ExamScopeSelection,
    val availableProvinces: List<String>,
    val saver: ExamScopeSaver,
)
