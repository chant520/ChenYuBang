package com.chenyubang.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExamScopeStoreTest {
    private lateinit var database: AppDatabase
    private lateinit var store: ExamScopeStore

    @Before
    fun open() {
        database =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries().build()
        store = ExamScopeStore(database)
    }

    @After
    fun close() = database.close()

    @Test
    fun defaultsKeepThreeChecksAndNoProvince() =
        runBlocking {
            val preferences = store.load()
            assertFalse(preferences.complete)
            assertEquals(ExamScopeSelection(), preferences.scope)
            assertTrue(preferences.scope.provinceMissing)
            assertTrue(store.availableProvinces().isEmpty())
        }

    @Test
    fun allCancelledPersistsAndReopeningDoesNotRepeatFirstLaunch() =
        runBlocking {
            val scope = ExamScopeSelection(national = false, provincial = false, institution = false)
            store.save(scope, complete = true)
            assertEquals(LaunchPreferences(true, scope), ExamScopeStore(database).load())
            store.save(ExamScopeSelection())
            assertTrue(store.load().complete)
        }

    @Test
    fun disabledProvinceSelectionAndExistingUUIDsSurviveEdits() =
        runBlocking {
            val selected = ExamScopeSelection(provinces = setOf("河南", "贵州"))
            store.save(selected)
            val original = scopeIDs()
            val disabled = selected.copy(provincial = false)
            store.save(disabled)
            assertEquals(original, scopeIDs())
            assertEquals(disabled, store.load().scope)
            assertEquals(0, enabledProvinces())
            store.save(selected.copy(provinces = setOf("河南")))
            assertEquals(original, scopeIDs())
            assertEquals(setOf("河南"), store.load().scope.provinces)
            assertEquals(1, enabledProvinces())
        }

    @Test
    fun teacherIsDisabledAndUnrelatedContentAndSettingsRemain() =
        runBlocking {
            val db = database.openHelper.writableDatabase
            db.execSQL("INSERT INTO exam_scope VALUES ('teacher-existing', '教师编', NULL, 1)")
            db.execSQL("INSERT INTO settings VALUES ('unrelated', 'retained')")
            db.execSQL("INSERT INTO idiom_scope VALUES ('fixture-reference', 'teacher-existing')")
            store.save(ExamScopeSelection(), complete = true)
            db.query("SELECT enabled FROM exam_scope WHERE id = 'teacher-existing'").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            db.query("SELECT value FROM settings WHERE key = 'unrelated'").use {
                assertTrue(it.moveToFirst())
                assertEquals("retained", it.getString(0))
            }
            db.query("SELECT COUNT(*) FROM idiom_scope").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
        }

    @Test
    fun savingFailureRollsBackCompletionProvincesAndScopeRows() =
        runBlocking {
            val initial = ExamScopeSelection(provinces = setOf("河南"))
            store.save(initial)
            val ids = scopeIDs()
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_completion BEFORE INSERT ON settings " +
                    "WHEN NEW.key = 'firstLaunch.complete' BEGIN SELECT RAISE(ABORT, 'test failure'); END",
            )
            val result = runCatching { store.save(initial.copy(provinces = setOf("贵州")), complete = true) }
            assertTrue(result.isFailure)
            assertEquals(LaunchPreferences(false, initial), store.load())
            assertEquals(ids, scopeIDs())
        }

    private fun scopeIDs(): Set<String> =
        database.openHelper.writableDatabase.query("SELECT id FROM exam_scope").use {
            buildSet { while (it.moveToNext()) add(it.getString(0)) }
        }

    private fun enabledProvinces(): Int =
        database.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM exam_scope WHERE type = '省考' AND enabled = 1",
        ).use {
            check(it.moveToFirst())
            it.getInt(0)
        }
}
