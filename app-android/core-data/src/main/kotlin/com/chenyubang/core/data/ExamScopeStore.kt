package com.chenyubang.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** P01/P14 共用的独立 Data API；不修改数据库定义、FTS 或迁移。 */
data class ExamScopeSelection(
    val national: Boolean = true,
    val provincial: Boolean = true,
    val institution: Boolean = true,
    val provinces: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = !national && !provincial && !institution
    val provinceMissing: Boolean get() = provincial && provinces.isEmpty()
    val description: String get() =
        listOfNotNull(
            "国考".takeIf { national },
            (if (provinceMissing) "省考（未选省份）" else "省考 · ${provinces.sorted().joinToString("、")}")
                .takeIf { provincial },
            "事业编".takeIf { institution },
        ).joinToString(" · ").ifEmpty { "未选考试范围" }
}

data class LaunchPreferences(val complete: Boolean, val scope: ExamScopeSelection)

class ExamScopeStore(private val database: AppDatabase) {
    suspend fun load(): LaunchPreferences =
        withContext(Dispatchers.IO) {
            val db = database.openHelper.writableDatabase
            val complete =
                db.query("SELECT value FROM settings WHERE key = 'firstLaunch.complete'").use {
                    it.moveToFirst() && it.getString(0) == "true"
                }
            val rows = mutableListOf<Triple<String, String?, Boolean>>()
            db.query("SELECT type, province, enabled FROM exam_scope").use { cursor ->
                while (cursor.moveToNext()) {
                    rows +=
                        Triple(
                            cursor.getString(0),
                            if (cursor.isNull(1)) null else cursor.getString(1),
                            cursor.getInt(2) != 0,
                        )
                }
            }
            val savedProvinces =
                db.query("SELECT value FROM settings WHERE key = 'examScope.provinces'").use {
                    if (it.moveToFirst()) Json.decodeFromString<List<String>>(it.getString(0)).toSet() else null
                }
            val scope =
                if (rows.isEmpty()) {
                    ExamScopeSelection()
                } else {
                    ExamScopeSelection(
                        national = rows.any { it.first == "国考" && it.third },
                        provincial = rows.any { it.first == "省考" && it.third },
                        institution = rows.any { it.first == "事业编" && it.third },
                        provinces =
                            savedProvinces ?: rows.filter { it.first == "省考" && it.third }
                                .mapNotNull { it.second }.toSet(),
                    )
                }
            LaunchPreferences(complete, scope)
        }

    suspend fun availableProvinces(): List<String> =
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.query(
                "SELECT DISTINCT region FROM example WHERE examType = '省考' AND trim(region) != '' ORDER BY region",
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
        }

    /** 写入范围与首启完成标记同一事务；保留既有 scope uuid、禁用不删行。 */
    suspend fun save(
        scope: ExamScopeSelection,
        complete: Boolean = false,
    ): Unit =
        withContext(Dispatchers.IO) {
            val db = database.openHelper.writableDatabase
            db.beginTransaction()
            try {
                db.execSQL("UPDATE exam_scope SET enabled = 0")
                val selections =
                    listOf(
                        Triple("国考", null, scope.national),
                        Triple("事业编", null, scope.institution),
                        Triple("教师编", null, false),
                    ) +
                        if (scope.provinces.isEmpty()) {
                            listOf(Triple("省考", null, scope.provincial))
                        } else {
                            scope.provinces.sorted().map { Triple("省考", it, scope.provincial) }
                        }
                selections.forEach { (type, province, enabled) ->
                    val id =
                        db.query(
                            "SELECT id FROM exam_scope WHERE type = ? AND province IS ? LIMIT 1",
                            arrayOf(type, province),
                        ).use { if (it.moveToFirst()) it.getString(0) else UUID.randomUUID().toString() }
                    db.execSQL(
                        "INSERT OR REPLACE INTO exam_scope (id, type, province, enabled) VALUES (?, ?, ?, ?)",
                        arrayOf(id, type, province, if (enabled) 1 else 0),
                    )
                }
                db.execSQL(
                    "INSERT OR REPLACE INTO settings (key, value) VALUES ('examScope.provinces', ?)",
                    arrayOf(Json.encodeToString(scope.provinces.sorted())),
                )
                if (complete) {
                    db.execSQL(
                        "INSERT OR REPLACE INTO settings (key, value) VALUES ('firstLaunch.complete', 'true')",
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
}
