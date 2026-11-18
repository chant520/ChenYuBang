package com.chenyubang.core.data

import android.os.Build
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PackFailureTest : PackDatabaseTest() {
    @Test
    fun invalidSemanticPackagesNeverChangeContentFtsOrAnyUserField() =
        runBlocking {
            seedUsers()
            importer.import(pack)
            val before = snapshot(USER_TABLES + CONTENT_TABLES)
            val candidates = semanticFailures()
            for (bytes in candidates) {
                assertThrows(IllegalArgumentException::class.java) { importer.validate(bytes.inputStream()) }
                assertEquals(before, snapshot(USER_TABLES + CONTENT_TABLES))
            }
            assertTrue(database.idiomDao().search("bbwy").any { it.word == "步步为营" })
        }

    @Test
    fun invalidArchivesNeverChangeContentFtsOrAnyUserField() =
        runBlocking {
            seedUsers()
            importer.import(pack)
            val before = snapshot(USER_TABLES + CONTENT_TABLES)
            val real = PackFixtureTest.bytes()
            val oversized = PackFixtureTest.files().getValue("idioms.json").copyOf(PackArchive.MAX_ENTRY_BYTES + 1)
            val invalid =
                listOf(
                    PackFixtureTest.archive(PackFixtureTest.files() + ("extra.json" to byteArrayOf())),
                    PackZipMutationTest.rename(real, "examples.json", "manifest.json"),
                    PackFixtureTest.archive(PackFixtureTest.files() - "idioms.json" + ("../bad.json" to byteArrayOf())),
                    real.copyOf(PackZipMutationTest.centralOffset(real)),
                    PackZipMutationTest.corruptCrc(real),
                    real.copyOf(PackArchive.MAX_INPUT_BYTES + 1),
                    PackFixtureTest.archive(PackFixtureTest.files() + ("idioms.json" to oversized)),
                )
            for (bytes in invalid) {
                assertThrows(Exception::class.java) { importer.validate(bytes.inputStream()) }
                assertEquals(before, snapshot(USER_TABLES + CONTENT_TABLES))
            }
        }

    @Test
    fun formalDatabaseTriggerAfterFtsRebuildProvesAtomicRollback() =
        runBlocking {
            seedUsers()
            importer.import(pack)
            val before = snapshot(USER_TABLES + CONTENT_TABLES)
            val modified =
                importer.validate(
                    PackFixtureTest.mutate(idioms = {
                        it.firstChanged { row -> row.with("sense", JsonPrimitive(row.string("sense") + " ")) }
                    }).inputStream(),
                )
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_pack_scope BEFORE INSERT ON idiom_scope " +
                    "BEGIN SELECT RAISE(ABORT, 'pack transaction failure'); END",
            )
            try {
                val failure = assertThrows(Exception::class.java) { runBlocking { importer.import(modified) } }
                assertTrue(failure.message.orEmpty().contains("pack transaction failure"))
                assertEquals(before, snapshot(USER_TABLES + CONTENT_TABLES))
                assertTrue(database.idiomDao().search("bbwy").any { it.word == "步步为营" })
            } finally {
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_pack_scope")
            }
            importer.import(modified)
            assertEquals(before.filterKeys { it in USER_TABLES }, snapshot(USER_TABLES))
            assertEquals(PackFixtureTest.IDIOM_COUNT, count("idiom_fts"))
        }

    private fun semanticFailures(): List<ByteArray> {
        val firstId = ((PackFixtureTest.json("idioms") as JsonArray).first() as JsonObject).getValue("id")
        return listOf(
            PackFixtureTest.mutate(manifest = { it.with("sha256", JsonPrimitive("0".repeat(DIGEST_LENGTH))) }),
            PackFixtureTest.mutate(manifest = { it.with("schemaVersion", JsonPrimitive(2)) }),
            PackFixtureTest.mutate(idioms = {
                    rows ->
                rows.firstChanged { it.with("id", (rows[1] as JsonObject).getValue("id")) }
            }),
            PackFixtureTest.mutate(idioms = {
                    rows ->
                rows.firstChanged { it.with("word", (rows[1] as JsonObject).getValue("word")) }
            }),
            PackFixtureTest.mutate(examples = { it.firstChanged { row -> row.with("id", firstId) } }),
            PackFixtureTest.mutate(examples = { it.firstChanged { row -> row.with("answer", JsonPrimitive("E")) } }),
            PackFixtureTest.mutate(idioms = { it.firstChanged { row -> row.with("frequency", JsonPrimitive("1")) } }),
            PackFixtureTest.mutate(idioms = { it.firstChanged { row -> row.with("unknown", JsonPrimitive(true)) } }),
            PackFixtureTest.mutate(examples = {
                it.firstChanged { row ->
                    row.with("idiomIds", JsonArray(listOf(JsonPrimitive("00000000-0000-4000-8000-000000000000"))))
                }
            }),
        )
    }

    companion object {
        private const val DIGEST_LENGTH = 64
    }
}
