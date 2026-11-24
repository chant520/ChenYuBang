package com.chenyubang.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 4.2 恢复导入（P22）。固定顺序：ZIP 严格校验 → schemaVersion → sha256 → 字段与 counts → P22。
 * 超版 / sha256 不符 / 解析失败均为 DataError.Hard（红，直接拒绝，不进 P22，不猜字段）。
 * 覆盖前在 cacheDir 留 backup-before-import-*.zip；事务内替换并校验条数，失败由 Room 回滚。
 */
class BackupImporter(
    private val database: AppDatabase,
    context: Context,
) {
    private val context = context.applicationContext
    private val operations = Mutex()

    /** 只读校验，不碰数据库；返回可安全导入的 ValidatedBackup。 */
    fun validate(input: InputStream): ValidatedBackup {
        val files =
            try {
                BackupArchive.read(input)
            } catch (error: Exception) {
                throw DataError.Hard("备份文件损坏或 ZIP 条目异常", error)
            }
        return try {
            verify(files)
        } catch (error: DataError.Hard) {
            throw error
        } catch (error: Exception) {
            throw DataError.Hard("备份文件字段异常或校验失败", error)
        }
    }

    /** 按 P22 选择写入：覆盖会先留安全副本，合并按 id / 业务键保留本机。全程离线。 */
    suspend fun restore(
        backup: ValidatedBackup,
        mode: ImportMode,
    ): BackupImportResult =
        withContext(Dispatchers.IO) {
            operations.withLock {
                val safety = if (mode == ImportMode.OVERWRITE) safetyCopy() else null
                val written =
                    try {
                        if (mode == ImportMode.OVERWRITE) {
                            BackupWriter.overwrite(database, backup)
                        } else {
                            BackupWriter.merge(database, backup)
                        }
                    } catch (error: Exception) {
                        throw DataError.Hard("导入写库失败，已回滚；请用自动留存的备份副本恢复", error)
                    }
                BackupImportResult(mode, written.counts, written.report, safety)
            }
        }

    private suspend fun safetyCopy(): String {
        val name = BackupSpec.SAFETY_PREFIX + BackupTime.fileStamp() + BackupSpec.ZIP_SUFFIX
        val file = File(context.cacheDir, name)
        val snapshot = BackupSnapshotReader.read(database)
        val data = BackupDocument.toJson(snapshot)
        val sha = BackupDocument.sha256(data)
        val manifest = BackupDocument.manifest(snapshot.counts(), BackupTime.nowUtc(), sha)
        FileOutputStream(file).use { output ->
            BackupArchive.write(
                output,
                manifest.toString().toByteArray(Charsets.UTF_8),
                data.toString().toByteArray(Charsets.UTF_8),
            )
        }
        return file.absolutePath
    }

    private fun verify(files: Map<String, ByteArray>): ValidatedBackup {
        val manifest = BackupSchema.validateManifest(PackJson.parse(files.getValue(BackupSpec.MANIFEST)))
        val data = BackupSchema.validateData(PackJson.parse(files.getValue(BackupSpec.DATA)))
        val version = manifest.integer("schemaVersion")
        if (version > BackupSpec.SCHEMA_VERSION) {
            throw DataError.Hard("备份版本 $version 高于当前支持的 ${BackupSpec.SCHEMA_VERSION}，无法安全读取")
        }
        val sha = manifest.string("sha256")
        if (sha != BackupDocument.sha256(data)) throw DataError.Hard("备份 sha256 重算不符，文件已损坏或被改动")
        val declared = BackupDocument.declaredCounts(manifest)
        if (declared != BackupDocument.counts(data)) throw DataError.Hard("备份 counts 与实际条数不符")
        return ValidatedBackup(
            version,
            manifest.string("appVersion"),
            manifest.integer("build"),
            manifest.string("exportedAt"),
            declared,
            sha,
            BackupDecode.fromJson(data),
        )
    }
}
