package com.chenyubang.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * P20 保存落库 DAO（规范 §4.1 / §8.1 / 验收第 11 条）。
 *
 * [persistCapture] 是唯一保存入口，`@Transaction` 保证 user_example + user_example_idiom 同成同败；
 * 仅标错时在同一事务内追加 mistake + mistake_idiom。图片复制 / 失败删图由 UserExampleSaver 负责。
 */
@Dao
abstract class UserExampleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertUserExample(entity: UserExampleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertUserExampleIdioms(rows: List<UserExampleIdiomEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMistake(entity: MistakeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMistakeIdioms(rows: List<MistakeIdiomEntity>)

    @Query("SELECT * FROM user_example WHERE id = :id")
    abstract suspend fun byId(id: String): UserExampleEntity?

    /**
     * 同事务写入一条录入题与它的全量归属（至少一条），标错才另写错题标记与其归属快照。
     * 任一插入失败，整批回滚；调用方捕获异常后删除已复制的图片，不留半条数据。
     */
    @Transaction
    open suspend fun persistCapture(
        userExample: UserExampleEntity,
        idioms: List<String>,
        mistake: MistakeEntity? = null,
    ) {
        insertUserExample(userExample)
        insertUserExampleIdioms(idioms.map { UserExampleIdiomEntity(userExample.id, it) })
        if (mistake != null) {
            insertMistake(mistake)
            insertMistakeIdioms(idioms.map { MistakeIdiomEntity(mistake.id, it) })
        }
    }
}

/** 关联表只读查询（验收第 10 / 11 条：勾选几条写几条、每条归属都能查回）。 */
@Dao
interface UserExampleIdiomDao {
    @Query("SELECT * FROM user_example_idiom WHERE userExampleId = :userExampleId ORDER BY idiomId")
    suspend fun idiomsOf(userExampleId: String): List<UserExampleIdiomEntity>

    @Query("SELECT * FROM user_example_idiom WHERE idiomId = :idiomId")
    suspend fun examplesOf(idiomId: String): List<UserExampleIdiomEntity>

    @Query("SELECT COUNT(*) FROM user_example_idiom")
    suspend fun count(): Int
}
