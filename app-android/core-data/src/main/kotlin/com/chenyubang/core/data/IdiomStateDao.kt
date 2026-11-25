package com.chenyubang.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** A6 学习状态的强类型值；存储串与开发规范 v1.9 §4.7 逐字一致。 */
enum class IdiomLearningStatus(val storedValue: String) {
    UNLEARNED("未学"),
    LEARNED("学过"),
    MASTERED("已掌握"),
    ;

    companion object {
        fun fromStored(value: String): IdiomLearningStatus =
            entries.firstOrNull { it.storedValue == value } ?: error("Unknown idiom status: $value")
    }
}

/** 单条读取投影；缺行时由 DAO 返回默认值，不创建 idiom_state 行。 */
data class IdiomLearningState(
    val idiomId: String,
    val status: IdiomLearningStatus,
    val favorite: Boolean,
)

/**
 * A6 学习状态 / A7 收藏（开发规范 v1.9 §4.7）。
 *
 * 两个写入方向各自在单事务内 upsert，冲突时只更新本方向字段；另一字段保持原值。
 * 读取只投影单行，缺行返回未学且未收藏，不批量写满。
 */
@Dao
abstract class IdiomStateDao {
    @Query("SELECT * FROM idiom_state WHERE idiomId = :idiomId")
    protected abstract suspend fun storedState(idiomId: String): IdiomStateEntity?

    suspend fun state(idiomId: String): IdiomLearningState {
        val stored = storedState(idiomId)
        return IdiomLearningState(
            idiomId = idiomId,
            status = stored?.let { IdiomLearningStatus.fromStored(it.status) } ?: IdiomLearningStatus.UNLEARNED,
            favorite = stored?.favorite ?: false,
        )
    }

    @Transaction
    open suspend fun setStatus(
        idiomId: String,
        status: IdiomLearningStatus,
    ) {
        if (updateStatus(idiomId, status.storedValue) == 0) {
            insertState(IdiomStateEntity(idiomId, status.storedValue, favorite = false))
        }
    }

    @Transaction
    open suspend fun setFavorite(
        idiomId: String,
        favorite: Boolean,
    ) {
        if (updateFavorite(idiomId, favorite) == 0) {
            insertState(IdiomStateEntity(idiomId, IdiomLearningStatus.UNLEARNED.storedValue, favorite))
        }
    }

    @Query("UPDATE idiom_state SET status = :status WHERE idiomId = :idiomId")
    protected abstract suspend fun updateStatus(
        idiomId: String,
        status: String,
    ): Int

    @Query("UPDATE idiom_state SET favorite = :favorite WHERE idiomId = :idiomId")
    protected abstract suspend fun updateFavorite(
        idiomId: String,
        favorite: Boolean,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertState(state: IdiomStateEntity)
}
