package com.chenyubang.core.data

import android.os.Build
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PackImporterTest : PackDatabaseTest() {
    @Test
    fun importsAuthenticEntirePackAndIsIdempotentWithoutWritingSettings() =
        runBlocking {
            val result = importer.import(pack)
            assertEquals("v1.0.0", result.packVersion)
            assertEquals(PackFixtureTest.DIGEST, result.sha256)
            assertEquals(PackCounts(PackFixtureTest.IDIOM_COUNT, PackFixtureTest.EXAMPLE_COUNT), result.counts)
            assertEquals(0, result.retainedIdioms)
            assertEquals(PackFixtureTest.IDIOM_COUNT, count("idiom"))
            assertEquals(PackFixtureTest.EXAMPLE_COUNT, count("example"))
            assertEquals(PackFixtureTest.IDIOM_COUNT, count("idiom_fts"))
            assertTrue(USER_TABLES.all { count(it) == 0 })
            val before = snapshot(CONTENT_TABLES)
            assertEquals(result, importer.import(pack))
            assertEquals(before, snapshot(CONTENT_TABLES))
            assertTrue(USER_TABLES.all { count(it) == 0 })
        }

    @Test
    fun preservesAllSixUserTablesFieldForFieldOnImportAndRefresh() =
        runBlocking {
            seedUsers()
            val before = snapshot(USER_TABLES)
            importer.import(pack)
            assertEquals(before, snapshot(USER_TABLES))
            importer.import(pack)
            importer.refreshScopeLinks(pack)
            assertEquals(before, snapshot(USER_TABLES))
        }

    @Test
    fun allRealIdiomsAreSearchableThroughFourFtsRoutes() =
        runBlocking {
            importer.import(pack)
            val dao = database.idiomDao()
            for (row in PackRows.idioms(pack)) {
                val idiom = row.entity
                for (query in listOf(idiom.word, idiom.pinyin.replace(" ", ""), idiom.initials)) {
                    assertTrue(dao.search(query, PackFixtureTest.IDIOM_COUNT).any { it.id == idiom.id })
                }
                val first = idiom.word.take(1)
                assertTrue(dao.search(first, PackFixtureTest.IDIOM_COUNT).any { it.id == idiom.id })
                assertTrue(
                    dao.hanFtsSearch(
                        "\"$first*\"",
                        "\"$first\"",
                        PackFixtureTest.IDIOM_COUNT,
                    ).any { it.id == idiom.id },
                )
            }
        }

    @Test
    fun mapsEveryRealWordAndQuestionNotJustManifestCounts() =
        runBlocking {
            importer.import(pack)
            val words = snapshot(listOf("idiom")).getValue("idiom").associateBy { it.getValue("id") }
            for (element in PackFixtureTest.json("idioms") as JsonArray) {
                val row = element as JsonObject
                val actual = words.getValue(row.string("id"))
                for (field in listOf("word", "pinyin", "initials", "sense", "origin", "color", "frequency")) {
                    assertEquals(row.optionalString(field), actual[field])
                }
            }
            val examples = snapshot(listOf("example")).getValue("example").associateBy { it.getValue("id") }
            for (element in PackFixtureTest.json("examples") as JsonArray) {
                val row = element as JsonObject
                val actual = examples.getValue(row.string("id"))
                for (field in listOf("stem", "answer", "explain", "examType", "year", "region", "questionType")) {
                    assertEquals(row.optionalString(field), actual[field])
                }
                val options = row.getValue("options") as JsonObject
                for (letter in listOf("A", "B", "C", "D")) assertEquals(options.string(letter), actual["option$letter"])
                assertNull(actual["imagePath"])
            }
        }

    @Test
    fun rejectsMistakeReferencedWordWithNewUuidAndRollsBackAllTables() =
        assertReferencedWordUuidReplacementRejected(PackRows.idioms(pack).first().entity.id)

    @Test
    fun rejectsStateReferencedWordWithNewUuidAndRollsBackAllTables() =
        assertReferencedWordUuidReplacementRejected(PackRows.idioms(pack)[1].entity.id)

    private fun assertReferencedWordUuidReplacementRejected(originalId: String) =
        runBlocking {
            seedUsers()
            importer.import(pack)
            val before = snapshot(USER_TABLES + CONTENT_TABLES)
            val original = PackRows.idioms(pack).single { it.entity.id == originalId }.entity
            val replacementId = UUID.randomUUID().toString()
            assertTrue(PackRows.idioms(pack).none { it.entity.id == replacementId })
            val updated =
                importer.validate(
                    PackFixtureTest.mutate(
                        idioms = { rows ->
                            JsonArray(
                                rows.map { element ->
                                    val row = element as JsonObject
                                    if (row.string("id") == originalId) {
                                        row.with("id", JsonPrimitive(replacementId))
                                    } else {
                                        row
                                    }
                                },
                            )
                        },
                        examples = { rows ->
                            JsonArray(
                                rows.map { element ->
                                    val row = element as JsonObject
                                    val links = row.getValue("idiomIds") as JsonArray
                                    row.with(
                                        "idiomIds",
                                        JsonArray(
                                            links.map { id ->
                                                if ((id as JsonPrimitive).content == originalId) {
                                                    JsonPrimitive(replacementId)
                                                } else {
                                                    id
                                                }
                                            },
                                        ),
                                    )
                                },
                            )
                        },
                    ).inputStream(),
                )
            assertEquals(pack.counts, updated.counts)
            assertTrue(pack.sha256 != updated.sha256)
            assertEquals(
                original.copy(id = replacementId),
                PackRows.idioms(updated).single {
                    it.entity.id == replacementId
                }.entity,
            )
            assertEquals(before, snapshot(USER_TABLES + CONTENT_TABLES))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { importer.import(updated) } }
            assertEquals(before, snapshot(USER_TABLES + CONTENT_TABLES))
            importer.refreshScopeLinks(pack)
            assertEquals(before, snapshot(USER_TABLES + CONTENT_TABLES))
        }

    @Test
    fun retainsOnlyUserReferencedDeletedWordsWithoutRecreatingOrphanScopeLinks() =
        runBlocking {
            val words = PackRows.idioms(pack)
            val mistakeWord = words.first().entity
            val stateId = PackRows.examples(pack).first().idiomIds.first()
            val stateWord = words.first { it.entity.id == stateId }.entity
            val unused = words.first { it.entity.id !in setOf(mistakeWord.id, stateId) }.entity
            seedUsers(mistakeWord.id, stateId)
            importer.import(pack)
            assertTrue(mistakeWord.id in linkedIds("user-national"))
            assertTrue(stateId in linkedIds("user-henan"))
            val before = snapshot(USER_TABLES)
            val smaller =
                importer.validate(
                    PackFixtureTest.withoutIds(setOf(mistakeWord.id, stateId, unused.id)).inputStream(),
                )
            val result = importer.import(smaller)
            assertEquals(2, result.retainedIdioms)
            assertEquals(smaller.counts.idioms + 2, count("idiom"))
            assertEquals(mistakeWord, database.idiomDao().byId(mistakeWord.id))
            assertEquals(stateWord, database.idiomDao().byId(stateId))
            assertNull(database.idiomDao().byId(unused.id))
            assertTrue(linkedIds("user-national").intersect(setOf(mistakeWord.id, stateId, unused.id)).isEmpty())
            assertTrue(linkedIds("user-henan").intersect(setOf(mistakeWord.id, stateId, unused.id)).isEmpty())
            importer.refreshScopeLinks(smaller)
            assertEquals(before, snapshot(USER_TABLES))
            assertEquals(smaller.counts.idioms, count("idiom_fts"))
            val dao = database.idiomDao()
            for (retained in listOf(mistakeWord, stateWord)) {
                for (query in listOf(
                    retained.word,
                    retained.pinyin.replace(" ", ""),
                    retained.initials,
                    retained.word.take(1),
                )) {
                    assertTrue(query, dao.search(query, PackFixtureTest.IDIOM_COUNT).none { it.id == retained.id })
                }
                val first = retained.word.take(1)
                assertTrue(
                    dao.hanFtsSearch("\"$first*\"", "\"$first\"", PackFixtureTest.IDIOM_COUNT)
                        .none { it.id == retained.id },
                )
            }
            assertTrue(dao.search(unused.pinyin, PackFixtureTest.IDIOM_COUNT).none { it.id == unused.id })
        }
}
