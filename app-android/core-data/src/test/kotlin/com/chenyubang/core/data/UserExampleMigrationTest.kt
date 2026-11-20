package com.chenyubang.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1→v2 迁移（规范 §4.5 v2 方案 / §11.1）：user_example 建表 + mistake.userExampleId 增列，
 * 覆盖正常路径（旧行与索引保留、外键可解析）与中断回滚（事务未提交则整体撤销）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserExampleMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrateV1ToV2KeepsRowsAndAddsTableColumn() {
        helper.createDatabase(DB_NORMAL, 1).use { db ->
            db.execSQL(
                "INSERT INTO mistake " +
                    "(id, stem, optionA, optionB, optionC, optionD, tagNames, state, createdAt, reviewCount) " +
                    "VALUES ('m1', '旧错题', 'A', 'B', 'C', 'D', '[]', '待复习', '2026-11-01T00:00:00Z', 0)",
            )
        }

        helper.runMigrationsAndValidate(DB_NORMAL, 2, true, DbMigrations.MIGRATION_1_2).use { db ->
            assertEquals(1, count(db, "SELECT COUNT(*) FROM mistake"))
            assertTrue(columnNames(db, "mistake").contains("userExampleId"))
            assertNull(
                db.query("SELECT userExampleId FROM mistake WHERE id = 'm1'").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getString(0)
                },
            )
            assertEquals(USER_EXAMPLE_COLUMNS, columnNames(db, "user_example"))
            val indexes = indexNames(db, "mistake")
            assertTrue(indexes.containsAll(listOf("index_mistake_state", "index_mistake_createdAt")))

            // 外键可解析：插入录入题后错题行可 JOIN 回录入题；isMistake 默认 false
            db.execSQL(
                "INSERT INTO user_example (id, stem, createdAt) VALUES ('u1', '新录入题', '2026-11-20T00:00:00Z')",
            )
            db.execSQL("UPDATE mistake SET userExampleId = 'u1' WHERE id = 'm1'")
            assertEquals(
                1,
                count(
                    db,
                    "SELECT COUNT(*) FROM mistake JOIN user_example ON user_example.id = mistake.userExampleId",
                ),
            )
            assertEquals(0, count(db, "SELECT isMistake FROM user_example WHERE id = 'u1'"))
        }
    }

    @Test
    fun interruptedMigrationRollsBack() {
        helper.createDatabase(DB_ROLLBACK, 1).use { db ->
            assertThrows(IllegalStateException::class.java) {
                db.beginTransaction()
                try {
                    DbMigrations.MIGRATION_1_2.migrate(db)
                    // 模拟迁移中断：未标记成功即抛出，事务必须整体回滚
                    error("simulated interruption")
                } finally {
                    db.endTransaction()
                }
            }
            assertEquals(1, db.version)
            assertFalse(columnNames(db, "mistake").contains("userExampleId"))
            assertFalse(tableNames(db).contains("user_example"))
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

    private fun columnNames(
        db: SupportSQLiteDatabase,
        table: String,
    ): List<String> =
        db.query("PRAGMA table_info('$table')").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
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
        const val DB_NORMAL = "user-example-migration.db"
        const val DB_ROLLBACK = "user-example-rollback.db"

        val USER_EXAMPLE_COLUMNS =
            listOf(
                "id", "imagePath", "ocrText", "stem", "optionA", "optionB", "optionC", "optionD",
                "myAnswer", "isMistake", "examType", "year", "region", "createdAt",
            )
    }
}
