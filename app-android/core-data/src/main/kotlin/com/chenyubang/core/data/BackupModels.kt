package com.chenyubang.core.data

import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 备份交换格式常量。schemaVersion 是交换格式版本，与 @Database(version = 3) 无关；
 * 包名、ZIP 条目、sha256 口径见 shared/schema/backup-{manifest,data}.schema.json。
 */
internal object BackupSpec {
    const val SCHEMA_VERSION = 3
    const val APP_VERSION = "0.10.0"
    const val BUILD = 1
    const val MANIFEST = "manifest.json"
    const val DATA = "data.json"
    const val SAFETY_PREFIX = "backup-before-import-"
    const val OUTPUT_PREFIX = "backup-"
    const val ZIP_SUFFIX = ".zip"
}

/** 时间口径：导出/清空写 UTC ISO 秒（无小数）；包名用本地时间秒级。 */
internal object BackupTime {
    private val UTC =
        DateTimeFormatter.ISO_INSTANT
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val SAFETY_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

    fun nowUtc(): String = UTC.format(Instant.now().truncatedTo(ChronoUnit.SECONDS))

    fun fileStamp(): String = STAMP.format(LocalDateTime.now())

    /** 安全副本名：毫秒时间戳 + UUID 后缀，避免同秒两次覆盖导入相互覆盖。 */
    fun safetyStamp(): String = SAFETY_STAMP.format(LocalDateTime.now()) + "-" + UUID.randomUUID()
}

/**
 * 错误分级（规范 §9.1）：Hard=红硬错误（备份包损坏、超版、写库失败），
 * Recoverable=橙可恢复异常（题库检查/下载失败，保留当前版本供重试）。UI 按子类型映射红/橙。
 */
sealed class DataError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Hard(
        message: String,
        cause: Throwable? = null,
    ) : DataError(message, cause)

    class Recoverable(
        message: String,
        cause: Throwable? = null,
    ) : DataError(message, cause)
}

/** P22 两种导入方式。覆盖会丢现有数据、合并按 id/业务键保留本机。 */
enum class ImportMode {
    OVERWRITE,
    MERGE,
}

/** manifest.counts 的五项（tags 只是展示投影，不进 counts）。 */
data class BackupCounts(
    val mistakes: Int = 0,
    val userExamples: Int = 0,
    val reviews: Int = 0,
    val idiomStates: Int = 0,
    val scopes: Int = 0,
)

/** mistakes[].options 对象，对应 mistake.optionA..D（备份形状用对象而非平铺列）。 */
data class BackupOptions(
    val a: String,
    val b: String,
    val c: String,
    val d: String,
)

/** 一条备份错题：字段对齐 backup-data.schema.json mistakes[]。imagePath 导出原值、导入恒 NULL。备份导入态允许 idiomIds 为空。 */
@Suppress("LongParameterList")
data class BackupMistake(
    val id: String,
    val userExampleId: String? = null,
    val imagePath: String? = null,
    val ocrText: String? = null,
    val stem: String,
    val options: BackupOptions,
    val myAnswer: String? = null,
    val reason: String? = null,
    val tagNames: List<String> = emptyList(),
    val examType: String? = null,
    val year: Int? = null,
    val region: String? = null,
    val state: String,
    val createdAt: String,
    val lastReviewedAt: String? = null,
    val reviewCount: Int = 0,
    val idiomIds: List<String> = emptyList(),
)

/**
 * 一条用户录入题：optionA..D 平铺、未标错题只在这里。
 * 备份导入态允许 idiomIds 为空（导出后关联成语全部失效时保留本体）；正常录入仍须至少一条。
 */
@Suppress("LongParameterList")
data class BackupUserExample(
    val id: String,
    val imagePath: String? = null,
    val ocrText: String? = null,
    val stem: String,
    val optionA: String? = null,
    val optionB: String? = null,
    val optionC: String? = null,
    val optionD: String? = null,
    val myAnswer: String? = null,
    val isMistake: Boolean = false,
    val examType: String? = null,
    val year: Int? = null,
    val region: String? = null,
    val createdAt: String,
    val idiomIds: List<String> = emptyList(),
)

data class BackupReview(
    val id: String,
    val mistakeId: String,
    val result: String,
    val at: String,
)

data class BackupIdiomState(
    val idiomId: String,
    val status: String,
    val favorite: Boolean,
)

/** exam_scope 投影：scopes[] 无 id 字段（schema additionalProperties:false）。 */
data class BackupScope(
    val type: String,
    val province: String?,
    val enabled: Boolean,
)

data class BackupTag(
    val name: String,
)

/** data.json 的六数组。各数组已按 id / idiomId / type+province / name 升序。 */
@Suppress("LongParameterList")
data class BackupSnapshot(
    val mistakes: List<BackupMistake> = emptyList(),
    val userExamples: List<BackupUserExample> = emptyList(),
    val reviews: List<BackupReview> = emptyList(),
    val idiomStates: List<BackupIdiomState> = emptyList(),
    val scopes: List<BackupScope> = emptyList(),
    val tags: List<BackupTag> = emptyList(),
) {
    fun counts(): BackupCounts =
        BackupCounts(mistakes.size, userExamples.size, reviews.size, idiomStates.size, scopes.size)
}

/** 通过全部硬校验的备份：可安全交给 import，不携带未校验原始字节。 */
@Suppress("LongParameterList")
data class ValidatedBackup(
    val schemaVersion: Int,
    val appVersion: String,
    val build: Int,
    val exportedAt: String,
    val counts: BackupCounts,
    val sha256: String,
    val snapshot: BackupSnapshot,
)

/** 脏数据报告：孤儿按规则上报但不猜字段；缺失关联只跳过关联，不丢弃整条记录。 */
@Suppress("LongParameterList")
data class OrphanReport(
    val mistakesWithoutUserExample: List<String> = emptyList(),
    val mistakeIdiomsMissing: Map<String, List<String>> = emptyMap(),
    val userExampleIdiomsMissing: Map<String, List<String>> = emptyMap(),
    val reviewsWithoutMistake: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            mistakesWithoutUserExample.isEmpty() &&
                mistakeIdiomsMissing.isEmpty() &&
                userExampleIdiomsMissing.isEmpty() &&
                reviewsWithoutMistake.isEmpty()
}

/** 导出结果：fileName 为 backup-YYYYMMDD-HHMMSS.zip；file 仅本地落盘重载非 null。 */
data class BackupExportResult(
    val fileName: String,
    val counts: BackupCounts,
    val sha256: String,
    val file: File? = null,
)

/** 导入结果：imported 为实际写入条数（去重/孤儿过滤后），orphans 供 P15 展示。 */
data class BackupImportResult(
    val mode: ImportMode,
    val imported: BackupCounts,
    val orphans: OrphanReport,
    val safetyCopy: String? = null,
)

/** 清空结果：clearedAt 为写入 settings.lastClearedAt 的 UTC 秒。 */
data class ClearResult(
    val clearedAt: String,
)
