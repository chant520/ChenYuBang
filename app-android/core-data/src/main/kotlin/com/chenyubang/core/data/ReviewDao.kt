package com.chenyubang.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.io.Serializable
import java.util.UUID

/** 复习队列一行；正文列在 SQL 里已按事实源优先回退快照，这里只做搬运。 */
data class ReviewQuestion(
    val mistakeId: String,
    val stem: String,
    val options: List<String?>,
    val myAnswer: String?,
    val idioms: List<ReviewIdiom> = emptyList(),
)

data class ReviewIdiom(
    val id: String,
    val word: String,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/** 正文 COALESCE(ue.*, m.*)：有 user_example 时读事实源，历史无关联时按 mistake 快照展示、照常入队。 */
data class ReviewQueueRow(
    val mistakeId: String,
    val stem: String,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
    val optionD: String?,
    val myAnswer: String?,
)

data class ReviewQuestionIdiom(
    val mistakeId: String,
    val id: String,
    val word: String,
)

/** C9 手动切换只接受这两个状态；数据库仍按开发规范 v1.9 §4.1 存字符串。 */
enum class MistakeReviewState(val storedValue: String) {
    PENDING("待复习"),
    MASTERED("已掌握"),
}

/** C9 写入结果：目标不存在时明确返回 [NOT_FOUND]，不会插入错题。 */
enum class MistakeStateUpdateResult {
    UPDATED,
    NOT_FOUND,
}

/**
 * 阶段 3 复习队列与结果记录（开发规范 v1.7 §8.1 / §8.2）。
 *
 * - 队列只取 state=待复习，按 createdAt 倒序；单成语按 mistake_idiom 关联过滤，命中任一勾选即入队；
 * - [record] 一个事务写一行 review_log 并把 reviewCount 加一、lastReviewedAt 刷新，state 不改。
 */
@Dao
abstract class ReviewDao {
    @Query(
        "SELECT m.id AS mistakeId, " +
            "COALESCE(ue.stem, m.stem) AS stem, " +
            "COALESCE(ue.optionA, m.optionA) AS optionA, " +
            "COALESCE(ue.optionB, m.optionB) AS optionB, " +
            "COALESCE(ue.optionC, m.optionC) AS optionC, " +
            "COALESCE(ue.optionD, m.optionD) AS optionD, " +
            "COALESCE(ue.myAnswer, m.myAnswer) AS myAnswer " +
            "FROM mistake m LEFT JOIN user_example ue ON ue.id = m.userExampleId " +
            "WHERE m.state = '待复习' " +
            "AND (:idiomId IS NULL OR EXISTS (" +
            "SELECT 1 FROM mistake_idiom mi WHERE mi.mistakeId = m.id AND mi.idiomId = :idiomId)) " +
            "ORDER BY m.createdAt DESC, m.id",
    )
    abstract suspend fun pendingRows(idiomId: String?): List<ReviewQueueRow>

    @Query(
        "SELECT mi.mistakeId AS mistakeId, idiom.id AS id, idiom.word AS word " +
            "FROM mistake_idiom mi JOIN idiom ON idiom.id = mi.idiomId " +
            "WHERE mi.mistakeId IN (:mistakeIds) ORDER BY idiom.word",
    )
    abstract suspend fun idiomsFor(mistakeIds: List<String>): List<ReviewQuestionIdiom>

    @Query("SELECT state FROM mistake WHERE id = :mistakeId")
    abstract suspend fun stateOf(mistakeId: String): String?

    /** C9：只按 mistake.id 更新 state；同值重复写入仍返回成功。 */
    @Query("UPDATE mistake SET state = :state WHERE id = :mistakeId")
    protected abstract suspend fun updateState(
        mistakeId: String,
        state: String,
    ): Int

    @Transaction
    open suspend fun setState(
        mistakeId: String,
        state: MistakeReviewState,
    ): MistakeStateUpdateResult =
        if (updateState(mistakeId, state.storedValue) > 0) {
            MistakeStateUpdateResult.UPDATED
        } else {
            MistakeStateUpdateResult.NOT_FOUND
        }

    /** 「再复习一轮」判空：与 queue 同一过滤口径，仅计数不组装正文。 */
    @Query(
        "SELECT COUNT(*) FROM mistake m WHERE m.state = '待复习' " +
            "AND (:idiomId IS NULL OR EXISTS (" +
            "SELECT 1 FROM mistake_idiom mi WHERE mi.mistakeId = m.id AND mi.idiomId = :idiomId))",
    )
    abstract suspend fun pendingCount(idiomId: String?): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertLog(log: ReviewLogEntity)

    @Query("UPDATE mistake SET reviewCount = reviewCount + 1, lastReviewedAt = :at WHERE id = :mistakeId")
    abstract suspend fun bumpReview(
        mistakeId: String,
        at: String,
    )

    /** 组装一整轮队列；题干与选项已按事实源优先取好，关联成语一并带出供 P12 汇总。 */
    @Transaction
    open suspend fun queue(idiomId: String? = null): List<ReviewQuestion> {
        val rows = pendingRows(idiomId)
        if (rows.isEmpty()) return emptyList()
        val idioms =
            idiomsFor(rows.map { it.mistakeId })
                .groupBy { it.mistakeId }
        return rows.map { row ->
            ReviewQuestion(
                mistakeId = row.mistakeId,
                stem = row.stem,
                options = listOf(row.optionA, row.optionB, row.optionC, row.optionD),
                myAnswer = row.myAnswer,
                idioms = idioms[row.mistakeId].orEmpty().map { ReviewIdiom(it.id, it.word) },
            )
        }
    }

    /** 每题判分后一行 review_log + 同事务回写 reviewCount / lastReviewedAt；state 绝不自动改。 */
    @Transaction
    open suspend fun record(
        mistakeId: String,
        result: String,
        at: String,
    ) {
        insertLog(
            ReviewLogEntity(
                id = UUID.randomUUID().toString().lowercase(),
                mistakeId = mistakeId,
                result = result,
                at = at,
            ),
        )
        bumpReview(mistakeId, at)
    }
}
