package com.chenyubang.app

import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.BackupExporter
import com.chenyubang.core.data.BackupImporter
import com.chenyubang.core.data.ClearData
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.data.ExamScopeStore
import com.chenyubang.core.data.PackImporter
import com.chenyubang.core.data.PackUpdateController
import com.chenyubang.core.data.UserExampleSaver
import com.chenyubang.feature.me.ExamScopeSaver
import com.chenyubang.feature.me.P15BackupViewModel
import com.chenyubang.ocr.OcrEngine

/** 构造保存范围回调，供首次引导与路由分发共用。 */
internal fun makeScopeSaver(
    store: ExamScopeStore,
    importer: PackImporter,
): ExamScopeSaver =
    ExamScopeSaver { scope, complete ->
        store.save(scope, complete = complete)
        importer.refreshScopeLinks()
    }

/** 路由分发所需依赖，集中成一个参数避免 RouteContent 参数过多。 */
internal data class RouteDeps(
    val scope: ExamScopeSelection,
    val database: AppDatabase,
    val availableProvinces: List<String>,
    val scopeSaver: ExamScopeSaver,
    val onScopeSaved: (ExamScopeSelection) -> Unit,
    val ocrEngine: OcrEngine,
    val userExampleSaver: UserExampleSaver,
    val backupExporter: BackupExporter,
    val backupImporter: BackupImporter,
    val packUpdates: PackUpdateController,
    val clearData: ClearData,
    /** P15 页与 P22 sheet 共用的备份 ViewModel，由 RouterShell 注入以避开 RouteContent 参数上限。 */
    val backupVm: P15BackupViewModel? = null,
)
