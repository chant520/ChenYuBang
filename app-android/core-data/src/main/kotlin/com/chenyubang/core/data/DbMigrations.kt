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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
