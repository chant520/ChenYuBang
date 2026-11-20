package com.chenyubang.app

import android.content.Context
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.data.ExamScopeStore
import com.chenyubang.core.data.LaunchPreferences
import com.chenyubang.core.data.PackImporter
import com.chenyubang.core.data.ValidatedPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LaunchBootstrap(
    val pack: ValidatedPack,
    val provinces: List<String>,
    val preferences: LaunchPreferences,
)

/** 启动装配：校验并导入内置题库、补 P01 首次范围、读回偏好，一次返回。 */
internal suspend fun loadInitialLaunch(
    context: Context,
    importer: PackImporter,
    scopeStore: ExamScopeStore,
): LaunchBootstrap {
    val pack = withContext(Dispatchers.IO) { context.assets.open("pack-v1.0.0.zip").use(importer::validate) }
    importer.import(pack)
    val provinces = scopeStore.availableProvinces()
    val loaded = scopeStore.load()
    if (!loaded.complete) {
        // P01 首次启动即按产品默认写入 exam_scope（国考 + 省考 + 事业编，省考省份默认全选）；
        // 库中已有范围行时 loaded.scope 就是既有值，原样写回，不覆盖用户设置。
        val scope =
            if (loaded.scope == ExamScopeSelection()) loaded.scope.copy(provinces = provinces.toSet()) else loaded.scope
        scopeStore.save(scope, complete = false)
        importer.refreshScopeLinks(pack)
    }
    return LaunchBootstrap(pack, provinces, loaded)
}
