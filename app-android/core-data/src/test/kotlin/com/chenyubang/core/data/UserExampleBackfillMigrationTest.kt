package com.chenyubang.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v3→v4 迁移（规范 4.5 节 v4 方案 / 11.1 节）：未标错且无任何归属的录入题按确定性文本规则回填。
 * 覆盖正常路径、中断回滚、多命中、无命中、悬空数据与二次执行幂等。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserExampleBackfillMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrateV3ToV4BackfillsUnlinkedUnmarkedExamples() {
        helper.createDatabase(DB_NORMAL, 3).use { db ->
            idiom(db, "i1", "一字千金")
            idiom(db, "i2", "画蛇添足")
            idiom(db, "i3", "守株待兔")
            example(db, "ex1", EXACT_STEM)
            db.execSQL("INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i1')")
            // u1 题干带首尾空白，规则一去空白后与 example.stem 全等 → i1
            userExample(db, "u1", "  $EXACT_STEM  ")
            // u2 / u3 分别由题干、myAnswer 完整包含成语词命中规则二
            userExample(db, "u2", "这道题说画蛇添足不对")
            userExample(db, "u3", "无关题干", "答案应为守株待兔")
            // u4 已有归属，即使文本命中也不动
            userExample(db, "u4", "画蛇添足")
            db.execSQL("INSERT INTO user_example_idiom (userExampleId, idiomId) VALUES ('u4', 'i2')")
            // u5 已标错、u6 两条规则均不中：都跳过
            userExample(db, "u5", "画蛇添足", isMistake = true)
            userExample(db, "u6", "完全没有匹配", "无")
        }

        helper.runMigrationsAndValidate(DB_NORMAL, 4, true, DbMigrations.MIGRATION_3_4).use { db ->
            assertEquals(listOf("u1:i1", "u2:i2", "u3:i3", "u4:i2"), links(db))
            assertEquals(0, danglingCount(db))
        }
    }

    @Test
    fun interruptedMigrationRollsBack() {
        helper.createDatabase(DB_ROLLBACK, 3).use { db ->
            idiom(db, "i1", "一字千金")
            example(db, "ex1", EXACT_STEM)
            db.execSQL("INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i1')")
            userExample(db, "u1", "  $EXACT_STEM  ")
            assertThrows(IllegalStateException::class.java) {
                db.beginTransaction()
                try {
                    DbMigrations.MIGRATION_3_4.migrate(db)
                    error("simulated interruption")
                } finally {
                    db.endTransaction()
                }
            }
            assertEquals(3, db.version)
            assertTrue(links(db).isEmpty())
        }
    }

    @Test
    fun multipleMatchesWriteAllAssociations() {
        helper.createDatabase(DB_MULTI, 3).use { db ->
            idiom(db, "i1", "一字千金")
            idiom(db, "i2", "画蛇添足")
            idiom(db, "i3", "守株待兔")
            example(db, "ex1", MULTI_STEM)
            db.execSQL("INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i1')")
            db.execSQL("INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i2')")
            // 规则一命中 i1/i2，规则二命中 i2/i3；i2 两规则重复命中只写一行
            userExample(db, "u1", "  $MULTI_STEM  ")
        }

        helper.runMigrationsAndValidate(DB_MULTI, 4, true, DbMigrations.MIGRATION_3_4).use { db ->
            assertEquals(listOf("u1:i1", "u1:i2", "u1:i3"), links(db))
        }
    }

    @Test
    fun noMatchLeavesNoAssociation() {
        helper.createDatabase(DB_NO_MATCH, 3).use { db ->
            idiom(db, "i1", "一字千金")
            userExample(db, "u1", "完全没有匹配", "无")
        }

        helper.runMigrationsAndValidate(DB_NO_MATCH, 4, true, DbMigrations.MIGRATION_3_4).use { db ->
            assertTrue(links(db).isEmpty())
        }
    }

    @Test
    fun danglingExampleIdiomDoesNotWriteMissingIdiom() {
        helper.createDatabase(DB_DANGLING, 3).use { db ->
            idiom(db, "i1", "一字千金")
            example(db, "ex1", EXACT_STEM)
            db.execSQL("INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i1')")
            db.execSQL("INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'ghost')")
            userExample(db, "u1", EXACT_STEM)
        }

        helper.runMigrationsAndValidate(DB_DANGLING, 4, true, DbMigrations.MIGRATION_3_4).use { db ->
            assertEquals(listOf("u1:i1"), links(db))
            assertEquals(0, danglingCount(db))
        }
    }

    @Test
    fun secondRunDoesNotAddRows() {
        helper.createDatabase(DB_IDEMPOTENT, 3).use { db ->
            idiom(db, "i1", "一字千金")
            example(db, "ex1", EXACT_STEM)
            db.execSQL("INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i1')")
            userExample(db, "u1", "  $EXACT_STEM  ")
        }

        helper.runMigrationsAndValidate(DB_IDEMPOTENT, 4, true, DbMigrations.MIGRATION_3_4).use { db ->
            val before = links(db)
            assertEquals(listOf("u1:i1"), before)
            DbMigrations.MIGRATION_3_4.migrate(db)
            assertEquals(before, links(db))
        }
    }

    private fun idiom(
        db: SupportSQLiteDatabase,
        id: String,
        word: String,
    ) {
        db.execSQL(
            "INSERT INTO idiom (id, word, pinyin, initials, sense, color, pitfalls, synonyms, antonyms, frequency) " +
                "VALUES (?, ?, 'py', 'py', '义', '中', '[]', '[]', '[]', 1)",
            arrayOf(id, word),
        )
    }

    private fun example(
        db: SupportSQLiteDatabase,
        id: String,
        stem: String,
    ) {
        db.execSQL(
            "INSERT INTO example (id, stem, optionA, optionB, optionC, optionD, answer, explain, " +
                "examType, year, region, questionType) " +
                "VALUES (?, ?, 'A', 'B', 'C', 'D', 'A', '释', '国考', 2026, '京', '单选')",
            arrayOf(id, stem),
        )
    }

    private fun userExample(
        db: SupportSQLiteDatabase,
        id: String,
        stem: String,
        myAnswer: String? = null,
        isMistake: Boolean = false,
    ) {
        db.execSQL(
            "INSERT INTO user_example (id, stem, myAnswer, isMistake, createdAt) VALUES (?, ?, ?, ?, ?)",
            arrayOf(id, stem, myAnswer, if (isMistake) 1 else 0, "2026-11-01T00:00:00Z"),
        )
    }

    private fun links(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT userExampleId || ':' || idiomId FROM user_example_idiom ORDER BY 1").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun danglingCount(db: SupportSQLiteDatabase): Int =
        count(
            db,
            "SELECT COUNT(*) FROM user_example_idiom AS link WHERE " +
                "link.userExampleId NOT IN (SELECT id FROM user_example) OR " +
                "link.idiomId NOT IN (SELECT id FROM idiom)",
        )

    private fun count(
        db: SupportSQLiteDatabase,
        sql: String,
    ): Int =
        db.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val DB_NORMAL = "user-example-backfill.db"
        const val DB_ROLLBACK = "user-example-backfill-rollback.db"
        const val DB_MULTI = "user-example-backfill-multi.db"
        const val DB_NO_MATCH = "user-example-backfill-no-match.db"
        const val DB_DANGLING = "user-example-backfill-dangling.db"
        const val DB_IDEMPOTENT = "user-example-backfill-idempotent.db"
        const val EXACT_STEM = "请补全：一行白鹭上青天"
        const val MULTI_STEM = "画蛇添足与守株待兔"
    }
}
