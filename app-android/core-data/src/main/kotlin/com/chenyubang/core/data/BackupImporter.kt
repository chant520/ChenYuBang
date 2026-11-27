package com.chenyubang.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * 4.2 恢复导入（P22）。固定顺序：ZIP 严格校验 → schemaVersion → sha256 → 字段与 counts → P22。
 * 超版 / sha256 不符 / 解析失败均为 DataError.Hard（红，直接拒绝，不进 P22，不猜字段）。
 * 覆盖前在 filesDir/backups 留 backup-before-import-*.zip；事务内替换并校验条数，失败由 Room 回滚。
 */
class BackupImporter(
    private val database: AppDatabase,
    context: Context,
) {
    private val context = context.applicationContext
    private val operations = Mutex()

    /** 只读校验，不碰数据库；返回可安全导入的 ValidatedBackup。 */
    fun validate(input: InputStream): ValidatedBackup {
        val files = guarded("备份文件损坏或 ZIP 条目异常") { BackupArchive.read(input) }
        return guarded("备份文件字段异常或校验失败") { verify(files) }
    }

    /** 把解析/校验的已知异常统一包装为硬错误；硬错误本身原样透传。 */
    private inline fun <T> guarded(
        message: String,
        block: () -> T,
    ): T {
        val outcome = runCatching(block)
        val failure = outcome.exceptionOrNull()
        if (failure == null) return outcome.getOrThrow()
        val error =
            if (failure is DataError.Hard) {
                failure
            } else if (
                failure is IOException ||
                failure is IllegalArgumentException ||
                failure is IllegalStateException
            ) {
                DataError.Hard(message, failure)
            } else {
                failure
            }
        throw error
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
                    } catch (error: android.database.SQLException) {
                        throw DataError.Hard("导入写库失败，已回滚；请用自动留存的备份副本恢复", error)
                    } catch (error: IllegalStateException) {
                        throw DataError.Hard("导入写库失败，已回滚；请用自动留存的备份副本恢复", error)
                    }
                BackupImportResult(mode, written.counts, written.report, safety)
            }
        }

    private suspend fun safetyCopy(): String {
        val dir = File(context.filesDir, "backups")
        dir.mkdirs()
        val name = BackupSpec.SAFETY_PREFIX + BackupTime.safetyStamp() + BackupSpec.ZIP_SUFFIX
        val file = File(dir, name)
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
        val sha = manifest.string("sha256")
        val declared = BackupDocument.declaredCounts(manifest)
        val mismatch =
            when {
                version > BackupSpec.SCHEMA_VERSION ->
                    "备份版本 $version 高于当前支持的 ${BackupSpec.SCHEMA_VERSION}，无法安全读取"
                sha != BackupDocument.sha256(data) -> "备份 sha256 重算不符，文件已损坏或被改动"
                declared != BackupDocument.counts(data) -> "备份 counts 与实际条数不符"
                else -> null
            }
        if (mismatch != null) throw DataError.Hard(mismatch)
        return ValidatedBackup(
            version,
            manifest.string("appVersion"),
            manifest.integer("build"),
            manifest.string("exportedAt"),
            declared,
            sha,
            verifiedSnapshot(data),
        )
    }

    /** 与 validate_schema.py 对齐：按 id 去重检查，并清理 idiomIds 内重复值；重复 id 由 guarded 包成硬错误。 */
    private fun verifiedSnapshot(data: JsonObject): BackupSnapshot {
        val snapshot = BackupDecode.fromJson(data)
        check(snapshot.mistakes.map { it.id }.distinct().size == snapshot.mistakes.size) { "重复的错题 id" }
        check(snapshot.userExamples.map { it.id }.distinct().size == snapshot.userExamples.size) {
            "重复的录入题 id"
        }
        check(snapshot.reviews.map { it.id }.distinct().size == snapshot.reviews.size) { "重复的复习记录 id" }
        check(snapshot.idiomStates.map { it.idiomId }.distinct().size == snapshot.idiomStates.size) {
            "重复的成语状态 idiomId"
        }
        return snapshot.copy(
            mistakes = snapshot.mistakes.map { it.copy(idiomIds = it.idiomIds.distinct()) },
            userExamples = snapshot.userExamples.map { it.copy(idiomIds = it.idiomIds.distinct()) },
        )
    }
}
