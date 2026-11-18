package com.chenyubang.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before

/** Shared real Room fixture; user mistake text is copied from the authentic exam, never invented content. */
abstract class PackDatabaseTest {
    protected lateinit var database: AppDatabase
    protected lateinit var importer: PackImporter
    protected lateinit var pack: ValidatedPack

    @Before
    fun setUpPackDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        importer = PackImporter(database, context)
        pack = importer.validate(PackFixtureTest.bytes().inputStream())
    }

    @After
    fun closePackDatabase() {
        database.close()
    }

    protected fun count(table: String): Int =
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use {
            check(it.moveToFirst())
            it.getInt(0)
        }

    protected fun snapshot(tables: List<String>): Map<String, List<Map<String, String?>>> =
        tables.associateWith { table ->
            database.openHelper.writableDatabase.query("SELECT * FROM $table ORDER BY 1, 2").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            cursor.columnNames.mapIndexed { index, name ->
                                name to if (cursor.isNull(index)) null else cursor.getString(index)
                            }.toMap(),
                        )
                    }
                }
            }
        }

    protected fun addScope(
        id: String,
        type: String,
        province: String? = null,
        enabled: Boolean = true,
    ) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO exam_scope (id, type, province, enabled) VALUES (?, ?, ?, ?)",
            arrayOf(id, type, province, if (enabled) 1 else 0),
        )
    }

    protected fun linkedIds(scope: String): Set<String> =
        database.openHelper.writableDatabase.query(
            "SELECT idiomId FROM idiom_scope WHERE scopeId = ?",
            arrayOf(scope),
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    protected fun seedUsers(
        mistakeIdiom: String = PackRows.idioms(pack).first().entity.id,
        stateIdiom: String = PackRows.idioms(pack)[1].entity.id,
    ) {
        val question = PackRows.examples(pack).first().entity
        runBlocking {
            database.mistakeDao().upsert(
                MistakeEntity(
                    id = "user-mistake", imagePath = "images/existing.jpg", ocrText = question.stem,
                    stem = question.stem, optionA = question.optionA, optionB = question.optionB,
                    optionC = question.optionC, optionD = question.optionD, myAnswer = "A", reason = "对象误用",
                    tagNames = listOf("对象误用", "望文生义"), examType = question.examType, year = question.year,
                    region = question.region, state = "待复习", createdAt = "2026-01-01T00:00:00Z",
                    lastReviewedAt = "2026-01-02T00:00:00Z", reviewCount = 1,
                ),
            )
        }
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO mistake_idiom (mistakeId, idiomId) VALUES (?, ?)",
            arrayOf("user-mistake", mistakeIdiom),
        )
        sqlite.execSQL(
            "INSERT INTO review_log (id, mistakeId, result, at) VALUES (?, ?, ?, ?)",
            arrayOf("user-review", "user-mistake", "仍不会", "2026-01-02T00:00:00Z"),
        )
        sqlite.execSQL(
            "INSERT INTO idiom_state (idiomId, status, favorite) VALUES (?, ?, ?)",
            arrayOf(stateIdiom, "学过", 1),
        )
        sqlite.execSQL(
            "INSERT INTO settings (key, value) VALUES (?, ?)",
            arrayOf("packVersion", "caller-owned-version"),
        )
        sqlite.execSQL("INSERT INTO settings (key, value) VALUES (?, ?)", arrayOf("firstLaunch.complete", "true"))
        addScope("user-national", "国考")
        addScope("user-henan", "省考", "河南")
    }

    companion object {
        val USER_TABLES = listOf("mistake", "mistake_idiom", "review_log", "idiom_state", "settings", "exam_scope")
        val CONTENT_TABLES = listOf("idiom", "example", "idiom_scope", "example_idiom", "idiom_fts")
    }
}
