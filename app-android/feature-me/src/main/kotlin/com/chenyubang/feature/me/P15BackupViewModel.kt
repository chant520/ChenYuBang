package com.chenyubang.feature.me

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.BackupCounts
import com.chenyubang.core.data.BackupExporter
import com.chenyubang.core.data.BackupImporter
import com.chenyubang.core.data.BackupImportResult
import com.chenyubang.core.data.DataError
import com.chenyubang.core.data.ImportMode
import com.chenyubang.core.data.ValidatedBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ===== 待拍板占位串（视觉契约第 7 节 / 第 6 节第 1、2、3 项；仅替文字，颜色/字号仍走 Tokens）=====
// 待拍板：P15 导出成功条逐字措辞未出稿，收口轮须替换。
const val P15_EXPORT_SUCCESS_TITLE_PLACEHOLDER = "备份完成（占位·待拍板）"
// 待拍板：P15 导出失败红硬错误条逐字串未出稿，收口轮须替换。
const val P15_EXPORT_FAILURE_TITLE_PLACEHOLDER = "导出失败（占位·待拍板）"
// 待拍板：P22 超版 / sha256 不符 / 字段异常硬错误拒绝逐字串未出稿，收口轮须替换。
const val P22_REJECT_TITLE_PLACEHOLDER = "备份无法读取（占位·待拍板）"
// 待拍板：P15 导入写库失败红硬错误条逐字串未出稿，收口轮须替换。
const val P15_IMPORT_FAILURE_TITLE_PLACEHOLDER = "导入失败（占位·待拍板）"
// 待拍板：P15 导入完成结果条逐字串未出稿，收口轮须替换。
const val P15_IMPORT_DONE_TITLE_PLACEHOLDER = "导入完成（占位·待拍板）"
// 待拍板：P15「最近一次备份时间」值位逐字串未出稿，收口轮须替换。
const val P15_LAST_BACKUP_VALUE_PLACEHOLDER = "暂无备份记录（占位·待拍板）"

/** 待拍板：导出成功条条数格式，开工清单第 36 行只要求「成功给条数」，稿未给模板（视觉契约第 6 节第 1 项）。 */
fun p15ExportCountsPlaceholder(c: BackupCounts): String =
    "错题 ${c.mistakes} · 录入题 ${c.userExamples} · 复习 ${c.reviews} · 学习状态 ${c.idiomStates} · 考试范围 ${c.scopes}（占位·待拍板）"

/** 待拍板：导入完成结果条条数与 orphans / safetyCopy 呈现格式稿未给（视觉契约第 1.2 节未覆盖）。 */
fun p15ImportDetailPlaceholder(result: BackupImportResult): String {
    val c = result.imported
    val written = "写入：错题 ${c.mistakes} · 录入题 ${c.userExamples} · 复习 ${c.reviews} · 学习状态 ${c.idiomStates} · 考试范围 ${c.scopes}"
    val o = result.orphans
    val orphan =
        if (o.isEmpty) {
            "孤儿数据：无"
        } else {
            buildString {
                append("孤儿数据：")
                if (o.mistakesWithoutUserExample.isNotEmpty()) append("错题缺录入题 ${o.mistakesWithoutUserExample.size} ")
                if (o.mistakeIdiomsMissing.isNotEmpty()) append("错题关联成语缺失 ${o.mistakeIdiomsMissing.size} ")
                if (o.userExampleIdiomsMissing.isNotEmpty()) append("录入题关联成语缺失 ${o.userExampleIdiomsMissing.size} ")
                if (o.reviewsWithoutMistake.isNotEmpty()) append("复习记录缺错题 ${o.reviewsWithoutMistake.size}")
            }
        }
    val safety =
        result.safetyCopy?.let { "已自动留存导入前副本：$it" }
            ?: "合并模式未生成安全副本"
    return "$written；$orphan；$safety（占位·待拍板）"
}

/** P15 结果区（同一时刻只显示一条）。HardError 的 title 为占位串、detail 为底层诊断。 */
sealed interface P15Banner {
    data class Exported(
        val counts: BackupCounts,
        val fileName: String,
    ) : P15Banner

    data class HardError(
        val title: String,
        val detail: String,
    ) : P15Banner

    data class Imported(
        val result: BackupImportResult,
    ) : P15Banner
}

