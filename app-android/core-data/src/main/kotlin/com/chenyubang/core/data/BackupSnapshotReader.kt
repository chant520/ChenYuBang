package com.chenyubang.core.data

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从 AppDatabase 的六类用户表只读快照（不触碰 idiom/example/exam_scope 内容表写入）。
 * 关联表 idiomIds 一并投影；各数组按 id / idiomId / type+province / name 升序。
 */
internal object BackupSnapshotReader {
    private const val MISTAKE_LINKS = "SELECT mistakeId, idiomId FROM mistake_idiom"
    private const val EXAMPLE_LINKS = "SELECT userExampleId, idiomId FROM user_example_idiom"
    private val converters = Converters()
    private val str: (Cursor, String) -> String =
        { cursor, name -> cursor.getString(cursor.getColumnIndexOrThrow(name)) }
    private val optStr: (Cursor, String) -> String? = { cursor, name ->
        cursor.getColumnIndex(name).let { if (it < 0 || cursor.isNull(it)) null else cursor.getString(it) }
    }
    private val optInt: (Cursor, String) -> Int? = { cursor, name ->
        cursor.getColumnIndex(name).let { if (it < 0 || cursor.isNull(it)) null else cursor.getInt(it) }
    }
    private val bool: (Cursor, String) -> Boolean =
        { cursor, name -> cursor.getInt(cursor.getColumnIndexOrThrow(name)) != 0 }

    suspend fun read(database: AppDatabase): BackupSnapshot =
        withContext(Dispatchers.IO) {
            val sqlite = database.openHelper.writableDatabase
            val mistakeLinks = links(sqlite, MISTAKE_LINKS, "mistakeId", "idiomId")
            val mistakeList = mistakes(sqlite).map { it.copy(idiomIds = mistakeLinks[it.id].orEmpty()) }
            val exampleLinks = links(sqlite, EXAMPLE_LINKS, "userExampleId", "idiomId")
            val exampleList = userExamples(sqlite).map { it.copy(idiomIds = exampleLinks[it.id].orEmpty()) }
            BackupSnapshot(
                mistakes = mistakeList,
                userExamples = exampleList,
                reviews = reviews(sqlite),
                idiomStates = idiomStates(sqlite),
                scopes = scopes(sqlite),
                tags = tags(mistakeList),
            )
        }

    private fun mistakes(sqlite: SupportSQLiteDatabase): List<BackupMistake> =
        sqlite.query("SELECT * FROM mistake ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        BackupMistake(
                            id = str(cursor, "id"),
                            userExampleId = optStr(cursor, "userExampleId"),
                            imagePath = optStr(cursor, "imagePath"),
                            ocrText = optStr(cursor, "ocrText"),
                            stem = str(cursor, "stem"),
                            options =
                                BackupOptions(
                                    a = str(cursor, "optionA"),
                                    b = str(cursor, "optionB"),
                                    c = str(cursor, "optionC"),
                                    d = str(cursor, "optionD"),
                                ),
                            myAnswer = optStr(cursor, "myAnswer"),
                            reason = optStr(cursor, "reason"),
                            tagNames = converters.toList(str(cursor, "tagNames")),
                            examType = optStr(cursor, "examType"),
                            year = optInt(cursor, "year"),
                            region = optStr(cursor, "region"),
                            state = str(cursor, "state"),
                            createdAt = str(cursor, "createdAt"),
                            lastReviewedAt = optStr(cursor, "lastReviewedAt"),
                            reviewCount = cursor.getInt(cursor.getColumnIndexOrThrow("reviewCount")),
                        ),
                    )
                }
            }
        }

    private fun userExamples(sqlite: SupportSQLiteDatabase): List<BackupUserExample> =
        sqlite.query("SELECT * FROM user_example ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        BackupUserExample(
                            id = str(cursor, "id"),
                            imagePath = optStr(cursor, "imagePath"),
                            ocrText = optStr(cursor, "ocrText"),
                            stem = str(cursor, "stem"),
                            optionA = optStr(cursor, "optionA"),
                            optionB = optStr(cursor, "optionB"),
                            optionC = optStr(cursor, "optionC"),
                            optionD = optStr(cursor, "optionD"),
                            myAnswer = optStr(cursor, "myAnswer"),
                            isMistake = bool(cursor, "isMistake"),
                            examType = optStr(cursor, "examType"),
                            year = optInt(cursor, "year"),
                            region = optStr(cursor, "region"),
                            createdAt = str(cursor, "createdAt"),
                        ),
                    )
                }
            }
        }

    private fun reviews(sqlite: SupportSQLiteDatabase): List<BackupReview> =
        sqlite.query("SELECT id, mistakeId, result, at FROM review_log ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        BackupReview(
                            id = str(cursor, "id"),
                            mistakeId = str(cursor, "mistakeId"),
                            result = str(cursor, "result"),
                            at = str(cursor, "at"),
                        ),
                    )
                }
            }
        }

    private fun idiomStates(sqlite: SupportSQLiteDatabase): List<BackupIdiomState> =
        sqlite.query("SELECT idiomId, status, favorite FROM idiom_state ORDER BY idiomId").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        BackupIdiomState(
                            idiomId = str(cursor, "idiomId"),
                            status = str(cursor, "status"),
                            favorite = bool(cursor, "favorite"),
                        ),
                    )
                }
            }
        }

    private fun scopes(sqlite: SupportSQLiteDatabase): List<BackupScope> =
        sqlite.query("SELECT type, province, enabled FROM exam_scope ORDER BY type, province").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        BackupScope(
                            type = str(cursor, "type"),
                            province = optStr(cursor, "province"),
                            enabled = bool(cursor, "enabled"),
                        ),
                    )
                }
            }
        }

    private fun tags(mistakes: List<BackupMistake>): List<BackupTag> =
        mistakes.flatMap { it.tagNames }.distinct().sorted().map { BackupTag(it) }

    private fun links(
        sqlite: SupportSQLiteDatabase,
        query: String,
        parent: String,
        child: String,
    ): Map<String, List<String>> =
        sqlite.query(query).use { cursor ->
            val result = LinkedHashMap<String, MutableList<String>>()
            while (cursor.moveToNext()) {
                result.getOrPut(str(cursor, parent)) { mutableListOf() } += str(cursor, child)
            }
            result.mapValues { it.value.sorted() }
        }
}
