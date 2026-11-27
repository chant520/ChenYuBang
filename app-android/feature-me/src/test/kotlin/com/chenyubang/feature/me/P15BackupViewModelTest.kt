package com.chenyubang.feature.me

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.chenyubang.core.data.BackupCounts
import com.chenyubang.core.data.BackupExportResult
import com.chenyubang.core.data.BackupImportResult
import com.chenyubang.core.data.BackupSnapshot
import com.chenyubang.core.data.ImportMode
import com.chenyubang.core.data.OrphanReport
import com.chenyubang.core.data.ValidatedBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** pickImport→confirmImport 状态机：校验失败不进 P22、放弃不写库、成功回调 onFinished(true)。 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class P15BackupViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exporter =
        object : P15Exporter {
            override suspend fun exportTo(output: OutputStream): BackupExportResult = error("unused")

            override suspend fun exportToFile(directory: File): BackupExportResult = error("unused")
        }

    private fun uri(): Uri {
        val uri = Uri.parse("content://test/backup.zip")
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        return uri
    }

    private fun backup() =
        ValidatedBackup(
            schemaVersion = 1,
            appVersion = "1.0",
            build = 1,
            exportedAt = "2026-11-27T00:00:00Z",
            counts = BackupCounts(),
            sha256 = "abc",
            snapshot = BackupSnapshot(),
        )

    private fun result() =
        BackupImportResult(
            mode = ImportMode.MERGE,
            imported = BackupCounts(mistakes = 1),
            orphans = OrphanReport(),
        )

    private class FakeImporter(
        private val validate: () -> ValidatedBackup,
        private val restore: () -> BackupImportResult = { error("restore should not run") },
    ) : P15Importer {
        var validateCalls = 0
        val restoreCalls = mutableListOf<Pair<ValidatedBackup, ImportMode>>()

        override fun validate(input: InputStream): ValidatedBackup {
            validateCalls++
            return validate()
        }

        override suspend fun restore(
            backup: ValidatedBackup,
            mode: ImportMode,
        ): BackupImportResult {
            restoreCalls += backup to mode
            return restore()
        }
    }

    @Test
    fun validationFailureStaysOnP15WithoutPendingBackup() =
        runTest {
            val importer =
                FakeImporter(validate = { error("sha256 mismatch") })
            val model = P15BackupViewModel(context, exporter, importer, ioDispatcher = Dispatchers.Unconfined)
            var validatedCalls = 0

            model.pickImport(uri()) { validatedCalls++ }
            advanceUntilIdle()

            assertEquals(1, importer.validateCalls)
            assertEquals(0, validatedCalls)
            assertNull(model.state.value.pendingBackup)
            val banner = model.state.value.banner as P15Banner.HardError
            assertEquals(P15_REJECT_TITLE, banner.title)
        }

    @Test
    fun validBackupSetsPendingAndOpensImportMode() =
        runTest {
            val backup = backup()
            val importer = FakeImporter(validate = { backup })
            val model = P15BackupViewModel(context, exporter, importer, ioDispatcher = Dispatchers.Unconfined)
            var validatedCalls = 0

            model.pickImport(uri()) { validatedCalls++ }
            advanceUntilIdle()

            assertEquals(1, validatedCalls)
            assertNull(model.state.value.banner)
            assertSame(backup, model.state.value.pendingBackup)
        }

    @Test
    fun discardImportClearsPendingWithoutTouchingDatabase() =
        runTest {
            val importer = FakeImporter(validate = { backup() })
            val model = P15BackupViewModel(context, exporter, importer, ioDispatcher = Dispatchers.Unconfined)

            model.pickImport(uri()) {}
            advanceUntilIdle()
            model.discardImport()

            assertNull(model.state.value.pendingBackup)
            assertTrue(importer.restoreCalls.isEmpty())
        }

    @Test
    fun confirmImportSuccessShowsResultAndFinishesTrue() =
        runTest {
            val backup = backup()
            val importer = FakeImporter(validate = { backup }, restore = { result() })
            val model = P15BackupViewModel(context, exporter, importer, ioDispatcher = Dispatchers.Unconfined)
            var finished: Boolean? = null

            model.pickImport(uri()) {}
            advanceUntilIdle()
            model.confirmImport(ImportMode.MERGE) { finished = it }
            advanceUntilIdle()

            assertEquals(listOf(backup to ImportMode.MERGE), importer.restoreCalls)
            assertEquals(true, finished)
            assertNull(model.state.value.pendingBackup)
            assertTrue(model.state.value.banner is P15Banner.Imported)
        }

    @Test
    fun confirmImportFailureFinishesFalseAndClearsPending() =
        runTest {
            val importer = FakeImporter(validate = { backup() }, restore = { error("disk full") })
            val model = P15BackupViewModel(context, exporter, importer, ioDispatcher = Dispatchers.Unconfined)
            var finished: Boolean? = null

            model.pickImport(uri()) {}
            advanceUntilIdle()
            model.confirmImport(ImportMode.OVERWRITE) { finished = it }
            advanceUntilIdle()

            assertEquals(false, finished)
            assertNull(model.state.value.pendingBackup)
            val banner = model.state.value.banner as P15Banner.HardError
            assertEquals(P15_IMPORT_FAILURE_TITLE, banner.title)
        }

    @Test
    fun confirmImportWithoutPendingBackupIsNoOp() =
        runTest {
            val importer = FakeImporter(validate = { backup() })
            val model = P15BackupViewModel(context, exporter, importer, ioDispatcher = Dispatchers.Unconfined)
            var finished: Boolean? = null

            model.confirmImport(ImportMode.MERGE) { finished = true }
            advanceUntilIdle()

            assertTrue(importer.restoreCalls.isEmpty())
            assertNull(finished)
            assertNull(model.state.value.banner)
        }
}