data class P15BackupUiState(
    val banner: P15Banner? = null,
    /** 校验通过、等待 P22 选择的备份；硬错误或导入结束后清空。 */
    val pendingBackup: ValidatedBackup? = null,
)

/**
 * P15 备份与恢复状态机，P15 页面与 P22 底部 sheet 共用同一实例（由 app 按固定 key 装配）。
 * 只调数据层公开 API：导出写 SAF 流或缓存目录，导入先同步 validate 再按 P22 选择 restore。
 */
class P15BackupViewModel(
    private val appContext: Context,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
) : ViewModel() {
    private val _state = MutableStateFlow(P15BackupUiState())
    val state: StateFlow<P15BackupUiState> = _state.asStateFlow()

    /** 存到手机本地：SAF CreateDocument 拿输出流后导出；exportTo 不关流，这里负责关闭。 */
    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(banner = null)
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        val out =
                            appContext.contentResolver.openOutputStream(uri)
                                ?: error("无法写入所选位置")
                        try {
                            exporter.exportTo(out)
                        } finally {
                            out.close()
                        }
                    }
                _state.value = _state.value.copy(banner = P15Banner.Exported(result.counts, result.fileName))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value =
                    _state.value.copy(
                        banner = P15Banner.HardError(P15_EXPORT_FAILURE_TITLE_PLACEHOLDER, error.message.orEmpty()),
                    )
            }
        }
    }

    /** 另存网盘：先落盘 cacheDir/backups，再把 File URI 交回页面拉起系统分享；全程零上传。 */
    fun exportToFile(
        directory: File,
        onFileReady: (File) -> Unit,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(banner = null)
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        if (!directory.exists()) directory.mkdirs()
                        exporter.exportToFile(directory)
                    }
                _state.value = _state.value.copy(banner = P15Banner.Exported(result.counts, result.fileName))
                result.file?.let(onFileReady)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value =
                    _state.value.copy(
                        banner = P15Banner.HardError(P15_EXPORT_FAILURE_TITLE_PLACEHOLDER, error.message.orEmpty()),
                    )
            }
        }
    }

    /**
     * 选文件后同步校验：超版 / sha256 不符 / 字段异常均为 DataError.Hard，停在 P15 红条不进 P22。
     * 校验通过才置 pendingBackup 并回调页面 present(Route.ImportMode)。
     */
    fun pickImport(
        uri: Uri,
        onValidated: () -> Unit,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(banner = null, pendingBackup = null)
            try {
                val backup =
                    withContext(Dispatchers.IO) {
                        val input =
                            appContext.contentResolver.openInputStream(uri)
                                ?: error("无法读取所选文件")
                        try {
                            importer.validate(input)
                        } finally {
                            input.close()
                        }
                    }
                _state.value = _state.value.copy(pendingBackup = backup)
                onValidated()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (hard: DataError.Hard) {
                _state.value =
                    _state.value.copy(
                        banner = P15Banner.HardError(P22_REJECT_TITLE_PLACEHOLDER, hard.message.orEmpty()),
                    )
            } catch (error: Exception) {
                _state.value =
                    _state.value.copy(
                        banner = P15Banner.HardError(P22_REJECT_TITLE_PLACEHOLDER, error.message.orEmpty()),
                    )
            }
        }
    }

    /** P22 选覆盖 / 合并：结果回 P15 结果区；onFinished(true) 时由 app bumpContentRevision。 */
    fun confirmImport(
        mode: ImportMode,
        onFinished: (Boolean) -> Unit,
    ) {
        val backup = _state.value.pendingBackup ?: return
        viewModelScope.launch {
            try {
                val result = importer.restore(backup, mode)
                _state.value =
                    _state.value.copy(
                        banner = P15Banner.Imported(result),
                        pendingBackup = null,
                    )
                onFinished(true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value =
                    _state.value.copy(
                        banner = P15Banner.HardError(P15_IMPORT_FAILURE_TITLE_PLACEHOLDER, error.message.orEmpty()),
                        pendingBackup = null,
                    )
                onFinished(false)
            }
        }
    }

    /** 取消 / 遮罩关闭 sheet：清空待导入备份，不碰数据库。 */
    fun discardImport() {
        _state.value = _state.value.copy(pendingBackup = null)
    }

    fun dismissBanner() {
        _state.value = _state.value.copy(banner = null)
    }
}
