package com.chenyubang.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 4.3 清空数据（P23，规范 §8.4）。同事务清空六张用户表：user_example / user_example_idiom /
 * mistake / mistake_idiom / review_log / idiom_state；保留 idiom / example / exam_scope /
 * idiom_scope / example_idiom 及 settings.theme。只 upsert settings.lastClearedAt（UTC ISO 秒），
 * 不删除其它 settings 行；事务内校验无孤儿行。
 */
class ClearData(private val database: AppDatabase) {
    suspend fun clear(): ClearResult =
        withContext(Dispatchers.IO) {
            val clearedAt = BackupTime.nowUtc()
            database.withTransaction {
                val sqlite = database.openHelper.writableDatabase
                BackupRows.clear(sqlite)
                sqlite.execSQL(
                    "INSERT OR REPLACE INTO settings (key, value) VALUES ('lastClearedAt', ?)",
                    arrayOf(clearedAt),
                )
                check(BackupRows.dangling(sqlite) == 0) { "Clear left dangling user rows" }
            }
            ClearResult(clearedAt)
        }
}
