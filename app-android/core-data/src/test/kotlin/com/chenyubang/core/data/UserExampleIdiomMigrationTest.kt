package com.chenyubang.core.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v2→v3 迁移（规范 §4.5 v3 方案 / §11.1）：user_example_idiom 建表、联合主键、两列索引，
 * 以及从既有标错题的 mistake.userExampleId + mistake_idiom 防御性回填；覆盖正常路径与中断回滚。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserExampleIdiomMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrateV2ToV3CreatesTableCompositeKeyAndBackfillsMarkedMistakes() {
        helper.createDatabase(DB_NORMAL, 2).use { db ->
            // 一条标错录入题：u1 指向的错题 m1 勾了 i1 / i2 两个成语 → 回填两行。
            db.execSQL("INSERT INTO user_example (id, stem, createdAt) VALUES ('u1', '标错题', '2026-11-20T00:00:00Z')")
            db.execSQL(
                "INSERT INTO mistake (id, userExampleId, stem, optionA, optionB, optionC, optionD, " +
                    "tagNames, state, createdAt, reviewCount) " +
                    "VALUES ('m1', 'u1', '标错题', 'A', 'B', 'C', 'D', '[]', '待复习', '2026-11-20T00:00:00Z', 0)",
            )
            db.execSQL("INSERT INTO mistake_idiom (mistakeId, idiomId) VALUES ('m1', 'i1')")
            db.execSQL("INSERT INTO mistake_idiom (mistakeId, idiomId) VALUES ('m1', 'i2')")
            // 历史错题 userExampleId 为 NULL：不得回填任何行。
            db.execSQL(
                "INSERT INTO mistake (id, userExampleId, stem, optionA, optionB, optionC, optionD, " +
                    "tagNames, state, createdAt, reviewCount) " +
                    "VALUES ('m0', NULL, '历史错题', 'A', 'B', 'C', 'D', '[]', '待复习', '2026-11-19T00:00:00Z', 0)",
            )
            db.execSQL("INSERT INTO mistake_idiom (mistakeId, idiomId) VALUES ('m0', 'i3')")
        }

        helper.runMigrationsAndValidate(DB_NORMAL, 3, true, DbMigrations.MIGRATION_2_3).use { db ->
            assertTrue(tableNames(db).contains("user_example_idiom"))
            assertEquals(PK_COLUMNS, primaryKeyColumns(db, "user_example_idiom"))
            val indexes = indexNames(db, "user_example_idiom")
            assertTrue(indexes.contains("index_user_example_idiom_userExampleId"))
            assertTrue(indexes.contains("index_user_example_idiom_idiomId"))

            // 防御性回填：只为 userExampleId 非空的错题写入，i1 / i2 各一行。
            assertEquals(2, count(db, "SELECT COUNT(*) FROM user_example_idiom"))
            assertEquals(
                1,
                count(db, "SELECT COUNT(*) FROM user_example_idiom WHERE userExampleId = 'u1' AND idiomId = 'i1'"),
            )
            assertEquals(
                1,
                count(db, "SELECT COUNT(*) FROM user_example_idiom WHERE userExampleId = 'u1' AND idiomId = 'i2'"),
            )
            assertEquals(0, count(db, "SELECT COUNT(*) FROM user_example_idiom WHERE idiomId = 'i3'"))

            // 联合主键：重复 (u1, i1) 插入被拒，仍是两行。
            assertThrows(SQLiteConstraintException::class.java) {
                db.execSQL("INSERT INTO user_example_idiom (userExampleId, idiomId) VALUES ('u1', 'i1')")
            }
            assertEquals(2, count(db, "SELECT COUNT(*) FROM user_example_idiom"))
        }
    }

    @Test
    fun interruptedMigrationRollsBack() {
        helper.createDatabase(DB_ROLLBACK, 2).use { db ->
            assertThrows(IllegalStateException::class.java) {
                db.beginTransaction()
                try {
                    DbMigrations.MIGRATION_2_3.migrate(db)
                    error("simulated interruption")
                } finally {
                    db.endTransaction()
                }
            }
            assertEquals(2, db.version)
            assertFalse(tableNames(db).contains("user_example_idiom"))
        }
    }

    private fun count(
        db: SupportSQLiteDatabase,
        sql: String,
    ): Int =
        db.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun primaryKeyColumns(
        db: SupportSQLiteDatabase,
        table: String,
    ): List<String> =
        db.query("PRAGMA table_info('$table')").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getInt(cursor.getColumnIndexOrThrow("pk")) > 0) {
                        add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                }
            }
        }

    private fun indexNames(
        db: SupportSQLiteDatabase,
        table: String,
    ): List<String> =
        db.query("PRAGMA index_list('$table')").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }

    private fun tableNames(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private companion object {
        const val DB_NORMAL = "user-example-idiom-migration.db"
        const val DB_ROLLBACK = "user-example-idiom-rollback.db"
        val PK_COLUMNS = listOf("userExampleId", "idiomId")
    }
}
