package com.chenyubang.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.ByteArrayInputStream

/** 验证计划第 2 节 #5/#7：manifest counts 五项一致、旧备份缺 userExamples 兼容。 */
@Suppress("MagicNumber")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackupDocumentTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val importer get() = BackupImporter(BackupDb.open(context), context)

    @Test
    fun manifestCountsAreExactlyFiveAndMatchActualArrays() {
        val snapshot = BackupFixture.incoming()
        val data = BackupFixture.data(snapshot)
        val metadata = BackupFixture.manifest(snapshot, data)
        val expected = BackupCounts(mistakes = 1, userExamples = 1, reviews = 1, idiomStates = 1, scopes = 2)
        assertEquals(BackupCounts(1, 1, 1, 1, 2), BackupDocument.counts(data))
        assertEquals(expected, BackupDocument.declaredCounts(metadata))
        assertEquals(5, (metadata.getValue("counts") as JsonObject).size)
        assertEquals(expected.mistakes, (data.getValue("mistakes") as JsonArray).size)
        assertEquals(expected.userExamples, (data.getValue("userExamples") as JsonArray).size)
        assertEquals(expected.reviews, (data.getValue("reviews") as JsonArray).size)
        assertEquals(expected.idiomStates, (data.getValue("idiomStates") as JsonArray).size)
        assertEquals(expected.scopes, (data.getValue("scopes") as JsonArray).size)
        assertFalse((metadata.getValue("counts") as JsonObject).containsKey("tags"))
    }

    @Test
    fun rejectsManifestCountsDifferentFromActualArrays() {
        val snapshot = BackupFixture.incoming()
        val data = BackupFixture.data(snapshot)
        val metadata = BackupFixture.manifest(snapshot, data)
        val counts = metadata.getValue("counts") as JsonObject
        val wrongCounts = JsonObject(counts + ("mistakes" to JsonPrimitive(9)))
        val wrong = JsonObject(metadata + ("counts" to wrongCounts))
        val error =
            assertThrows(DataError.Hard::class.java) {
                importer.validate(ByteArrayInputStream(BackupFixture.zip(data, wrong)))
            }
        assertTrue(error.message.orEmpty().contains("counts"))
    }

    @Test
    fun acceptsLegacyV1DataWithoutUserExamplesKey() {
        val snapshot = BackupFixture.incoming().copy(userExamples = emptyList())
        val data = JsonObject(BackupFixture.data(snapshot) - "userExamples")
        val legacyCounts =
            JsonObject(
                mapOf(
                    "mistakes" to JsonPrimitive(1),
                    "reviews" to JsonPrimitive(1),
                    "idiomStates" to JsonPrimitive(1),
                    "scopes" to JsonPrimitive(2),
                ),
            )
        val base =
            BackupDocument.manifest(snapshot.counts(), BackupFixture.UTC, BackupDocument.sha256(data))
        val legacy =
            JsonObject(
                (base - "counts" - "schemaVersion") +
                    ("counts" to legacyCounts) +
                    ("schemaVersion" to JsonPrimitive(1)),
            )
        val validated = importer.validate(ByteArrayInputStream(BackupFixture.zip(data, legacy)))
        assertEquals(0, validated.counts.userExamples)
        assertEquals(0, BackupDocument.declaredCounts(legacy).userExamples)
        assertTrue(validated.snapshot.userExamples.isEmpty())
    }

    @Test
    fun emptyIdiomIdsBackupPassesValidationAndDecodesToEmptyArray() {
        val snapshot =
            BackupFixture.incoming().copy(
                mistakes = listOf(BackupFixture.mistake(idiomIds = emptyList())),
                userExamples = listOf(BackupFixture.userExample(idiomIds = emptyList())),
            )
        val data = BackupFixture.data(snapshot)
        val validated =
            importer.validate(ByteArrayInputStream(BackupFixture.zip(data, BackupFixture.manifest(snapshot, data))))

        assertTrue(validated.snapshot.mistakes.single().idiomIds.isEmpty())
        assertTrue(validated.snapshot.userExamples.single().idiomIds.isEmpty())
        assertEquals(1, validated.counts.mistakes)
        assertEquals(1, validated.counts.userExamples)
    }
}
