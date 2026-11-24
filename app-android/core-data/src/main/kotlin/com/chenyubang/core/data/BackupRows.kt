package com.chenyubang.core.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/** 备份导入的 raw SQLite 写入原语；只碰六张用户表，imagePath 一律写 NULL（原图不随包）。 */
internal object BackupRows {
    private const val CLEAR_ORDER = "user_example_idiom,mistake_idiom,review_log,mistake,user_example,idiom_state"
    private val converters = Converters()

    fun clear(sqlite: SupportSQLiteDatabase) {
        CLEAR_ORDER.split(',').forEach { sqlite.execSQL("DELETE FROM $it") }
    }

    fun mistake(
        sqlite: SupportSQLiteDatabase,
        row: BackupMistake,
    ) {
        sqlite.execSQL(
            "INSERT INTO mistake (id, userExampleId, imagePath, ocrText, stem, optionA, optionB, optionC, " +
                "optionD, myAnswer, reason, tagNames, examType, year, region, state, createdAt, " +
                "lastReviewedAt, reviewCount) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(
                row.id, row.userExampleId, null, row.ocrText, row.stem,
                row.options.a, row.options.b, row.options.c, row.options.d, row.myAnswer,
                row.reason, converters.fromList(row.tagNames), row.examType, row.year, row.region,
                row.state, row.createdAt, row.lastReviewedAt, row.reviewCount,
            ),
        )
        mistakeLinks(sqlite, row.id, row.idiomIds)
    }

    fun userExample(
        sqlite: SupportSQLiteDatabase,
        row: BackupUserExample,
    ) {
        sqlite.execSQL(
            "INSERT INTO user_example (id, imagePath, ocrText, stem, optionA, optionB, optionC, optionD, " +
                "myAnswer, isMistake, examType, year, region, createdAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(
                row.id, null, row.ocrText, row.stem, row.optionA, row.optionB, row.optionC, row.optionD,
                row.myAnswer, if (row.isMistake) 1 else 0, row.examType, row.year, row.region, row.createdAt,
            ),
        )
        exampleLinks(sqlite, row.id, row.idiomIds)
    }

    fun review(
        sqlite: SupportSQLiteDatabase,
        row: BackupReview,
    ) {
        sqlite.execSQL(
            "INSERT INTO review_log (id, mistakeId, result, at) VALUES (?,?,?,?)",
            arrayOf(row.id, row.mistakeId, row.result, row.at),
        )
    }

    fun idiomState(
        sqlite: SupportSQLiteDatabase,
        row: BackupIdiomState,
    ) {
        sqlite.execSQL(
            "INSERT OR REPLACE INTO idiom_state (idiomId, status, favorite) VALUES (?,?,?)",
            arrayOf(row.idiomId, row.status, if (row.favorite) 1 else 0),
        )
    }

    /** scopes[] 无 id；保留本机同键 id，新增 scope 现分配 uuid（与 ExamScopeStore 同口径）。 */
    fun scope(
        sqlite: SupportSQLiteDatabase,
        row: BackupScope,
        id: String? = null,
    ) {
        sqlite.execSQL(
            "INSERT OR REPLACE INTO exam_scope (id, type, province, enabled) VALUES (?,?,?,?)",
            arrayOf(id ?: UUID.randomUUID().toString().lowercase(), row.type, row.province, if (row.enabled) 1 else 0),
        )
    }

    fun mistakeLinks(
        sqlite: SupportSQLiteDatabase,
        mistakeId: String,
        idiomIds: List<String>,
    ) {
        idiomIds.forEach { idiomId ->
            sqlite.execSQL(
                "INSERT OR IGNORE INTO mistake_idiom (mistakeId, idiomId) VALUES (?,?)",
                arrayOf(mistakeId, idiomId),
            )
        }
    }

    fun exampleLinks(
        sqlite: SupportSQLiteDatabase,
        userExampleId: String,
        idiomIds: List<String>,
    ) {
        idiomIds.forEach { idiomId ->
            sqlite.execSQL(
                "INSERT OR IGNORE INTO user_example_idiom (userExampleId, idiomId) VALUES (?,?)",
                arrayOf(userExampleId, idiomId),
            )
        }
    }

    fun count(
        sqlite: SupportSQLiteDatabase,
        table: String,
    ): Int =
        sqlite.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst()) { "Missing count result" }
            cursor.getInt(0)
        }

    /** 用户表内部悬空引用计数；内容表的 idiom 引用由 BackupOrphans 导入前过滤。 */
    fun dangling(sqlite: SupportSQLiteDatabase): Int =
        sqlite.query(
            "SELECT (SELECT COUNT(*) FROM user_example_idiom l LEFT JOIN user_example p " +
                "ON p.id = l.userExampleId WHERE p.id IS NULL) + " +
                "(SELECT COUNT(*) FROM mistake_idiom l LEFT JOIN mistake p " +
                "ON p.id = l.mistakeId WHERE p.id IS NULL) + " +
                "(SELECT COUNT(*) FROM review_log r LEFT JOIN mistake m " +
                "ON m.id = r.mistakeId WHERE m.id IS NULL)",
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Missing dangling result" }
            cursor.getInt(0)
        }
}
