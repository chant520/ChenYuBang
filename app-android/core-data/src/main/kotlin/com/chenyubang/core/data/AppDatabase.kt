package com.chenyubang.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

/** 成语 id + 词（P07 例题文本高亮扫描用：全量词表，普通 POJO，非 Room 实体）。 */
data class IdiomWord(val id: String, val word: String)

/**
 * 成语 DAO。A3 搜索走 FTS4 虚表 idiom_fts（规范 §4.2），支持四种输入：
 * 完整成语 / 单字 / 全拼 / 首字母。单字走「前缀 LIKE + FTS 组合」，不做分词库。
 *
 * 路由：含汉字（HAN）走中文路径；纯 ASCII 字母走拼音路径（去空格连写后 MATCH pinyin/initials）。
 * 结果 JOIN 回 idiom，ORDER BY word，分页 50（§4.2）。查无结果返回空列表，文案由上层给。
 * MATCH 串里的用户输入一律用双引号包裹成 FTS phrase（内部双引号翻倍），防注入。
 */

@Dao
abstract class IdiomDao {
    companion object {
        const val SEARCH_LIMIT = 50
    }

    suspend fun search(
        q: String,
        limit: Int = SEARCH_LIMIT,
    ): List<IdiomEntity> {
        val query = q.trim()
        if (query.isEmpty() || '\u0000' in query) return emptyList()
        return if (containsHan(query)) {
            val phrase = ftsPhrase(query)
            if (query.codePointCount(0, query.length) == 1) {
                // 单字：前缀 LIKE + FTS(word 前缀 OR sense 精确) 组合，按 id 去重后合并
                val byPrefix = prefixSearch(query + "%", limit)
                val byFts = hanFtsSearch(ftsPhrase(query, prefix = true), phrase, limit)
                mergeById(byPrefix, byFts, limit)
            } else {
                // 完整成语（≥2 字）：FTS MATCH word/sense
                hanFtsSearch(phrase, phrase, limit)
            }
        } else {
            // 纯 ASCII：全拼去空格连写命中 pinyin，首字母命中 initials，前缀支持部分输入
            val phrase = ftsPhrase(query.replace(" ", "").lowercase(), prefix = true)
            romanFtsSearch(phrase, limit)
        }
    }

    @Query("SELECT * FROM idiom WHERE id = :id")
    abstract suspend fun byId(id: String): IdiomEntity?

    /**
     * 成语库一次取全量（A1；§4.2「1,286 条一次性进内存做分组」，不分页）。
     *
     * 只返回已挂到启用范围上的成语：`idiom_scope` 由启用的 `exam_scope` 行重建（§4.6），
     * 省考勾选时省份默认全选、省考内容正常显示（信息架构 v1.4，推翻决策 I）。
     * 只读查询，不改表结构、不加迁移、不改既有方法签名。
     */
    @Query(
        "SELECT idiom.* FROM idiom JOIN (SELECT DISTINCT idiomId FROM idiom_scope) AS scope " +
            "ON idiom.id = scope.idiomId ORDER BY idiom.initials, idiom.word",
    )
    abstract suspend fun libraryIdioms(): List<IdiomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(idioms: List<IdiomEntity>)

    /** 整表重建 idiom_fts（供 0.5 导入引擎调用）。只重建内容表 FTS，绝不触碰用户表（§4.6）。 */
    @Transaction
    open suspend fun rebuildIdiomFts() {
        clearIdiomFts()
        populateIdiomFts()
    }

    @Query(
        "SELECT idiom.* FROM idiom JOIN idiom_fts ON idiom.id = idiom_fts.idiomid " +
            "WHERE idiom_fts.word MATCH :wordMatch UNION " +
            "SELECT idiom.* FROM idiom JOIN idiom_fts ON idiom.id = idiom_fts.idiomid " +
            "WHERE idiom_fts.sense MATCH :senseMatch ORDER BY word LIMIT :limit",
    )
    abstract suspend fun hanFtsSearch(
        wordMatch: String,
        senseMatch: String,
        limit: Int,
    ): List<IdiomEntity>

