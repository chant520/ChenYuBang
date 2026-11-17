package com.chenyubang.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

@Dao
interface IdiomDao {
    /**
     * A3 搜索的骨架实现：完整成语 / 首字母 / 全拼走前缀匹配。
     * 单字检索与 FTS 索引按规范 §4.2 在下一阶段接入（Room FTS4）。
     */
    @Query(
        "SELECT * FROM idiom WHERE word LIKE :q || '%' " +
            "OR initials LIKE :q || '%' OR pinyin LIKE :q || '%' ORDER BY word LIMIT 50",
    )
    suspend fun search(q: String): List<IdiomEntity>

    @Query("SELECT * FROM idiom WHERE id = :id")
    suspend fun byId(id: String): IdiomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(idioms: List<IdiomEntity>)
}

@Dao
interface MistakeDao {
    @Query("SELECT * FROM mistake ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(
        limit: Int,
        offset: Int,
    ): List<MistakeEntity>

    @Query("SELECT COUNT(*) FROM mistake WHERE state = :state")
    fun countByState(state: String): Flow<Int>

    @Query("SELECT * FROM mistake WHERE id = :id")
    suspend fun byId(id: String): MistakeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mistake: MistakeEntity)

    @Query("DELETE FROM mistake WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [
        IdiomEntity::class,
        ExamScopeEntity::class,
        IdiomScopeEntity::class,
        ExampleEntity::class,
        ExampleIdiomEntity::class,
        MistakeEntity::class,
        MistakeIdiomEntity::class,
        ReviewLogEntity::class,
        IdiomStateEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun idiomDao(): IdiomDao

    abstract fun mistakeDao(): MistakeDao

    companion object {
        const val NAME = "chenyubang.db"

        // Room 2.6.1 的 addMigrations 只有 vararg 重载；迁移数组仅在开库时展开一次
        @Suppress("SpreadOperator")
        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*DbMigrations.ALL)
                .build()
    }
}
