package com.chenyubang.app

import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.feature.me.ExamScopeSaver

/** 路由分发所需依赖，集中成一个参数避免 RouteContent 参数过多。 */
internal data class RouteDeps(
    val scope: ExamScopeSelection,
    val database: AppDatabase,
    val availableProvinces: List<String>,
    val scopeSaver: ExamScopeSaver,
    val onScopeSaved: (ExamScopeSelection) -> Unit,
)
