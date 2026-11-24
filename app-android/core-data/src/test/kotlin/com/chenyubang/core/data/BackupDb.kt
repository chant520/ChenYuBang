package com.chenyubang.core.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking

/** 备份专项单测共用的 in-memory Room 操作；只用真实 Room，不 mock DAO。 */
@Suppress("MagicNumber")
internal object BackupDb {
    const val TAG = "BackupDb"

    fun open(context: Context): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    fun exec(
        database: AppDatabase,
        sql: String,
        args: Array<Any?> = emptyArray(),
    ) {
        database.openHelper.writableDatabase.execSQL(sql, args)
    }

    fun count(
        database: AppDatabase,
        table: String,
    ): Int =
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    fun nullableText(
        database: AppDatabase,
        sql: String,
        args: Array<Any?> = emptyArray(),
    ): String? =
        database.openHelper.writableDatabase.query(sql, args).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }

    fun text(
        database: AppDatabase,
        sql: String,
        args: Array<Any?> = emptyArray(),
    ): String = requireNotNull(nullableText(database, sql, args))

    fun seedIdioms(database: AppDatabase) {
        runBlocking {
            database.idiomDao().upsertAll(
                listOf(BackupFixture.IDIOM_1, BackupFixture.IDIOM_2).mapIndexed { index, id ->
                    IdiomEntity(
                        id = id,
                        word = "成语$index",
                        pinyin = "cheng yu",
                        initials = "cy",
                        sense = "测试释义",
                        color = "red",
                        frequency = 1,
                    )
                },
            )
        }
    }

    /** 本机同 id 行：字段名与备份写入列一致，imagePath 故意留旧值用于验证合并不覆盖。 */
    fun seedLocalRows(database: AppDatabase) {
        val example = BackupFixture.userExample(BackupFixture.EXAMPLE_A, imagePath = "images/local.jpg")
        exec(
            database,
            "INSERT INTO user_example (id, imagePath, stem, optionA, optionB, optionC, optionD, " +
                "myAnswer, isMistake, createdAt) VALUES (?,?,?,?,?,?,?,?,?,?)",
            arrayOf(
                example.id, example.imagePath, example.stem, example.optionA, example.optionB,
                example.optionC, example.optionD, example.myAnswer, 1, example.createdAt,
            ),
        )
        exec(
            database,
            "INSERT INTO mistake (id, userExampleId, imagePath, stem, optionA, optionB, optionC, optionD, " +
                "tagNames, state, createdAt, reviewCount) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(
                BackupFixture.MISTAKE_A, BackupFixture.EXAMPLE_A, "images/local.jpg",
                BackupFixture.STEM, "本机甲", "本机乙", "本机丙", "本机丁", "[]", "已掌握", BackupFixture.UTC, 9,
            ),
        )
        exec(
            database,
            "INSERT INTO review_log (id, mistakeId, result, at) VALUES (?,?,?,?)",
            arrayOf(BackupFixture.REVIEW_A, BackupFixture.MISTAKE_A, "对", BackupFixture.UTC),
        )
        exec(
            database,
            "INSERT INTO idiom_state (idiomId, status, favorite) VALUES (?,?,?)",
            arrayOf(BackupFixture.IDIOM_1, "已掌握", 0),
        )
        exec(
            database,
            "INSERT INTO exam_scope (id, type, province, enabled) VALUES (?,?,?,?)",
            arrayOf("old-scope-id", "国考", "中央", 1),
        )
        exec(
            database,
            "INSERT INTO exam_scope (id, type, province, enabled) VALUES (?,?,?,?)",
            arrayOf("old-province-id", "省考", "河南", 1),
        )
    }

    fun rows(
        database: AppDatabase,
        sql: String,
    ): List<String> =
        database.openHelper.writableDatabase.query(sql).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
}
