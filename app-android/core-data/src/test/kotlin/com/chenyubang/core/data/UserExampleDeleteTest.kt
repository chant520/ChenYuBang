package com.chenyubang.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** P10 删除双分支自测（清单 6–7 + B7 来源页刷新）：归位不删题、整题删原图、两页列表重读。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserExampleDeleteTest {
    private lateinit var db: AppDatabase
    private lateinit var imagesDir: File
    private lateinit var source: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            db.idiomDao().upsertAll(
                listOf(
                    IdiomEntity("idiom-1", "按部就班", "an", "ab", "释义", color = "emerald", frequency = 1),
                ),
            )
        }
        imagesDir = File(context.filesDir, "delete-images-${UUID.randomUUID()}")
        source = File(context.cacheDir, "delete-capture-${UUID.randomUUID()}.jpg")
        source.writeBytes(byteArrayOf(1, 2, 3, 4))
    }

    @After
    fun tearDown() {
        db.close()
        source.delete()
        imagesDir.deleteRecursively()
    }

    @Test
    fun deleteMarkedClearsFlagAndMistakeButKeepsExampleAndImage() =
        runBlocking {
            val saved = saver().save(input(isMistake = true))
            val image = File(imagesDir, "${saved.userExampleId}.jpg")
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO review_log (id, mistakeId, result, at) " +
                    "VALUES ('log-1', '${saved.mistakeId}', '对', '2026-11-02T00:00:00Z')",
            )
            assertNotNull(db.mistakeQueryDao().mistakesByTime().first().firstOrNull())

            editor().deleteMarked(saved.userExampleId)

            val example = db.userExampleDao().byId(saved.userExampleId)!!
            assertFalse(example.isMistake)
            assertEquals("题干", example.stem)
            assertTrue(image.exists())
            assertEquals(0, count("SELECT COUNT(*) FROM mistake"))
            assertEquals(0, count("SELECT COUNT(*) FROM mistake_idiom"))
            assertEquals(0, count("SELECT COUNT(*) FROM review_log"))
            assertTrue(db.mistakeQueryDao().mistakesByTime().first().isEmpty())
            assertEquals(1, db.mistakeQueryDao().userExamplesByIdiom(IDIOM).first().size)
        }

    @Test
    fun deleteUnmarkedRemovesExampleLinksAndLocalImage() =
        runBlocking {
            val saved = saver().save(input(isMistake = false))
            val image = File(imagesDir, "${saved.userExampleId}.jpg")
            assertTrue(image.exists())
            assertEquals(1, db.mistakeQueryDao().userExamplesByIdiom(IDIOM).first().size)

            editor().deleteUnmarked(saved.userExampleId)

            assertNull(db.userExampleDao().byId(saved.userExampleId))
            assertEquals(0, count("SELECT COUNT(*) FROM user_example_idiom"))
            assertEquals(0, count("SELECT COUNT(*) FROM mistake"))
            assertFalse(image.exists())
            assertTrue(db.mistakeQueryDao().userExamplesByIdiom(IDIOM).first().isEmpty())
        }

    private fun saver() = UserExampleSaver(db.userExampleDao(), imagesDir)

    private fun editor() = UserExampleEditor(db, imagesDir)

    private fun input(isMistake: Boolean) =
        CaptureSaveInput(
            imagePath = source.absolutePath,
            ocrText = "",
            stem = "题干",
            myAnswer = "B",
            isMistake = isMistake,
            reason = "错因",
            tagNames = listOf("标签"),
            examType = "国考",
            year = 2025,
            idioms = listOf(SavedIdiom(IDIOM, "按部就班")),
            createdAt = "2026-11-20T12:00:00Z",
        )

    private fun count(sql: String): Int =
        db.openHelper.writableDatabase.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val IDIOM = "idiom-1"
    }
}
