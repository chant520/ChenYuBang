package com.chenyubang.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * 成语全文检索虚表（规范 §4.2：Room FTS4）。属 v1 基线，随 AppDatabase(version = 1) 一起建。
 *
 * 独立 FTS 表（非 external-content），不做触发器同步；题库导入时由 IdiomDao.rebuildIdiomFts()
 * 整表重建（规范 §4.6：导入只重建内容表 FTS，绝不触碰用户表）。
 *
 * - idiomid：映射 idiom.id 的 uuid，UNINDEXED（只用于 JOIN 回 idiom，不参与检索）。
 * - pinyin：存「去空格连写」规范化值（idiom.pinyin 显示版 "an bu jiu ban" → "anbujiuban"），
 *   使全拼 / 首字母 / 单字前缀都能命中；idiom 表本身的 pinyin 保持空格版不动。
 * - rowid 隐式，不声明 @PrimaryKey。
 */
@Entity(tableName = "idiom_fts")
@Fts4(notIndexed = ["idiomid"])
data class IdiomFtsEntity(
    @ColumnInfo(name = "idiomid") val idiomid: String,
    val word: String,
    val pinyin: String,
    val initials: String,
    val sense: String,
)
