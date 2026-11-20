package com.chenyubang.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** P20 保存落库单测（规范 §11.1）：两条保存分支、关联同事务、回滚与图片相对路径。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserExampleSaverTest {
    private lateinit var db: AppDatabase
    private lateinit var imagesDir: File
    private lateinit var source: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        imagesDir = File(context.filesDir, "test-images-${UUID.randomUUID()}")
        source = File(context.cacheDir, "capture-${UUID.randomUUID()}.jpg")
        source.writeBytes(byteArrayOf(1, 2, 3, 4))
    }

    @After
    fun tearDown() {
        db.close()
        source.delete()
        imagesDir.deleteRecursively()
    }

    @Test
    fun unmarkedSaveWritesOnlyUserExampleAndTwoIdioms() {
        val result = runBlocking { saver().save(input(idioms = listOf(I1, I2))) }
        assertNull(result.mistakeId)
        val saved = runBlocking { db.userExampleDao().byId(result.userExampleId) }!!
        assertEquals("images/${result.userExampleId}.jpg", saved.imagePath)
        assertFalse(saved.isMistake)
        assertEquals(2, runBlocking { db.userExampleIdiomDao().idiomsOf(result.userExampleId) }.size)
        assertEquals(2, count("SELECT COUNT(*) FROM user_example_idiom"))
        assertEquals(0, count("SELECT COUNT(*) FROM mistake"))
        assertTrue(File(imagesDir, "${result.userExampleId}.jpg").exists())
    }

    @Test
    fun markedSaveWritesMistakeAndTwoMistakeIdioms() {
        val result =
            runBlocking {
                saver().save(
                    input(
                        isMistake = true,
                        reason = "粗心",
                        tagNames = listOf("粗心", "审题"),
                        idioms = listOf(I1, I2),
                    ),
                )
            }
        assertNotNull(result.mistakeId)
        assertEquals(2, count("SELECT COUNT(*) FROM mistake_idiom"))
        val reason = scalar("SELECT reason FROM mistake WHERE id = '${result.mistakeId}'")
        assertEquals("粗心", reason)
        val tags = scalar("SELECT tagNames FROM mistake WHERE id = '${result.mistakeId}'")
        assertEquals("[\"粗心\",\"审题\"]", tags)
        assertEquals("待复习", scalar("SELECT state FROM mistake WHERE id = '${result.mistakeId}'"))
        assertEquals("0", scalar("SELECT reviewCount FROM mistake WHERE id = '${result.mistakeId}'"))
        assertEquals(2, runBlocking { db.userExampleIdiomDao().idiomsOf(result.userExampleId) }.size)
    }

    @Test
    fun emptyIdiomsAreRejectedWithoutRowsOrFile() {
        assertThrows(CaptureSaveError.NoIdioms::class.java) {
            runBlocking { saver().save(input(idioms = emptyList())) }
        }
        assertEquals(0, count("SELECT COUNT(*) FROM user_example"))
        assertEquals(0, count("SELECT COUNT(*) FROM user_example_idiom"))
        assertEquals(0, imagesDir.listFiles()?.size ?: 0)
    }

    @Test
    fun duplicateIdiomsAreDeduplicatedKeepingFirstOccurrence() {
        val result =
            runBlocking {
                saver().save(input(idioms = listOf(I1, I1.copy(word = "重复"), I2)))
            }
        val rows = runBlocking { db.userExampleIdiomDao().idiomsOf(result.userExampleId) }
        assertEquals(2, rows.size)
        assertEquals(1, count("SELECT COUNT(*) FROM user_example_idiom WHERE idiomId = '${I1.id}'"))
    }

    @Test
    fun databaseFailureRollsBackAndDeletesCopiedImage() {
        val fixed = "fixed-user-example-id"
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO user_example (id, stem, createdAt) VALUES ('$fixed', '已存在', '2026-11-20T00:00:00Z')",
        )
        assertThrows(CaptureSaveError.Database::class.java) {
            runBlocking { saver(newId = { fixed }).save(input(idioms = listOf(I1, I2))) }
        }
        assertEquals(1, count("SELECT COUNT(*) FROM user_example"))
        assertEquals(0, count("SELECT COUNT(*) FROM user_example_idiom"))
        assertEquals(0, count("SELECT COUNT(*) FROM mistake"))
        assertFalse(File(imagesDir, "$fixed.jpg").exists())
    }

    @Test
    fun blankOptionsBecomeNullAndAnswerIsConstrained() {
        val result =
            runBlocking {
                saver().save(input(optionA = "", optionB = " 乙 ", myAnswer = "B", idioms = listOf(I1)))
            }
        val saved = runBlocking { db.userExampleDao().byId(result.userExampleId) }!!
        assertNull(saved.optionA)
        assertEquals("乙", saved.optionB)
        assertEquals("B", saved.myAnswer)
        assertThrows(CaptureSaveError.InvalidAnswer::class.java) {
            runBlocking { saver().save(input(myAnswer = "E", idioms = listOf(I1))) }
        }
    }

    @Test
    fun unmarkedSaveDoesNotPersistReasonOrTags() {
        runBlocking {
            saver().save(input(isMistake = false, reason = "不落库", tagNames = listOf("不该写"), idioms = listOf(I1)))
        }
        assertEquals(0, count("SELECT COUNT(*) FROM mistake"))
    }

    private fun saver(newId: () -> String = { UUID.randomUUID().toString() }) =
        UserExampleSaver(db.userExampleDao(), imagesDir, newId = newId)

    private fun input(
        isMistake: Boolean = false,
        reason: String = "",
        tagNames: List<String> = emptyList(),
        idioms: List<SavedIdiom>,
        optionA: String? = null,
        optionB: String? = null,
        myAnswer: String? = null,
    ) = CaptureSaveInput(
        imagePath = source.absolutePath,
        ocrText = "识别文字",
        stem = "  题干  ",
        optionA = optionA,
        optionB = optionB,
        myAnswer = myAnswer,
        isMistake = isMistake,
        reason = reason,
        tagNames = tagNames,
        examType = "国考",
        year = 2025,
        idioms = idioms,
        createdAt = "2026-11-20T12:00:00Z",
    )

    private fun count(sql: String): Int =
        db.openHelper.writableDatabase.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun scalar(sql: String): String? =
        db.openHelper.writableDatabase.query(sql).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private companion object {
        val I1 = SavedIdiom("11111111-1111-4111-8111-111111111111", "按部就班")
        val I2 = SavedIdiom("22222222-2222-4222-8222-222222222222", "别出心裁")
    }
}
