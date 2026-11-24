package com.chenyubang.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.io.File
import java.util.UUID

/** P10 编辑写库自测（清单 1–5）：快照列真刷新、状态不动、关联顺序一致、非法输入不改库。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserExampleEditorTest {
    private lateinit var db: AppDatabase
    private lateinit var imagesDir: File
    private lateinit var source: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { db.idiomDao().upsertAll(listOf(idiom(I1), idiom(I2), idiom(I3))) }
        imagesDir = File(context.filesDir, "edit-images-${UUID.randomUUID()}")
        source = File(context.cacheDir, "edit-capture-${UUID.randomUUID()}.jpg")
        source.writeBytes(byteArrayOf(1, 2, 3, 4))
    }

    @After
    fun tearDown() {
        db.close()
        source.delete()
        imagesDir.deleteRecursively()
    }

    @Test
    fun editRefreshesBothUserExampleAndEveryMistakeSnapshot() =
        runBlocking {
            val saved = saver().save(markedInput())
            val exampleId = saved.userExampleId
            val extra = "extra-mistake-id"
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO mistake (id, userExampleId, stem, optionA, optionB, optionC, optionD, " +
                    "state, createdAt, tagNames, reviewCount) " +
                    "VALUES ('$extra', '$exampleId', '旧快照', '甲', '乙', '丙', '丁', '待复习', " +
                    "'2026-11-01T00:00:00Z', '[]', 0)",
            )
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO review_log (id, mistakeId, result, at) " +
                    "VALUES ('log-1', '${saved.mistakeId}', '对', '2026-11-02T00:00:00Z')",
            )
            val before =
                row(mistakeStateSql(saved.mistakeId!!))
            editor().edit(editInput(saved.userExampleId))

            val example = db.userExampleDao().byId(saved.userExampleId)!!
            assertEquals("新题干", example.stem)
            assertEquals("甲", example.optionA)
            assertNull(example.optionB)
            assertEquals("  原图识别  ", example.ocrText)
            assertEquals("images/${saved.userExampleId}.jpg", example.imagePath)
            assertTrue(example.isMistake)
            assertEquals("2026-11-20T12:00:00Z", example.createdAt)

            listOf(saved.mistakeId!!, extra).forEach { id ->
                val mark = db.userExampleDao().mistakesOf(saved.userExampleId).first { it.id == id }
                assertEquals("新题干", mark.stem)
                assertEquals("甲", mark.optionA)
                assertEquals("", mark.optionB)
                assertEquals("D", mark.myAnswer)
                assertEquals("新错因", mark.reason)
                assertEquals(listOf("新标签"), mark.tagNames)
                assertEquals("2026", mark.year.toString())
            }
            assertEquals("待复习", row("SELECT state FROM mistake WHERE id = '${saved.mistakeId}'"))
            assertEquals(
                before,
                row(mistakeStateSql(saved.mistakeId!!)),
            )
            assertEquals(1, count("SELECT COUNT(*) FROM review_log"))
            assertEquals("新题干", db.mistakeQueryDao().mistakesByTime().first().first().mistake.stem)
        }

    @Test
    fun editRewritesTwoIdiomTablesInSameOrderAndDeduplicates() =
        runBlocking {
            val saved = saver().save(markedInput())
            val exampleId = saved.userExampleId
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO mistake (id, userExampleId, stem, optionA, optionB, optionC, optionD, " +
                    "state, createdAt, tagNames, reviewCount) " +
                    "VALUES ('extra-mistake-id', '$exampleId', '旧快照', '甲', '乙', '丙', '丁', " +
                    "'待复习', '2026-11-01T00:00:00Z', '[]', 0)",
            )
            editor().edit(editInput(saved.userExampleId, idiomIds = listOf(I3, I1, I1, I3)))

            val exampleOrder =
                order(
                    idiomOrderSql(exampleId),
                )
            assertEquals(listOf(I3, I1), exampleOrder)
            listOf(saved.mistakeId!!, "extra-mistake-id").forEach { id ->
                assertEquals(
                    exampleOrder,
                    order("SELECT idiomId FROM mistake_idiom WHERE mistakeId = '$id' ORDER BY rowid"),
                )
            }
            assertEquals(2, count("SELECT COUNT(*) FROM user_example_idiom"))
        }

    @Test
    fun editOfUnmarkedExampleCreatesNoMistakeRows() =
        runBlocking {
            val saved = saver().save(unmarkedInput())
            val exampleId = saved.userExampleId
            editor().edit(editInput(saved.userExampleId, idiomIds = listOf(I2)))
            assertNull(saved.mistakeId)
            assertEquals(0, count("SELECT COUNT(*) FROM mistake"))
            assertEquals(0, count("SELECT COUNT(*) FROM mistake_idiom"))
            assertEquals(0, count("SELECT COUNT(*) FROM review_log"))
            assertEquals(
                listOf(I2),
                order(
                    idiomOrderSql(exampleId),
                ),
            )
        }

    @Test
    fun invalidInputsLeaveEveryStoredFieldUntouched() =
        runBlocking {
            val saved = saver().save(markedInput())
            val exampleId = saved.userExampleId
            val before =
                row(
                    exampleRowSql(exampleId),
                )
            val badStem = editInput(saved.userExampleId).copy(stem = "   ")
            assertThrows(ExampleEditError.EmptyStem::class.java) { runBlocking { editor().edit(badStem) } }
            val noIdiom = editInput(saved.userExampleId).copy(idiomIds = emptyList())
            assertThrows(ExampleEditError.NoIdioms::class.java) { runBlocking { editor().edit(noIdiom) } }
            val badAnswer = editInput(saved.userExampleId).copy(myAnswer = "E")
            assertThrows(ExampleEditError.InvalidAnswer::class.java) { runBlocking { editor().edit(badAnswer) } }
            val badYear = editInput(saved.userExampleId).copy(year = 20)
            assertThrows(ExampleEditError.InvalidYear::class.java) { runBlocking { editor().edit(badYear) } }
            val badTag = editInput(saved.userExampleId).copy(tagNames = listOf("九个字的超长标签内容啊"))
            assertThrows(ExampleEditError.InvalidTag::class.java) { runBlocking { editor().edit(badTag) } }
            assertEquals(
                before,
                row(
                    exampleRowSql(exampleId),
                ),
            )
            assertEquals(1, count("SELECT COUNT(*) FROM mistake"))
            assertEquals(2, count("SELECT COUNT(*) FROM user_example_idiom"))
        }

    @Test
    fun editWithZeroIdiomsStillRejectedWithoutAnyWrite() =
        runBlocking {
            val saved = saver().save(markedInput())
            val exampleId = saved.userExampleId
            val bodyBefore = row(exampleRowSql(exampleId))
            val idiomRowsBefore = count("SELECT COUNT(*) FROM user_example_idiom")
            val filesBefore = imagesDir.listFiles()?.size ?: 0

            assertThrows(ExampleEditError.NoIdioms::class.java) {
                runBlocking { editor().edit(editInput(exampleId, idiomIds = emptyList())) }
            }

            assertEquals(bodyBefore, row(exampleRowSql(exampleId)))
            assertEquals(idiomRowsBefore, count("SELECT COUNT(*) FROM user_example_idiom"))
            assertEquals(filesBefore, imagesDir.listFiles()?.size ?: 0)
        }

    @Test
    fun missingExampleFailsWithoutPartialWrite() =
        runBlocking {
            val saved = saver().save(markedInput())
            val exampleId = saved.userExampleId
            val before = count("SELECT COUNT(*) FROM user_example_idiom")
            assertThrows(ExampleEditError.NotFound::class.java) {
                runBlocking { editor().edit(editInput("not-in-db")) }
            }
            assertEquals(before, count("SELECT COUNT(*) FROM user_example_idiom"))
            assertEquals("题干", db.userExampleDao().byId(saved.userExampleId)!!.stem)
        }

    @Test
    fun databaseFailureRollsBackWholeEdit() =
        runBlocking {
            val saved = saver().save(markedInput())
            val exampleId = saved.userExampleId
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_edit BEFORE UPDATE ON user_example BEGIN SELECT RAISE(ABORT, 'boom'); END",
            )
            assertThrows(ExampleEditError.Database::class.java) {
                runBlocking { editor().edit(editInput(saved.userExampleId)) }
            }
            assertEquals("题干", db.userExampleDao().byId(saved.userExampleId)!!.stem)
            assertEquals(
                listOf(I1, I2),
                order(
                    idiomOrderSql(exampleId),
                ),
            )
            assertEquals("题干", db.userExampleDao().mistakesOf(saved.userExampleId).first().stem)
        }

    private fun saver() = UserExampleSaver(db.userExampleDao(), imagesDir)

    private fun editor() = UserExampleEditor(db, imagesDir)

    private fun idiom(id: String) =
        IdiomEntity(
            id = id,
            word = "词$id",
            pinyin = "ci",
            initials = "c",
            sense = "释义",
            color = "emerald",
            frequency = 1,
        )

    private fun markedInput() =
        CaptureSaveInput(
            imagePath = source.absolutePath,
            ocrText = "  原图识别  ",
            stem = "  题干  ",
            optionA = "甲",
            optionB = "乙",
            optionC = "丙",
            optionD = "丁",
            myAnswer = "B",
            isMistake = true,
            reason = "旧错因",
            tagNames = listOf("旧标签"),
            examType = "国考",
            year = 2025,
            idioms = listOf(SavedIdiom(I1, "词1"), SavedIdiom(I2, "词2")),
            createdAt = "2026-11-20T12:00:00Z",
        )

    private fun unmarkedInput() = markedInput().copy(isMistake = false, reason = "", tagNames = emptyList())

    private fun editInput(
        id: String,
        idiomIds: List<String> = listOf(I1),
    ) = ExampleEditInput(
        id = id,
        stem = "  新题干  ",
        optionA = " 甲 ",
        optionB = "   ",
        optionC = "丙",
        optionD = "丁",
        myAnswer = "d",
        examType = " 省考 ",
        year = 2026,
        reason = " 新错因 ",
        tagNames = listOf("新标签"),
        idiomIds = idiomIds,
    )

    private fun idiomOrderSql(id: String) =
        "SELECT idiomId FROM user_example_idiom WHERE userExampleId = '$id' ORDER BY rowid"

    private fun mistakeStateSql(id: String) =
        "SELECT state, createdAt, lastReviewedAt, reviewCount FROM mistake WHERE id = '$id'"

    private fun exampleRowSql(id: String) =
        "SELECT stem, optionA, optionB, myAnswer, examType, year FROM user_example WHERE id = '$id'"

    private fun count(sql: String): Int =
        db.openHelper.writableDatabase.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun row(sql: String): String =
        db.openHelper.writableDatabase.query(sql).use { cursor ->
            if (!cursor.moveToFirst()) return@use ""
            (0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "<null>" }
        }

    private fun order(sql: String): List<String> =
        db.openHelper.writableDatabase.query(sql).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private companion object {
        const val I1 = "11111111-1111-4111-8111-111111111111"
        const val I2 = "22222222-2222-4222-8222-222222222222"
        const val I3 = "33333333-3333-4333-8333-333333333333"
    }
}
