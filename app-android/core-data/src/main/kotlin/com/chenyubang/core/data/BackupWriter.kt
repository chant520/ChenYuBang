package com.chenyubang.core.data

import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

/** 写库结果：counts 为实际写入条数（去重/孤儿过滤后），供 P15 结果条展示。 */
internal data class BackupWriteResult(
    val report: OrphanReport,
    val counts: BackupCounts,
)

/** 合并时的本机键，决定同 id / 同业务键保留本机。 */
private data class LocalKeys(
    val mistakeIds: Set<String>,
    val userExampleIds: Set<String>,
    val reviewIds: Set<String>,
    val stateIds: Set<String>,
    val scopeKeys: Set<String>,
    val scopeIds: Map<String, String>,
)

/**
 * 4.2 写库：覆盖＝同事务清空并整体替换六张用户表；合并＝按 id / 业务键跳过本机已有行。
 * 两种方式都只碰用户表，不触碰 idiom / example / exam_scope 内容结构（exam_scope 仅写范围行）
 * 与 settings.theme；事务内校验条数，任何失败由 Room 回滚。
 */
internal object BackupWriter {
    private val ids: (SupportSQLiteDatabase, String) -> Set<String> = { sqlite, query ->
        sqlite.query(query).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
    }
    private val scopeKey: (BackupScope) -> String = { "${it.type}\u0000${it.province.orEmpty()}" }

    suspend fun overwrite(
        database: AppDatabase,
        backup: ValidatedBackup,
    ): BackupWriteResult =
        database.withTransaction {
            val sqlite = database.openHelper.writableDatabase
            val resolved = BackupOrphans.resolve(backup, references(sqlite, backup, false))
            BackupRows.clear(sqlite)
            val counts = insertAll(sqlite, resolved.snapshot, emptyMap())
            verify(sqlite, counts, BackupCounts())
            BackupWriteResult(resolved.report, counts)
        }

    suspend fun merge(
        database: AppDatabase,
        backup: ValidatedBackup,
    ): BackupWriteResult =
        database.withTransaction {
            val sqlite = database.openHelper.writableDatabase
            val before = snapshotCounts(sqlite)
            val resolved = BackupOrphans.resolve(backup, references(sqlite, backup, true))
            val local = localKeys(sqlite)
            val rows = resolved.snapshot
            val incoming =
                rows.copy(
                    mistakes = rows.mistakes.filter { it.id !in local.mistakeIds },
                    userExamples = rows.userExamples.filter { it.id !in local.userExampleIds },
                    reviews = rows.reviews.filter { it.id !in local.reviewIds },
                    idiomStates = rows.idiomStates.filter { it.idiomId !in local.stateIds },
                    scopes = rows.scopes.filter { scopeKey(it) !in local.scopeKeys },
                )
            val counts = insertAll(sqlite, incoming, local.scopeIds)
            verify(sqlite, counts, before)
            BackupWriteResult(resolved.report, counts)
        }

    private fun references(
        sqlite: SupportSQLiteDatabase,
        backup: ValidatedBackup,
        includeLocalMistakes: Boolean,
    ): BackupReferences {
        val idiomIds = ids(sqlite, "SELECT id FROM idiom")
        val userExampleIds = ids(sqlite, "SELECT id FROM user_example") + backup.snapshot.userExamples.map { it.id }
        val localMistakes = if (includeLocalMistakes) ids(sqlite, "SELECT id FROM mistake") else emptySet()
        return BackupReferences(
            idiomIds = idiomIds,
            userExampleIds = userExampleIds,
            mistakeIds = localMistakes + backup.snapshot.mistakes.map { it.id },
        )
    }

    private fun localKeys(sqlite: SupportSQLiteDatabase): LocalKeys {
        val scopeIds = LinkedHashMap<String, String>()
        sqlite.query("SELECT id, type, province FROM exam_scope").use { cursor ->
            while (cursor.moveToNext()) {
                val province = if (cursor.isNull(2)) "" else cursor.getString(2)
                val key = "${cursor.getString(1)}\u0000$province"
                scopeIds[key] = cursor.getString(0)
            }
        }
        return LocalKeys(
            ids(sqlite, "SELECT id FROM mistake"),
            ids(sqlite, "SELECT id FROM user_example"),
            ids(sqlite, "SELECT id FROM review_log"),
            ids(sqlite, "SELECT idiomId FROM idiom_state"),
            scopeIds.keys,
            scopeIds,
        )
    }

    private fun insertAll(
        sqlite: SupportSQLiteDatabase,
        rows: BackupSnapshot,
        scopeIds: Map<String, String>,
    ): BackupCounts {
        rows.userExamples.forEach { BackupRows.userExample(sqlite, it) }
        rows.mistakes.forEach { BackupRows.mistake(sqlite, it) }
        rows.reviews.forEach { BackupRows.review(sqlite, it) }
        rows.idiomStates.forEach { BackupRows.idiomState(sqlite, it) }
        rows.scopes.forEach { BackupRows.scope(sqlite, it, scopeIds[scopeKey(it)]) }
        return rows.counts()
    }

    private fun snapshotCounts(sqlite: SupportSQLiteDatabase): BackupCounts =
        BackupCounts(
            BackupRows.count(sqlite, "user_example"),
            BackupRows.count(sqlite, "mistake"),
            BackupRows.count(sqlite, "review_log"),
            BackupRows.count(sqlite, "idiom_state"),
            BackupRows.count(sqlite, "exam_scope"),
        )

    private fun verify(
        sqlite: SupportSQLiteDatabase,
        incoming: BackupCounts,
        before: BackupCounts,
    ) {
        check(BackupRows.count(sqlite, "user_example") == before.userExamples + incoming.userExamples) {
            "Import user_example count"
        }
        check(BackupRows.count(sqlite, "mistake") == before.mistakes + incoming.mistakes) { "Import mistake count" }
        check(BackupRows.count(sqlite, "review_log") == before.reviews + incoming.reviews) { "Import review count" }
        check(BackupRows.count(sqlite, "idiom_state") == before.idiomStates + incoming.idiomStates) {
            "Import idiom_state count"
        }
        check(BackupRows.count(sqlite, "exam_scope") == before.scopes + incoming.scopes) { "Import scope count" }
        check(BackupRows.dangling(sqlite) == 0) { "Import dangling rows" }
    }
}
