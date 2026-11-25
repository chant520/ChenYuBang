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

/** P24 收藏列表一行：成语展示字段 + 该成语的 A6 状态存储串（同源 idiom_state）。 */
data class FavoriteIdiom(
    val idiomId: String,
    val word: String,
    val pinyin: String,
    val initials: String,
    val frequency: Int,
    val status: String,
)

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
@Suppress("TooManyFunctions")
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

    // ---- 以下为阶段6包1新增只读聚合查询：只 SELECT，不建表 / 不加字段 / 不建索引 / 不迁移。 ----

    /** E2「已学成语数」：显式落行且状态为学过或已掌握；缺行=未学不计。 */
    @Query("SELECT COUNT(*) FROM idiom_state WHERE status IN ('学过', '已掌握')")
    abstract suspend fun countLearned(): Int

    /** E2「已掌握数」：仅 idiom_state 的成语自评已掌握，非错题 C9 的掌握态。 */
    @Query("SELECT COUNT(*) FROM idiom_state WHERE status = '已掌握'")
    abstract suspend fun countMastered(): Int

    /** E2「收藏数」：与 P24 列表同源过滤 favorite=1；取消收藏后 -1。 */
    @Query("SELECT COUNT(*) FROM idiom_state WHERE favorite = 1")
    abstract suspend fun countFavorite(): Int

    /** P03 批量取该页状态，避免逐行 state() 的 N+1；返回值只含已落行，缺行由调用方投影补未学。 */
    @Query("SELECT * FROM idiom_state WHERE idiomId IN (:ids)")
    abstract suspend fun statesForIds(ids: List<String>): List<IdiomStateEntity>

    /** P24 收藏列表：只取 favorite=1 且能 JOIN 到 idiom 的行，天然排除未收藏 / 缺省行。 */
    @Query(
        "SELECT idiom.id AS idiomId, idiom.word, idiom.pinyin, idiom.initials, idiom.frequency, " +
            "idiom_state.status AS status FROM idiom_state " +
            "JOIN idiom ON idiom.id = idiom_state.idiomId " +
            "WHERE idiom_state.favorite = 1 ORDER BY idiom.initials, idiom.word",
    )
    abstract suspend fun favoriteIdioms(): List<FavoriteIdiom>
}
