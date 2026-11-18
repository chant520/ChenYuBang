package com.chenyubang.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

internal object PackVerification {
    suspend fun stage(
        context: Context,
        pack: ValidatedPack,
        idioms: List<PackIdiom>,
        examples: List<PackExample>,
    ) {
        val temporary =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
        try {
            temporary.withTransaction {
                temporary.idiomDao().upsertAll(idioms.map { it.entity })
                PackContent.insertExamples(temporary.openHelper.writableDatabase, examples)
                temporary.idiomDao().rebuildIdiomFts()
                verify(temporary, pack, idioms, examples)
                verifyFts(temporary, idioms)
            }
        } finally {
            temporary.close()
        }
    }

    suspend fun verify(
        database: AppDatabase,
        pack: ValidatedPack,
        idioms: List<PackIdiom>,
        examples: List<PackExample>,
    ) {
        val sqlite = database.openHelper.writableDatabase
        require(count(sqlite, "SELECT COUNT(*) FROM idiom") >= pack.counts.idioms) { "Pack idiom row count" }
        require(count(sqlite, "SELECT COUNT(*) FROM (SELECT word FROM idiom GROUP BY word HAVING COUNT(*) > 1)") == 0) {
            "Pack word conflicts with retained user content"
        }
        require(count(sqlite, "SELECT COUNT(*) FROM example") == pack.counts.examples) { "Pack example row count" }
        require(count(sqlite, "SELECT COUNT(*) FROM example_idiom") == examples.sumOf { it.idiomIds.size }) {
            "Pack example link count"
        }
        require(count(sqlite, DANGLING_LINKS) == 0) { "Pack dangling references" }
        idioms.forEach { row ->
            require(database.idiomDao().byId(row.entity.id) == row.entity) { "Pack idiom row mismatch" }
        }
        examples.forEach { row ->
            verifyExample(sqlite, row.entity)
            row.idiomIds.forEach { idiomId ->
                sqlite.query(
                    "SELECT 1 FROM example_idiom WHERE exampleId = ? AND idiomId = ?",
                    arrayOf(row.entity.id, idiomId),
                ).use { require(it.moveToFirst()) { "Pack example link mismatch" } }
            }
        }
    }

    fun count(
        database: SupportSQLiteDatabase,
        query: String,
    ): Int =
        database.query(query).use { cursor ->
            check(cursor.moveToFirst()) { "Missing count result" }
            cursor.getInt(0)
        }

    private suspend fun verifyFts(
        database: AppDatabase,
        idioms: List<PackIdiom>,
    ) {
        val dao = database.idiomDao()
        require(count(database.openHelper.writableDatabase, "SELECT COUNT(*) FROM idiom_fts") == idioms.size) {
            "Pack FTS row count"
        }
        idioms.forEach { row ->
            val entity = row.entity
            for (query in listOf(entity.word, entity.pinyin, entity.initials)) {
                require(dao.search(query, idioms.size).any { it.id == entity.id }) { "Pack FTS search mismatch" }
            }
            val first = entity.word.take(1)
            require(dao.hanFtsSearch("\"$first*\"", "\"$first\"", idioms.size).any { it.id == entity.id }) {
                "Pack single-character FTS mismatch"
            }
        }
    }

    private fun verifyExample(
        database: SupportSQLiteDatabase,
        expected: ExampleEntity,
    ) {
        database.query("SELECT * FROM example WHERE id = ?", arrayOf(expected.id)).use { cursor ->
            require(cursor.moveToFirst()) { "Missing pack example" }

            fun field(name: String): String = cursor.getString(cursor.getColumnIndexOrThrow(name))
            val actual =
                ExampleEntity(
                    id = field("id"), stem = field("stem"), optionA = field("optionA"), optionB = field("optionB"),
                    optionC = field("optionC"), optionD = field("optionD"), answer = field("answer"),
                    explain = field("explain"), examType = field("examType"), year = field("year").toInt(),
                    region = field("region"), questionType = field("questionType"),
                    imagePath =
                        cursor.getColumnIndexOrThrow(
                            "imagePath",
                        ).let { if (cursor.isNull(it)) null else field("imagePath") },
                )
            require(actual == expected) { "Pack example row mismatch" }
        }
    }

    private const val DANGLING_LINKS =
        "SELECT COUNT(*) FROM example_idiom AS link " +
            "LEFT JOIN idiom ON idiom.id = link.idiomId LEFT JOIN example ON example.id = link.exampleId " +
            "WHERE idiom.id IS NULL OR example.id IS NULL"
}
