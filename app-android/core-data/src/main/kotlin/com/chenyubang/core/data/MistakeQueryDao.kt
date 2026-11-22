package com.chenyubang.core.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** P08 总览两格（与 P02「我的错题」卡同源，唯一一份查询）。 */
data class MistakeOverview(
    val total: Int,
    val pending: Int,
)

/** P08「按成语分组」一行：该成语下标记为错题的题数。 */
data class MistakeIdiomGroup(
    val idiomId: String,
    val word: String,
    val mistakeCount: Int,
)

/** P08「按录入时间 / 按考试类型」一行：错题本体 + 它关联的成语（经 mistake_idiom）。 */
data class MistakeWithIdioms(
    @Embedded val mistake: MistakeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy =
            Junction(
                value = MistakeIdiomEntity::class,
                parentColumn = "mistakeId",
                entityColumn = "idiomId",
            ),
    )
    val idioms: List<IdiomEntity>,
)

/**
 * P09 一行：一条录入题 + 它的错题标记（未标错时为空列表）。
 * 归属过滤走 user_example_idiom（信息架构 v1.4：P09 展示该成语下全部录入题，不看过没过标错）。
 */
data class UserExampleWithMistake(
    @Embedded val userExample: UserExampleEntity,
    @Relation(parentColumn = "id", entityColumn = "userExampleId")
    val mistakes: List<MistakeEntity>,
)

/**
 * P08 / P09 只读查询（工作包 2.6，开发规范 v1.7 §4.1）。
 *
 * 口径：
 *  - 错题一律从 mistake + mistake_idiom 出发，未标错的录入题只出现在 P09；
 *  - 录入题归属一律走 user_example_idiom，P09 用 @Relation 取错题标记，避免手写 JOIN
 *    让 tagNames 的 JSON 转换器失效；
 *  - 全部是只读查询，不建表、不改迁移、不碰 AppDatabase 版本。
 */
@Dao
interface MistakeQueryDao {
    /** 总览：错题总数与待复习数。state 取值是库内字符串，不是样式。 */
    @Query(
        "SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN state = '待复习' THEN 1 ELSE 0 END), 0) AS pending FROM mistake",
    )
    fun overview(): Flow<MistakeOverview>

    /** 按成语分组：只统计挂到 mistake_idiom 上的错题，计数倒序、同数按拼音首字母（iOS MistakeLibrary.swift:143 同 tie-break）。 */
    @Query(
        "SELECT idiom.id AS idiomId, idiom.word AS word, COUNT(*) AS mistakeCount " +
            "FROM mistake " +
            "JOIN mistake_idiom ON mistake_idiom.mistakeId = mistake.id " +
            "JOIN idiom ON idiom.id = mistake_idiom.idiomId " +
            "GROUP BY idiom.id, idiom.word " +
            "ORDER BY mistakeCount DESC, idiom.initials, idiom.word",
    )
    fun groupsByIdiom(): Flow<List<MistakeIdiomGroup>>

    /** 按录入时间倒序的全部错题（天然只含标错记录，不从 user_example 出发）。 */
    @Transaction
    @Query("SELECT * FROM mistake ORDER BY createdAt DESC, id")
    fun mistakesByTime(): Flow<List<MistakeWithIdioms>>

    /**
     * P09：某成语下的全部录入题（含未标错），时间倒序。
     * INNER JOIN 只作用在 user_example 与 user_example_idiom 之间；是否标错交给 @Relation 左连，
     * 保证「未标错的录入题也要出现」这一核心语义。
     */
    @Transaction
    @Query(
        "SELECT user_example.* FROM user_example " +
            "INNER JOIN user_example_idiom ON user_example_idiom.userExampleId = user_example.id " +
            "WHERE user_example_idiom.idiomId = :idiomId " +
            "ORDER BY user_example.createdAt DESC, user_example.id",
    )
    fun userExamplesByIdiom(idiomId: String): Flow<List<UserExampleWithMistake>>
}
