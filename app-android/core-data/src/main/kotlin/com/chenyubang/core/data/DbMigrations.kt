package com.chenyubang.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 迁移登记表，与 shared/schema/migrations.json 对应（规范 §4.5）。
 *
 * 新增版本时：
 *   1. 在下面追加 Migration(旧, 新) 并只做「加表 / 加可空列」；
 *   2. 同步升 AppDatabase.version 与 shared/schema/migrations.json；
 *   3. 在 core-data 单测里补一条正常路径 + 一条中断回滚用例。
 *
 * 禁止使用 fallbackToDestructiveMigration / 自动迁移推断。
 */
object DbMigrations {
    private const val VERSION_V2 = 2
    private const val VERSION_V3 = 3
    private const val VERSION_V4 = 4

    /** v1→v2：新增 user_example（用户录入题）+ mistake.userExampleId 可空列（规范 §4.5 v2 方案）。 */
    val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_example` (" +
                        "`id` TEXT NOT NULL, `imagePath` TEXT, `ocrText` TEXT, `stem` TEXT NOT NULL, " +
                        "`optionA` TEXT, `optionB` TEXT, `optionC` TEXT, `optionD` TEXT, `myAnswer` TEXT, " +
                        "`isMistake` INTEGER NOT NULL DEFAULT 0, `examType` TEXT, `year` INTEGER, " +
                        "`region` TEXT, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("ALTER TABLE `mistake` ADD COLUMN `userExampleId` TEXT")
            }
        }

    /**
     * v2→v3：新增 user_example_idiom（录入题归属成语，联合主键 + 两列索引），
     * 并对历史标错题按 mistake.userExampleId + mistake_idiom 防御性回填（已存在跳过、只补不删）。
     */
    val MIGRATION_2_3: Migration =
        object : Migration(VERSION_V2, VERSION_V3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_example_idiom` (" +
                        "`userExampleId` TEXT NOT NULL, `idiomId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`userExampleId`,`idiomId`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_user_example_idiom_userExampleId` " +
                        "ON `user_example_idiom` (`userExampleId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_user_example_idiom_idiomId` " +
                        "ON `user_example_idiom` (`idiomId`)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO user_example_idiom(userExampleId, idiomId) " +
                        "SELECT m.userExampleId, mi.idiomId FROM mistake m " +
                        "JOIN mistake_idiom mi ON mi.mistakeId = m.id " +
                        "WHERE m.userExampleId IS NOT NULL",
                )
            }
        }

    /**
     * v3→v4：纯数据回填，不新增表或列（规范 4.5 节 v4 方案）。
     * 只处理未标错且无任何归属的录入题：规则一题干去首尾空白后与 example.stem 全等，
     * 经 example_idiom 并 JOIN idiom 保证外键存在；规则二题干或 myAnswer 完整包含 idiom.word。
     * 两规则命中多条全写、均不中跳过；INSERT OR IGNORE 配合无归属前置条件保证幂等。
     */
    private val BACKFILL_SQL =
        """
        INSERT OR IGNORE INTO user_example_idiom(userExampleId, idiomId)
        SELECT ue.id, ei.idiomId
        FROM user_example AS ue
        JOIN example AS ex ON trim(ue.stem, char(9,10,13,32,12288)) = ex.stem
        JOIN example_idiom AS ei ON ei.exampleId = ex.id
        JOIN idiom AS i ON i.id = ei.idiomId
        WHERE ue.isMistake = 0
          AND NOT EXISTS (SELECT 1 FROM user_example_idiom AS link WHERE link.userExampleId = ue.id)
        UNION
        SELECT ue.id, i.id
        FROM user_example AS ue
        JOIN idiom AS i
          ON i.word <> ''
         AND (instr(ue.stem, i.word) > 0 OR instr(coalesce(ue.myAnswer, ''), i.word) > 0)
        WHERE ue.isMistake = 0
          AND NOT EXISTS (SELECT 1 FROM user_example_idiom AS link WHERE link.userExampleId = ue.id)
        """.trimIndent()

    val MIGRATION_3_4: Migration =
        object : Migration(VERSION_V3, VERSION_V4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(BACKFILL_SQL)
            }
        }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
