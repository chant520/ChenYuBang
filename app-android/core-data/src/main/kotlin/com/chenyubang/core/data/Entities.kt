package com.chenyubang.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 10 张表，字段名与开发规范 §4.1 一一对应。主键一律 uuid 字符串（备份合并靠它去重）。
 * 时间一律 UTC ISO-8601 字符串，展示时转本地时区。
 */

@Entity(tableName = "idiom", indices = [Index("word"), Index("initials")])
data class IdiomEntity(
    @PrimaryKey val id: String,
    val word: String,
    val pinyin: String,
    val initials: String,
    val sense: String,
    val origin: String? = null,
    val color: String,
    val pitfalls: String = "[]",
    val synonyms: String = "[]",
    val antonyms: String = "[]",
    val frequency: Int,
)

@Entity(tableName = "exam_scope")
data class ExamScopeEntity(
    @PrimaryKey val id: String,
    val type: String,
    val province: String? = null,
    val enabled: Boolean,
)

@Entity(tableName = "idiom_scope", primaryKeys = ["idiomId", "scopeId"])
data class IdiomScopeEntity(
    val idiomId: String,
    val scopeId: String,
)

@Entity(tableName = "example")
data class ExampleEntity(
    @PrimaryKey val id: String,
    val stem: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val answer: String,
    val explain: String,
    val examType: String,
    val year: Int,
    val region: String,
    val questionType: String,
    val imagePath: String? = null,
)

@Entity(tableName = "example_idiom", primaryKeys = ["exampleId", "idiomId"])
data class ExampleIdiomEntity(
    val exampleId: String,
    val idiomId: String,
)

@Entity(tableName = "mistake", indices = [Index("state"), Index("createdAt")])
data class MistakeEntity(
    @PrimaryKey val id: String,
    val imagePath: String? = null,
    val ocrText: String? = null,
    val stem: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val myAnswer: String? = null,
    val reason: String? = null,
    @ColumnInfo(name = "tagNames") val tagNames: List<String> = emptyList(),
    val examType: String? = null,
    val year: Int? = null,
    val region: String? = null,
    val state: String,
    val createdAt: String,
    val lastReviewedAt: String? = null,
    val reviewCount: Int = 0,
)

@Entity(tableName = "mistake_idiom", primaryKeys = ["mistakeId", "idiomId"])
data class MistakeIdiomEntity(
    val mistakeId: String,
    val idiomId: String,
)

@Entity(tableName = "review_log", indices = [Index("mistakeId")])
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val mistakeId: String,
    val result: String,
    val at: String,
)

@Entity(tableName = "idiom_state")
data class IdiomStateEntity(
    @PrimaryKey val idiomId: String,
    val status: String,
    val favorite: Boolean = false,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
