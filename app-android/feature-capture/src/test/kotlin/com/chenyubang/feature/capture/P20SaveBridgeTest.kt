package com.chenyubang.feature.capture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.data.IdiomEntity
import com.chenyubang.core.data.MistakeEntity
import com.chenyubang.core.data.MistakeIdiomEntity
import com.chenyubang.core.data.UserExampleDao
import com.chenyubang.core.data.UserExampleEntity
import com.chenyubang.core.data.UserExampleIdiomEntity
import com.chenyubang.core.data.UserExampleSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class P20SaveBridgeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `performSave uses current model state and resets saving`() =
        runBlocking {
            val answers = mutableListOf<String?>()
            val dao =
                object : UserExampleDao() {
                    override suspend fun insertUserExample(entity: UserExampleEntity) {
                        answers.add(entity.myAnswer)
                    }

                    override suspend fun insertUserExampleIdioms(rows: List<UserExampleIdiomEntity>) = Unit

                    override suspend fun insertMistake(entity: MistakeEntity) = Unit

                    override suspend fun insertMistakeIdioms(rows: List<MistakeIdiomEntity>) = Unit

                    override suspend fun byId(id: String): UserExampleEntity? = null

                    override suspend fun mistakesOf(id: String): List<MistakeEntity> = emptyList()

                    override suspend fun linkedIdioms(id: String): List<IdiomEntity> = emptyList()
                }
            val source = File(temporaryFolder.root, "source.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val saver =
                UserExampleSaver(
                    dao,
                    File(temporaryFolder.root, "images"),
                    { "saved-id" },
                    { "2026-01-01T00:00:00Z" },
                )
            val database =
                Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).build()
            val model = P20FormViewModel(CoroutineScope(Dispatchers.Unconfined))
            val idiom =
                IdiomEntity(
                    id = "idiom-1",
                    word = "按部就班",
                    pinyin = "",
                    initials = "",
                    sense = "",
                    color = "",
                    synonyms = "[]",
                    frequency = 5,
                )
            model.candidate.bootstrap("按部就班", listOf(idiom))
            model.form.setAnswer("B")
            val deps = P20FormDeps(database, saver, source.absolutePath)
            val draft = OcrDraft("题干", listOf("甲", "乙", "丙", "丁"))

            var savedId: String? = null
            performSave(draft, deps, model) { id, _ -> savedId = id }
            database.close()

            assertEquals(listOf("B"), answers)
            assertEquals("saved-id", savedId)
            assertFalse(model.state.value.form.saving)
        }

    @Test
    fun `performSave skips re-entrant call while saving`() =
        runBlocking {
            val answers = mutableListOf<String?>()
            val dao =
                object : UserExampleDao() {
                    override suspend fun insertUserExample(entity: UserExampleEntity) {
                        answers.add(entity.myAnswer)
                    }

                    override suspend fun insertUserExampleIdioms(rows: List<UserExampleIdiomEntity>) = Unit

                    override suspend fun insertMistake(entity: MistakeEntity) = Unit

                    override suspend fun insertMistakeIdioms(rows: List<MistakeIdiomEntity>) = Unit

                    override suspend fun byId(id: String): UserExampleEntity? = null

                    override suspend fun mistakesOf(id: String): List<MistakeEntity> = emptyList()

                    override suspend fun linkedIdioms(id: String): List<IdiomEntity> = emptyList()
                }
            val source = File(temporaryFolder.root, "source2.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val saver =
                UserExampleSaver(
                    dao,
                    File(temporaryFolder.root, "images2"),
                    { "id-2" },
                    { "2026-01-01T00:00:00Z" },
                )
            val model = P20FormViewModel(CoroutineScope(Dispatchers.Unconfined))
            val idiom =
                IdiomEntity(
                    id = "idiom-2",
                    word = "按部就班",
                    pinyin = "",
                    initials = "",
                    sense = "",
                    color = "",
                    synonyms = "[]",
                    frequency = 5,
                )
            model.candidate.bootstrap("按部就班", listOf(idiom))
            model.form.setAnswer("A")
            model.form.setSaveState(saving = true)
            val deps =
                P20FormDeps(
                    Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        AppDatabase::class.java,
                    ).build(),
                    saver,
                    source.absolutePath,
                )

            performSave(OcrDraft("题干", listOf("甲")), deps, model) { _, _ -> Unit }

            assertTrue(answers.isEmpty())
            assertTrue(model.state.value.form.saving)
        }
}
