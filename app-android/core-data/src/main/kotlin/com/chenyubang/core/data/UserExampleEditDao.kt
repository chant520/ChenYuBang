package com.chenyubang.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * P10 编辑/删除写口子（与只读的 [UserExampleDao] 分开，避免单 DAO 函数数越过 detekt 阈值）。
 * 组合操作的事务边界放在 [UserExampleEditor] 的 withTransaction，这里只放单条 SQL。
 */
@Dao
abstract class UserExampleEditDao {
    @Query(
        "UPDATE user_example SET stem = :stem, optionA = :optionA, optionB = :optionB, " +
            "optionC = :optionC, optionD = :optionD, myAnswer = :myAnswer, examType = :examType, year = :year " +
            "WHERE id = :id",
    )
    @Suppress("LongParameterList")
    abstract suspend fun updateExampleBody(
        id: String,
        stem: String,
        optionA: String?,
        optionB: String?,
        optionC: String?,
        optionD: String?,
        myAnswer: String?,
        examType: String?,
        year: Int?,
    )

    /** 快照列刷新；state / createdAt / lastReviewedAt / reviewCount 不在 SET 列表内。 */
    @Query(
        "UPDATE mistake SET stem = :stem, optionA = :optionA, optionB = :optionB, " +
            "optionC = :optionC, optionD = :optionD, myAnswer = :myAnswer, reason = :reason, " +
            "tagNames = :tagNames, examType = :examType, year = :year WHERE id = :id",
    )
    @Suppress("LongParameterList")
    abstract suspend fun updateMistakeBody(
        id: String,
        stem: String,
        optionA: String,
        optionB: String,
        optionC: String,
        optionD: String,
        myAnswer: String?,
        reason: String?,
        tagNames: List<String>,
        examType: String?,
        year: Int?,
    )

    @Query("DELETE FROM user_example_idiom WHERE userExampleId = :id")
    abstract suspend fun deleteExampleIdioms(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertExampleIdioms(rows: List<UserExampleIdiomEntity>)

    @Query("DELETE FROM mistake_idiom WHERE mistakeId = :id")
    abstract suspend fun deleteMistakeIdioms(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMistakeIdioms(rows: List<MistakeIdiomEntity>)

    @Query("DELETE FROM review_log WHERE mistakeId = :id")
    abstract suspend fun deleteReviewLogsOf(id: String)

    @Query("DELETE FROM mistake WHERE id = :id")
    abstract suspend fun deleteMistake(id: String)

    /** 删除错题分支的 isMistake 归位；不删 user_example 本体。 */
    @Query("UPDATE user_example SET isMistake = 0 WHERE id = :id")
    abstract suspend fun clearMistakeFlag(id: String)

    @Query("DELETE FROM user_example WHERE id = :id")
    abstract suspend fun deleteExample(id: String)
}
