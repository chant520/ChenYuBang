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
class PackScopeTest : PackDatabaseTest() {
    @Test
    fun provincialNullBlankUnrelatedAndDisabledScopesNeverLink() =
        runBlocking {
            addScope("null", "省考")
            addScope("blank", "省考", "")
            addScope("unrelated", "省考", "河北")
            addScope("disabled", "省考", "河南", enabled = false)
            val before = snapshot(USER_TABLES)
            importer.import(pack)
            assertEquals(0, count("idiom_scope"))
            assertEquals(before, snapshot(USER_TABLES))
        }

    @Test
    fun henanAndGuizhouUseOnlyTheirOwnRealExamEvidence() =
        runBlocking {
            addScope("henan", "省考", "河南")
            addScope("guizhou", "省考", "贵州")
            addScope("national", "国考")
            addScope("institution", "事业编")
            val before = snapshot(USER_TABLES)
            importer.import(pack)
            assertEquals(expectedProvince("河南"), linkedIds("henan"))
            assertEquals(expectedProvince("贵州"), linkedIds("guizhou"))
            assertEquals(HENAN_COUNT, linkedIds("henan").size)
            assertEquals(GUIZHOU_COUNT, linkedIds("guizhou").size)
            assertTrue(linkedIds("henan").intersect(linkedIds("guizhou")).isEmpty())
            val rows = PackRows.idioms(pack)
            assertEquals(rows.filter { "国考" in it.scopes }.map { it.entity.id }.toSet(), linkedIds("national"))
            assertEquals(rows.filter { "事业编" in it.scopes }.map { it.entity.id }.toSet(), linkedIds("institution"))
            assertEquals(before, snapshot(USER_TABLES))
        }

    @Test
    fun importBeforeScopeSaveThenRefreshRebuildsLinksWithoutTouchingAnyUserField() =
        runBlocking {
            importer.import(pack)
            assertEquals(0, count("idiom_scope"))
            ExamScopeStore(database).save(
                ExamScopeSelection(national = false, institution = false, provinces = setOf("河南", "贵州")),
                complete = true,
            )
            val beforeUsers = snapshot(USER_TABLES)
            val beforeContent = snapshot(CONTENT_TABLES - "idiom_scope")
            importer.refreshScopeLinks(pack)
            val selected = snapshot(listOf("exam_scope")).getValue("exam_scope").filter { it["enabled"] == "1" }
            for (row in selected) {
                assertEquals(expectedProvince(row.getValue("province")!!), linkedIds(row.getValue("id")!!))
            }
            assertEquals(HENAN_COUNT + GUIZHOU_COUNT, count("idiom_scope"))
            val links = snapshot(listOf("idiom_scope"))
            importer.refreshScopeLinks(pack)
            assertEquals(links, snapshot(listOf("idiom_scope")))
            assertEquals(beforeUsers, snapshot(USER_TABLES))
            assertEquals(beforeContent, snapshot(CONTENT_TABLES - "idiom_scope"))
        }

    @Test
    fun refreshRefusesUninstalledOrStalePackWithoutDeletingLinks() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { importer.refreshScopeLinks(pack) } }
        runBlocking {
            addScope("henan", "省考", "河南")
            importer.import(pack)
        }
        val before = snapshot(USER_TABLES + CONTENT_TABLES)
        val stale =
            importer.validate(
                PackFixtureTest.mutate(idioms = {
                    it.firstChanged { row -> row.with("sense", JsonPrimitive(row.string("sense") + " ")) }
                }).inputStream(),
            )
        assertThrows(IllegalArgumentException::class.java) { runBlocking { importer.refreshScopeLinks(stale) } }
        assertEquals(before, snapshot(USER_TABLES + CONTENT_TABLES))
    }

    @Test
    fun refreshRejectsPreviousPackWithSameIdsAndChangedScopesWithoutTouchingUsersOrLinks() =
        runBlocking {
            seedUsers()
            addScope("user-institution", "事业编")
            val users = snapshot(USER_TABLES)
            importer.import(pack)
            assertTrue(linkedIds("user-national").isNotEmpty())
            val updated =
                importer.validate(
                    PackFixtureTest.mutate(idioms = { rows ->
                        JsonArray(
                            rows.map { element ->
                                (element as JsonObject).with("scopes", JsonArray(listOf(JsonPrimitive("事业编"))))
                            },
                        )
                    }).inputStream(),
                )
            val ids = PackRows.idioms(pack).map { it.entity.id }.toSet()
            assertEquals(ids, PackRows.idioms(updated).map { it.entity.id }.toSet())
            assertTrue(pack.sha256 != updated.sha256)
            importer.import(updated)
            assertTrue(linkedIds("user-national").isEmpty())
            assertTrue(linkedIds("user-henan").isEmpty())
            assertEquals(ids, linkedIds("user-institution"))
            assertEquals(users, snapshot(USER_TABLES))
            val before = snapshot(USER_TABLES + CONTENT_TABLES)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { importer.refreshScopeLinks(pack) } }
            assertEquals(before, snapshot(USER_TABLES + CONTENT_TABLES))
            importer.refreshScopeLinks(updated)
            assertEquals(before, snapshot(USER_TABLES + CONTENT_TABLES))
        }

    private fun expectedProvince(province: String): Set<String> {
        val eligible = PackRows.idioms(pack).filter { "省考" in it.scopes }.map { it.entity.id }.toSet()
        val questions = PackFixtureTest.json("examples") as JsonArray
        return questions.map { it as JsonObject }
            .filter { it.string("examType") == "省考" && it.optionalString("region") == province }
            .flatMap { (it.getValue("idiomIds") as JsonArray).map { id -> (id as JsonPrimitive).content } }
            .filter { it in eligible }.toSet()
    }

    companion object {
        private const val HENAN_COUNT = 20
        private const val GUIZHOU_COUNT = 3
    }
}
