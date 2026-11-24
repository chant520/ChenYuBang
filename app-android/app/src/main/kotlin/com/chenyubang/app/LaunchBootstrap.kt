package com.chenyubang.app

import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.data.ExamScopeStore
import com.chenyubang.core.data.LaunchPreferences
import com.chenyubang.core.data.PackImporter
import com.chenyubang.core.data.PackUpdateCoordinator
import kotlinx.coroutines.CancellationException

internal data class LaunchBootstrap(
    val provinces: List<String>,
    val preferences: LaunchPreferences,
)

/** 启动先恢复已安装 ZIP（含两步提交中断修复），再补首次范围与偏好；启动不联网。 */
internal suspend fun loadInitialLaunch(
    coordinator: PackUpdateCoordinator,
    scopeStore: ExamScopeStore,
    importer: PackImporter,
): LaunchBootstrap {
    coordinator.bootstrap()
    val provinces = scopeStore.availableProvinces()
    val loaded = scopeStore.load()
    if (!loaded.complete) {
        val scope =
            if (loaded.scope == ExamScopeSelection()) loaded.scope.copy(provinces = provinces.toSet()) else loaded.scope
        scopeStore.save(scope, complete = false)
        try {
            importer.refreshScopeLinks()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 启动恢复优先完成；范围链接失败留到下次范围保存时重算。
        }
    }
    return LaunchBootstrap(provinces, loaded)
}