    @Query(
        "SELECT idiom.* FROM idiom JOIN idiom_fts ON idiom.id = idiom_fts.idiomid " +
            "WHERE idiom_fts.pinyin MATCH :match UNION " +
            "SELECT idiom.* FROM idiom JOIN idiom_fts ON idiom.id = idiom_fts.idiomid " +
            "WHERE idiom_fts.initials MATCH :match ORDER BY word LIMIT :limit",
    )
    abstract suspend fun romanFtsSearch(
        match: String,
        limit: Int,
    ): List<IdiomEntity>

    @Query(
        "SELECT idiom.* FROM idiom JOIN idiom_fts ON idiom.id = idiom_fts.idiomid " +
            "WHERE idiom.word LIKE :prefix ORDER BY idiom.word LIMIT :limit",
    )
    abstract suspend fun prefixSearch(
        prefix: String,
        limit: Int,
    ): List<IdiomEntity>

    @Query("DELETE FROM idiom_fts")
    abstract suspend fun clearIdiomFts()

    @Query(
        "INSERT INTO idiom_fts(idiomid, word, pinyin, initials, sense) " +
            "SELECT id, word, replace(pinyin, ' ', ''), initials, sense FROM idiom",
    )
    abstract suspend fun populateIdiomFts()
}

private fun containsHan(q: String): Boolean =
    q.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

private fun ftsPhrase(
    token: String,
    prefix: Boolean = false,
): String = "\"" + token.replace("\"", "\"\"") + (if (prefix) "*" else "") + "\""

private fun mergeById(
    a: List<IdiomEntity>,
    b: List<IdiomEntity>,
    limit: Int,
): List<IdiomEntity> {
    val seen = LinkedHashMap<String, IdiomEntity>()
    for (entity in a + b) seen.putIfAbsent(entity.id, entity)
    return seen.values.sortedBy { it.word }.take(limit)
}

/** 成语词表只读查询（P07 例题文本高亮扫描用；独立 DAO 避免 IdiomDao 函数数越过 detekt 阈值）。 */
@Dao
interface IdiomWordDao {
    @Query("SELECT id, word FROM idiom")
    suspend fun allWords(): List<IdiomWord>
}

/**
 * P20 候选生成的全库只读查询（工作包 2.4）。与 [IdiomDao.libraryIdioms] 的考试范围过滤区分：
 * 这里全量取词，调用方只做文本命中判定；独立 DAO 避免 IdiomDao 函数数越过 detekt 阈值。
 */
@Dao
interface IdiomMatchDao {
    @Query("SELECT * FROM idiom ORDER BY word")
    suspend fun allIdioms(): List<IdiomEntity>
}

/**
 * 例题 DAO（规范 §4.1 `example` / `example_idiom`，只读查询，不加表不迁移）。
 *
 * 工作包 1.5 / 1.6 三处查询：
 *  - [byId]：P07 例题详情取单条（缺行返回 null，页面按「没有找到这道例题」空态处理）。
 *  - [listByExample]：一个例题的关联成语 id 列表（B3 一题多成语，供 P07 关联成语区）。
 *  - [listByIdiom]：某成语关联的全部系统真题（A4，供 P05 真题区），按年份倒序、id 作次级排序保证稳定。
 */
@Dao
interface ExampleDao {
    @Query("SELECT * FROM example WHERE id = :id")
    suspend fun byId(id: String): ExampleEntity?

    @Query("SELECT * FROM example_idiom WHERE exampleId = :exampleId ORDER BY idiomId")
    suspend fun listByExample(exampleId: String): List<ExampleIdiomEntity>

    @Query(
        "SELECT example.* FROM example JOIN example_idiom ON example.id = example_idiom.exampleId " +
            "WHERE example_idiom.idiomId = :idiomId ORDER BY example.year DESC, example.id",
    )
    suspend fun listByIdiom(idiomId: String): List<ExampleEntity>
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
        UserExampleEntity::class,
        UserExampleIdiomEntity::class,
        IdiomFtsEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun idiomDao(): IdiomDao

    abstract fun idiomWordDao(): IdiomWordDao

    abstract fun idiomMatchDao(): IdiomMatchDao

    abstract fun exampleDao(): ExampleDao

    abstract fun mistakeDao(): MistakeDao

    abstract fun userExampleDao(): UserExampleDao

    abstract fun userExampleEditDao(): UserExampleEditDao

    abstract fun userExampleIdiomDao(): UserExampleIdiomDao

    abstract fun mistakeQueryDao(): MistakeQueryDao

    abstract fun reviewDao(): ReviewDao

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
