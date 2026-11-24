package com.chenyubang.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.ByteArrayInputStream

/** 验证计划第 2 节 #1/#2/#3/#4/#6/#8：合并保本机、覆盖回滚、sha/超版拒绝、原图占位、范围替换。 */
@Suppress("MagicNumber", "TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackupImporterTest {
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
    fun mergeKeepsLocalRowsForEverySameIdAndOnlyAddsNewScope() =
        runBlocking {
            BackupDb.seedLocalRows(database)
            val backup = importer.validate(ByteArrayInputStream(BackupFixture.zip(BackupFixture.incoming())))
            val result = importer.restore(backup, ImportMode.MERGE)
            val id = BackupFixture.MISTAKE_A
            assertEquals("images/local.jpg", localText("SELECT imagePath FROM mistake WHERE id = ?", id))
            assertEquals("本机甲", localText("SELECT optionA FROM mistake WHERE id = ?", id))
            assertEquals("已掌握", localText("SELECT state FROM mistake WHERE id = ?", id))
            assertEquals("9", localText("SELECT reviewCount FROM mistake WHERE id = ?", id))
            assertEquals(
                "images/local.jpg",
                localText("SELECT imagePath FROM user_example WHERE id = ?", BackupFixture.EXAMPLE_A),
            )
            assertEquals("对", localText("SELECT result FROM review_log WHERE id = ?", BackupFixture.REVIEW_A))
            assertEquals(
                "已掌握",
                localText("SELECT status FROM idiom_state WHERE idiomId = ?", BackupFixture.IDIOM_1),
            )
            assertEquals(1, BackupDb.count(database, "mistake"))
            assertEquals(1, BackupDb.count(database, "user_example"))
            assertEquals(1, BackupDb.count(database, "review_log"))
            assertEquals(1, BackupDb.count(database, "idiom_state"))
            assertEquals(BackupCounts(scopes = 1), result.imported)
            assertEquals(3, BackupDb.count(database, "exam_scope"))
            assertEquals("河南", localText("SELECT province FROM exam_scope WHERE id = 'old-province-id'"))
        }

    @Test
    fun overwriteFailureRollsBackAllTablesAndKeepsSafetyCopy() =
        runBlocking {
            BackupDb.seedLocalRows(database)
            val before = BackupDb.count(database, "mistake") to BackupDb.count(database, "exam_scope")
            BackupDb.exec(
                database,
                "CREATE TRIGGER fail_import BEFORE INSERT ON mistake " +
                    "BEGIN SELECT RAISE(ABORT, 'injected import failure'); END",
            )
            val backup = importer.validate(ByteArrayInputStream(BackupFixture.zip(BackupFixture.incoming())))
            val error =
                assertThrows(DataError.Hard::class.java) {
                    runBlocking { importer.restore(backup, ImportMode.OVERWRITE) }
                }
            assertTrue(error.message.orEmpty().contains("回滚"))
            assertEquals(before, BackupDb.count(database, "mistake") to BackupDb.count(database, "exam_scope"))
            assertEquals("本机甲", localText("SELECT optionA FROM mistake WHERE id = ?", BackupFixture.MISTAKE_A))
            assertEquals("河南", localText("SELECT province FROM exam_scope WHERE id = 'old-province-id'"))
            val safety =
                context.cacheDir.listFiles { file ->
                    file.name.startsWith(BackupSpec.SAFETY_PREFIX) && file.name.endsWith(BackupSpec.ZIP_SUFFIX)
                }
            assertEquals(1, safety?.size)
            assertEquals(
                BackupArchive.names,
                BackupArchive.read(ByteArrayInputStream(safety!![0].readBytes())).keys,
            )
        }

    @Test
    fun rejectsTamperedDataWithShaMismatchBeforeWriting() {
        val snapshot = BackupFixture.incoming()
        val data = BackupFixture.data(snapshot)
        val metadata = BackupFixture.manifest(snapshot, data)
        val tags = data.getValue("tags") as JsonArray
        val tampered =
            JsonObject(data + ("tags" to JsonArray(tags + JsonObject(mapOf("name" to JsonPrimitive("新标签"))))))
        val error =
            assertThrows(DataError.Hard::class.java) {
                importer.validate(ByteArrayInputStream(BackupFixture.zip(tampered, metadata)))
            }
        assertTrue(error.message.orEmpty().contains("sha256"))
        assertEquals(0, BackupDb.count(database, "mistake"))
        assertEquals(0, BackupDb.count(database, "user_example"))
    }

    @Test
    fun rejectsHigherSchemaVersionAndAllowsEqualAndLower() {
        val snapshot = BackupFixture.incoming()
        val data = BackupFixture.data(snapshot)
        val metadata = BackupFixture.manifest(snapshot, data)
        val high = JsonObject(metadata + ("schemaVersion" to JsonPrimitive(BackupSpec.SCHEMA_VERSION + 1)))
        val error =
            assertThrows(DataError.Hard::class.java) {
                importer.validate(ByteArrayInputStream(BackupFixture.zip(data, high)))
            }
        assertTrue(error.message.orEmpty().contains("高于当前支持"))
        for (version in listOf(BackupSpec.SCHEMA_VERSION, 1)) {
            val accepted = JsonObject(metadata + ("schemaVersion" to JsonPrimitive(version)))
            val validated = importer.validate(ByteArrayInputStream(BackupFixture.zip(data, accepted)))
            assertEquals(version, validated.schemaVersion)
        }
    }

    @Test
    fun overwriteWritesImagePathNullForMistakeAndUserExample() =
        runBlocking {
            val backup = importer.validate(ByteArrayInputStream(BackupFixture.zip(BackupFixture.incoming())))
            importer.restore(backup, ImportMode.OVERWRITE)
            assertNull(localText("SELECT imagePath FROM mistake WHERE id = ?", BackupFixture.MISTAKE_A))
            assertNull(localText("SELECT imagePath FROM user_example WHERE id = ?", BackupFixture.EXAMPLE_A))
        }

    @Test
    fun overwriteReplacesExamScopeRowsAndSyncsProvinces() =
        runBlocking {
            BackupDb.seedLocalRows(database)
            val backup = importer.validate(ByteArrayInputStream(BackupFixture.zip(BackupFixture.incoming())))
            importer.restore(backup, ImportMode.OVERWRITE)
            assertEquals(2, BackupDb.count(database, "exam_scope"))
            assertTrue(BackupDb.rows(database, "SELECT province FROM exam_scope").none { it == "河南" })
            assertEquals(
                "old-scope-id",
                localText("SELECT id FROM exam_scope WHERE type = '国考'"),
            )
            assertEquals(
                "[\"湖北\"]",
                localText("SELECT value FROM settings WHERE key = 'examScope.provinces'"),
            )
        }

    @Test
    fun allInvalidIdiomRefsKeepBodiesWithZeroIdiomRowsAndExactOrphans() =
        runBlocking {
            val deadIdiom1 = "88888888-8888-4888-8888-888888888888"
            val deadIdiom2 = "77777777-7777-4777-8777-777777777777"
            val missingMistake = "66666666-6666-4666-8666-666666666666"
            val snapshot =
                BackupSnapshot(
                    mistakes =
                        listOf(
                            BackupFixture.mistake(
                                id = BackupFixture.MISTAKE_A,
                                userExampleId = BackupFixture.EXAMPLE_A,
                                idiomIds = listOf(deadIdiom1, deadIdiom2),
                            ),
                        ),
                    userExamples =
                        listOf(
                            BackupFixture.userExample(
                                id = BackupFixture.EXAMPLE_A,
                                idiomIds = listOf(deadIdiom1),
                            ),
                        ),
                    reviews =
                        listOf(
                            BackupFixture.review(id = BackupFixture.REVIEW_A, mistakeId = BackupFixture.MISTAKE_A),
                            BackupFixture.review(id = BackupFixture.REVIEW_B, mistakeId = missingMistake),
                        ),
                )
            val backup = importer.validate(ByteArrayInputStream(BackupFixture.zip(snapshot)))
            val result = importer.restore(backup, ImportMode.OVERWRITE)

            assertEquals(1, BackupDb.count(database, "mistake"))
            assertEquals(1, BackupDb.count(database, "user_example"))
            assertEquals(0, BackupDb.count(database, "mistake_idiom"))
            assertEquals(0, BackupDb.count(database, "user_example_idiom"))
            assertEquals(1, BackupDb.count(database, "review_log"))
            assertEquals(0, result.orphans.mistakesWithoutUserExample.size)
            assertEquals(1, result.orphans.mistakeIdiomsMissing.size)
            assertEquals(listOf(deadIdiom1, deadIdiom2), result.orphans.mistakeIdiomsMissing[BackupFixture.MISTAKE_A])
            assertEquals(1, result.orphans.userExampleIdiomsMissing.size)
            assertEquals(listOf(deadIdiom1), result.orphans.userExampleIdiomsMissing[BackupFixture.EXAMPLE_A])
            assertEquals(listOf(BackupFixture.REVIEW_B), result.orphans.reviewsWithoutMistake)
            assertEquals(BackupCounts(mistakes = 1, userExamples = 1, reviews = 1), result.imported)
        }

    private fun localText(
        sql: String,
        arg: String? = null,
    ): String? =
        if (arg == null) {
            BackupDb.nullableText(database, sql)
        } else {
            BackupDb.nullableText(database, sql, arrayOf(arg))
        }
}
