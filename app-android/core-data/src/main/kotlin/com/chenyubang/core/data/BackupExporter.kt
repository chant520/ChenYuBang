package com.chenyubang.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 4.1 备份导出（P15）。只写 manifest.json + data.json 两个 ZIP 条目，不打包 images/，
 * 不含题库内容表；userExamples[] 属用户数据必须导出。sha256 对 data.json 走 PackCanonical
 * 规范化重算（等价 tools/canonical.py:sha256_of），counts 五项取数组实际条数。
 */
class BackupExporter(private val database: AppDatabase) {
    /** 写出到调用方 OutputStream（系统分享面板另存网盘用）；不关闭该流，全程零上传。 */
    suspend fun exportTo(output: OutputStream): BackupExportResult =
        withContext(Dispatchers.IO) {
            emit(BackupSnapshotReader.read(database), output, fileName(), null)
        }

    /** 在指定目录落盘 backup-YYYYMMDD-HHMMSS.zip（本地时间秒级），返回文件与五项条数。 */
    suspend fun exportToFile(directory: File): BackupExportResult =
        withContext(Dispatchers.IO) {
            val name = fileName()
            val target = File(directory, name)
            FileOutputStream(target).use { emit(BackupSnapshotReader.read(database), it, name, target) }
        }

    private fun emit(
        snapshot: BackupSnapshot,
        output: OutputStream,
        fileName: String,
        file: File?,
    ): BackupExportResult {
        val data = BackupDocument.toJson(snapshot)
        val sha = BackupDocument.sha256(data)
        val counts = snapshot.counts()
        val manifest = BackupDocument.manifest(counts, BackupTime.nowUtc(), sha)
        BackupArchive.write(
            output,
            manifest.toString().toByteArray(Charsets.UTF_8),
            data.toString().toByteArray(Charsets.UTF_8),
        )
        return BackupExportResult(fileName, counts, sha, file)
    }

    private fun fileName(): String = BackupSpec.OUTPUT_PREFIX + BackupTime.fileStamp() + BackupSpec.ZIP_SUFFIX
}
