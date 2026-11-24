package com.chenyubang.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.ByteArrayInputStream

/** JsonNull 回归：可空字段解码为 null、NULL 同键合并复用 id、NULL 同键覆盖复用 id 且不写字面串。 */
@Suppress("MagicNumber", "TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackupDecodeNullTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var importer: BackupImporter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = BackupDb.open(context)
        BackupDb.seedIdioms(database)
        importer = BackupImporter(database, context)
        context.cacheDir.listFiles { file -> file.name.startsWith(BackupSpec.SAFETY_PREFIX) }
            ?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun jsonNullOptionalFieldsDecodeToNullWhileMissingAndRealStringsStayCorrect() {
        val data = nullFieldData()
        val snapshot = BackupDecode.fromJson(data)
        val mistake = snapshot.mistakes[0]
        assertNull(mistake.userExampleId)
        assertNull(mistake.imagePath)
        assertNull(mistake.ocrText)
        assertNull(mistake.examType)
        assertNull(mistake.region)
        assertNull(mistake.lastReviewedAt)
        assertNull(mistake.myAnswer)
        assertEquals("搭配不当", mistake.reason)
        assertEquals(BackupFixture.STEM, mistake.stem)
        val example = snapshot.userExamples[0]
        assertNull(example.imagePath)
        assertNull(example.ocrText)
        assertNull(example.optionA)
        assertEquals("乙选项", example.optionB)
        assertNull(snapshot.scopes[0].province)
        assertEquals("湖北", snapshot.scopes[1].province)

        val validated =
            importer.validate(
                ByteArrayInputStream(BackupFixture.zip(data, BackupFixture.manifest(BackupFixture.incoming(), data))),
            )
        assertNull(validated.snapshot.mistakes[0].imagePath)
        assertNull(validated.snapshot.userExamples[0].optionA)
        assertNull(validated.snapshot.scopes[0].province)
    }

    @Test
    fun mergeNullProvinceReusesSameKeyIdsAndWritesNoLiteralNull() =
        runBlocking {
            seedLocalNullScopes()
            val backup = nullScopeBackup()
            val result = importer.restore(backup, ImportMode.MERGE)

            assertEquals(BackupCounts(), result.imported)
            assertEquals(4, BackupDb.count(database, "exam_scope"))
            assertEquals("2", sqlCount("SELECT COUNT(*) FROM exam_scope WHERE province IS NULL"))
            assertEquals("0", sqlCount("SELECT COUNT(*) FROM exam_scope WHERE province = 'null'"))
            assertEquals(
                "local-null-national",
                BackupDb.nullableText(database, "SELECT id FROM exam_scope WHERE type = '国考' AND province IS NULL"),
            )
            assertEquals(
                "local-null-provincial",
                BackupDb.nullableText(database, "SELECT id FROM exam_scope WHERE type = '省考' AND province IS NULL"),
            )
        }

    @Test
    fun overwriteNullProvinceReusesSameKeyIdsAndSkipsNullInSettings() =
        runBlocking {
            seedLocalNullScopes()
            val backup = nullScopeBackup()
            val result = importer.restore(backup, ImportMode.OVERWRITE)

            assertEquals(2, result.imported.scopes)
            assertEquals(2, BackupDb.count(database, "exam_scope"))
            assertEquals("2", sqlCount("SELECT COUNT(*) FROM exam_scope WHERE province IS NULL"))
            assertEquals("0", sqlCount("SELECT COUNT(*) FROM exam_scope WHERE province = 'null'"))
            assertEquals(
                setOf("local-null-national", "local-null-provincial"),
                BackupDb.rows(database, "SELECT id FROM exam_scope").toSet(),
            )
            assertEquals(
                "0",
                sqlCount("SELECT COUNT(*) FROM exam_scope WHERE id IN ('old-scope-id', 'old-province-id')"),
            )
            assertEquals(
                "[]",
                BackupDb.nullableText(database, "SELECT value FROM settings WHERE key = 'examScope.provinces'"),
            )
        }

    private fun nullFieldData(): JsonObject {
        val base = BackupFixture.data(BackupFixture.incoming())
        val mistakes = base.getValue("mistakes") as JsonArray
        val firstMistake = mistakes[0] as JsonObject
        val examples = base.getValue("userExamples") as JsonArray
        val firstExample = examples[0] as JsonObject
        val scopes = base.getValue("scopes") as JsonArray
        val firstScope = scopes[0] as JsonObject
        val nullMistakeFields =
            mapOf<String, JsonElement>(
                "userExampleId" to JsonNull,
                "imagePath" to JsonNull,
                "ocrText" to JsonNull,
                "examType" to JsonNull,
                "region" to JsonNull,
                "lastReviewedAt" to JsonNull,
            )
        val nullExampleFields =
            mapOf<String, JsonElement>(
                "imagePath" to JsonNull,
                "ocrText" to JsonNull,
                "optionA" to JsonNull,
            )
        val nulledMistake = JsonObject(firstMistake + nullMistakeFields - "myAnswer")
        val nulledExample = JsonObject(firstExample + nullExampleFields)
        val nulledScope = JsonObject(firstScope + ("province" to JsonNull))
        return JsonObject(
            base +
                mapOf<String, JsonElement>(
                    "mistakes" to JsonArray(listOf<JsonElement>(nulledMistake) + mistakes.drop(1)),
                    "userExamples" to JsonArray(listOf<JsonElement>(nulledExample) + examples.drop(1)),
                    "scopes" to JsonArray(listOf<JsonElement>(nulledScope) + scopes.drop(1)),
                ),
        )
    }

    private fun seedLocalNullScopes() {
        BackupDb.seedLocalRows(database)
        BackupDb.exec(
            database,
            "INSERT INTO exam_scope (id, type, province, enabled) VALUES (?,?,?,?)",
            arrayOf("local-null-national", "国考", null, 1),
        )
        BackupDb.exec(
            database,
            "INSERT INTO exam_scope (id, type, province, enabled) VALUES (?,?,?,?)",
            arrayOf("local-null-provincial", "省考", null, 1),
        )
    }

    private fun nullScopeBackup(): ValidatedBackup {
        val snapshot =
            BackupFixture.incoming().copy(
                scopes = listOf(BackupFixture.scope("国考", null), BackupFixture.scope("省考", null)),
            )
        val validated = importer.validate(ByteArrayInputStream(BackupFixture.zip(snapshot)))
        assertNull(validated.snapshot.scopes[0].province)
        assertNull(validated.snapshot.scopes[1].province)
        return validated
    }

    private fun sqlCount(sql: String): String = requireNotNull(BackupDb.nullableText(database, sql))
}
