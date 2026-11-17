package com.chenyubang.core.data

import androidx.room.migration.Migration

/**
 * 迁移登记表，与 shared/schema/migrations.json 对应（规范 §4.5.3）。
 *
 * v1 是基线版本，没有迁移。新增版本时：
 *   1. 在下面追加 Migration(旧, 新) 并只做「加表 / 加可空列」；
 *   2. 同步升 AppDatabase.version；
 *   3. 在 androidTest 里补一条正常路径 + 一条中断回滚用例；
 *   4. 更新 shared/schema/migrations.json。
 *
 * 禁止使用 fallbackToDestructiveMigration / 自动迁移推断。
 */
object DbMigrations {
    val ALL: Array<Migration> = emptyArray()
}
