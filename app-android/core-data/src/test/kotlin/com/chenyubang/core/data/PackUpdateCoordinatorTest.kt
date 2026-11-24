package com.chenyubang.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Suppress("MagicNumber", "LongMethod", "TooManyFunctions")
class PackUpdateCoordinatorTest : PackDatabaseTest() {
    private lateinit var context: Context
    private lateinit var directory: File
    private val userTables =
        listOf(
            "mistake",
            "mistake_idiom",
            "review_log",
            "idiom_state",
            "settings",
            "exam_scope",
            "user_example",
            "user_example_idiom",
        )

    @Before
    fun setUpUpdateDirectory() {
        context = ApplicationProvider.getApplicationContext()
        directory = Files.createTempDirectory("pack-update-test").toFile()
    }

    @After
    fun cleanUpdateDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun equalAndLowerVersionsNeverDownloadZip() =
        runBlocking {
            val source = FakeSource()
            val coordinator = coordinator(source)
            coordinator.bootstrap()
            for (version in listOf("v1.0.0", "v0.99.99")) {
                source.result = ChannelManifestResult.Success(channel(version = version), LATEST)
                assertEquals(PackCheckOutcome.UNCHANGED, coordinator.check())
                assertEquals(PackVersionStatus.UP_TO_DATE, coordinator.state.value?.status)
                assertEquals("v1.0.0", coordinator.state.value?.version)
                assertEquals(
                    PackCounts(59, 11),
                    coordinator.state.value?.let { PackCounts(it.idiomCount, it.exampleCount) },
                )
            }
            assertEquals(2, source.latestCalls)
            assertEquals(0, source.downloadCalls)
            assertFalse(coordinator.store.installed.exists())
        }

    @Test
    fun successfulUpdatePersistsActualZipAndPreservesAllEightUserTables() =
        runBlocking {
            seedAllUsers()
            val beforeUsers = snapshot(userTables)
            val update = updatedPack("v1.1.0")
            val source = FakeSource(update)
            val coordinator = coordinator(source)
            coordinator.bootstrap()
            assertEquals(PackCheckOutcome.UPDATED, coordinator.check())
            assertTrue(update.bytes.contentEquals(coordinator.store.installed.readBytes()))
            assertEquals(PackVersionStatus.UPDATE_OK, coordinator.state.value?.status)
            assertEquals("v1.1.0", coordinator.state.value?.version)
            assertEquals(
                PackCounts(59, 11),
                coordinator.state.value?.let { PackCounts(it.idiomCount, it.exampleCount) },
            )
            assertEquals(beforeUsers, snapshot(userTables))
            assertEquals(PackFixtureTest.IDIOM_COUNT, count("idiom"))
            assertTrue(database.idiomDao().search("步步为营").any { it.sense.endsWith("更新") })
            assertEquals(1, source.latestCalls)
            assertEquals(1, source.downloadCalls)
        }

    @Test
    fun everyChannelMismatchKeepsOldZipAndDatabase() =
        runBlocking {
            seedAllUsers()
            val old = updatedPack("v1.1.0")
            val initialSource = FakeSource(old)
            val coordinator = coordinator(initialSource)
            coordinator.bootstrap()
            assertEquals(PackCheckOutcome.UPDATED, coordinator.check())
            val oldBytes = coordinator.store.installed.readBytes()
            val before = snapshot(userTables + CONTENT_TABLES)

            val next = updatedPack("v1.2.0")
            val base = channel(version = next.validated.packVersion, update = next)
            val mismatches =
                listOf(
                    base.copy(packVersion = "v1.2.1", packFile = "pack-v1.2.1.zip"),
                    base.copy(builtAt = "2026-03-01T00:00:00Z"),
                    base.copy(counts = PackCounts(58, 11)),
                    base.copy(sha256 = "b".repeat(64)),
                )
            val source = FakeSource(next)
            val retryCoordinator = coordinator(source, PackInstallStore(context, directory))
            retryCoordinator.bootstrap()
            mismatches.forEach { mismatch ->
                source.result = ChannelManifestResult.Success(mismatch, LATEST)
                assertEquals(PackCheckOutcome.FAILED, retryCoordinator.check())
                assertEquals(PackVersionStatus.RECOVERABLE_FAIL, retryCoordinator.state.value?.status)
                assertEquals("v1.1.0", retryCoordinator.state.value?.version)
                assertTrue(oldBytes.contentEquals(retryCoordinator.store.installed.readBytes()))
                assertEquals(before, snapshot(userTables + CONTENT_TABLES))
            }
            assertEquals(4, source.downloadCalls)
        }

    @Test
    fun importFailureRestoresOldZipAndRoomTransactionThenManualRetrySucceeds() =
        runBlocking {
            val old = updatedPack("v1.1.0")
            val coordinator = coordinator(FakeSource(old))
            coordinator.bootstrap()
            assertEquals(PackCheckOutcome.UPDATED, coordinator.check())
            val oldBytes = coordinator.store.installed.readBytes()
            val before = snapshot(userTables + CONTENT_TABLES)
            val next = updatedPack("v1.2.0")
            val source = FakeSource(next)
            val retryCoordinator = coordinator(source, PackInstallStore(context, directory))
            retryCoordinator.bootstrap()
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_update BEFORE INSERT ON idiom " +
                    "BEGIN SELECT RAISE(ABORT, 'update import failure'); END",
            )
            try {
                assertEquals(PackCheckOutcome.FAILED, retryCoordinator.check())
                assertTrue(oldBytes.contentEquals(retryCoordinator.store.installed.readBytes()))
                assertEquals(before, snapshot(userTables + CONTENT_TABLES))
                assertEquals("v1.1.0", retryCoordinator.state.value?.version)
            } finally {
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_update")
            }
            assertEquals(1, source.latestCalls)
            assertEquals(PackCheckOutcome.UPDATED, retryCoordinator.check())
            assertEquals(2, source.latestCalls)
            assertTrue(next.bytes.contentEquals(retryCoordinator.store.installed.readBytes()))
            assertEquals("v1.2.0", retryCoordinator.state.value?.version)
        }

    @Test
    fun startupRepairsPersistedButUnimportedZipWithoutNetwork() =
        runBlocking {
            importer.import(pack)
            val update = updatedPack("v1.1.0")
            val store = PackInstallStore(context, directory)
            assertTrue(store.installed.parentFile?.isDirectory == true)
            store.installed.writeBytes(update.bytes)
            val source = FakeSource()
            val coordinator = coordinator(source, store)
            coordinator.bootstrap()
            assertEquals(0, source.latestCalls)
            assertEquals(0, source.downloadCalls)
            assertEquals(PackVersionStatus.BUILTIN, coordinator.state.value?.status)
            assertEquals("v1.1.0", coordinator.state.value?.version)
            assertTrue(database.idiomDao().search("步步为营").any { it.sense.endsWith("更新") })
            assertTrue(update.bytes.contentEquals(store.installed.readBytes()))
        }

    @Test
    fun corruptInstalledZipFallsBackToBuiltinAndRemovesCorruptFile() =
        runBlocking {
            val store = PackInstallStore(context, directory)
            assertTrue(store.installed.parentFile?.isDirectory == true)
            store.installed.writeBytes("corrupt".toByteArray())
            val source = FakeSource()
            val coordinator = coordinator(source, store)
            coordinator.bootstrap()
            assertEquals(0, source.latestCalls)
            assertEquals(PackVersionStatus.BUILTIN, coordinator.state.value?.status)
            assertEquals("v1.0.0", coordinator.state.value?.version)
            assertFalse(store.installed.exists())
            assertEquals(PackFixtureTest.IDIOM_COUNT, count("idiom"))
        }

    @Test
    fun mockFallbackLoadsActualRowsAndRecoversWithRealPack() =
        runBlocking {
            val store = PackInstallStore(context, directory, builtinAsset = "missing.zip")
            val source = FakeSource()
            val coordinator = coordinator(source, store)
            coordinator.bootstrap()
            assertEquals(PackVersionStatus.TEST_MOCK, coordinator.state.value?.status)
            assertEquals("测试数据（TEST）", coordinator.state.value?.version)
            assertEquals(3, coordinator.state.value?.idiomCount)
            assertEquals(2, coordinator.state.value?.exampleCount)
            assertEquals(3, count("idiom"))
            assertEquals(2, count("example"))

            assertEquals(PackCheckOutcome.FAILED, coordinator.check())
            assertEquals(PackVersionStatus.RECOVERABLE_FAIL, coordinator.state.value?.status)
            assertEquals("测试数据（TEST）", coordinator.state.value?.version)
            coordinator.continueOffline()
            assertEquals(PackVersionStatus.TEST_MOCK, coordinator.state.value?.status)

            for (version in listOf("v0.0.1", "v0.0.0")) {
                source.result = ChannelManifestResult.Success(channel(version), LATEST)
                assertEquals(PackCheckOutcome.FAILED, coordinator.check())
                assertEquals(PackVersionStatus.RECOVERABLE_FAIL, coordinator.state.value?.status)
                assertEquals("测试数据（TEST）", coordinator.state.value?.version)
            }
            assertEquals(0, source.downloadCalls)
            source.result = null

            val update = updatedPack("v1.1.0")
            source.update = update
            assertEquals(PackCheckOutcome.UPDATED, coordinator.check())
            assertEquals(PackVersionStatus.UPDATE_OK, coordinator.state.value?.status)
            assertEquals("v1.1.0", coordinator.state.value?.version)
            assertFalse(coordinator.state.value?.isTest == true)
            assertTrue(update.bytes.contentEquals(store.installed.readBytes()))
        }

    @Test
    fun concurrentChecksShareOneRequestAndCompletedRequestDoesNotStick() =
        runBlocking {
            val gate = CompletableDeferred<ChannelManifestResult>()
            val source = FakeSource(result = ChannelManifestResult.Recoverable("offline"), gate = gate)
            val coordinator = coordinator(source)
            coordinator.bootstrap()
            coroutineScope {
                val first = async { coordinator.check() }
                val second = async { coordinator.check() }
                while (source.latestCalls == 0) yield()
                assertEquals(PackVersionStatus.CHECKING, coordinator.state.value?.status)
                gate.complete(ChannelManifestResult.Recoverable("offline"))
                assertEquals(
                    listOf(PackCheckOutcome.FAILED, PackCheckOutcome.FAILED),
                    listOf(first.await(), second.await()),
                )
            }
            assertEquals(1, source.latestCalls)
            assertEquals(0, source.downloadCalls)
            assertEquals(PackCheckOutcome.FAILED, coordinator.check())
            assertEquals(2, source.latestCalls)
        }

    @Test
    fun failureDoesNotRetryAndContinueOfflineDoesNoRequestOrWrite() =
        runBlocking {
            val source = FakeSource()
            val coordinator = coordinator(source)
            coordinator.bootstrap()
            assertEquals(PackCheckOutcome.FAILED, coordinator.check())
            val files = directory.walkTopDown().filter { it.isFile }.map { it to it.readBytes() }.toList()
            coordinator.continueOffline()
            coordinator.continueOffline()
            assertEquals(1, source.latestCalls)
            assertEquals(0, source.downloadCalls)
            assertEquals(PackVersionStatus.OFFLINE, coordinator.state.value?.status)
            assertEquals("v1.0.0", coordinator.state.value?.version)
            assertEquals(files, directory.walkTopDown().filter { it.isFile }.map { it to it.readBytes() }.toList())
        }

    @Test
    fun updateRetainsOnlyMistakeAndStateReferencedOrphans() =
        runBlocking {
            val words = PackRows.idioms(pack)
            val mistakeId = words.first().entity.id
            val stateId = words[1].entity.id
            val removedId = words[2].entity.id
            seedAllUsers(mistakeId, stateId)
            val beforeUsers = snapshot(userTables)
            val bytes = PackFixtureTest.withoutIds(setOf(mistakeId, stateId, removedId), version = "v1.1.0")
            val validated = importer.validate(bytes.inputStream())
            val coordinator = coordinator(FakeSource(Update(bytes, validated)))
            coordinator.bootstrap()
            assertEquals(PackCheckOutcome.UPDATED, coordinator.check())
            assertEquals(2, count("idiom") - validated.counts.idioms)
            assertTrue(database.idiomDao().byId(mistakeId) != null)
            assertTrue(database.idiomDao().byId(stateId) != null)
            assertEquals(null, database.idiomDao().byId(removedId))
            assertEquals(beforeUsers, snapshot(userTables))
        }

    private fun coordinator(
        source: FakeSource,
        store: PackInstallStore = PackInstallStore(context, directory),
    ) = PackUpdateCoordinator(context, importer, source, store)

    private fun seedAllUsers(
        mistakeIdiom: String = PackRows.idioms(pack).first().entity.id,
        stateIdiom: String = PackRows.idioms(pack)[1].entity.id,
    ) {
        seedUsers(mistakeIdiom, stateIdiom)
        BackupDb.seedLocalRows(database)
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO user_example_idiom (userExampleId, idiomId) VALUES (?, ?)",
            arrayOf(BackupFixture.EXAMPLE_A, mistakeIdiom),
        )
    }

    private fun updatedPack(version: String): Update {
        val bytes =
            PackFixtureTest.mutate(
                idioms = { rows -> rows.firstChanged { it.with("sense", JsonPrimitive(it.string("sense") + "更新")) } },
                manifest = {
                    it.with(
                        "packVersion",
                        JsonPrimitive(version),
                    ).with("builtAt", JsonPrimitive("2026-02-28T00:00:00Z"))
                },
            )
        return Update(bytes, importer.validate(bytes.inputStream()))
    }

    private fun channel(
        version: String = "v1.0.0",
        update: Update? = null,
    ): ChannelManifest {
        val validated = update?.validated ?: pack
        return ChannelManifest(
            schemaVersion = 1,
            packVersion = version,
            builtAt = validated.builtAt,
            counts = validated.counts,
            sha256 = validated.sha256,
            sizeBytes = update?.bytes?.size ?: PackFixtureTest.bytes().size,
            packFile = "pack-$version.zip",
        )
    }

    private data class Update(val bytes: ByteArray, val validated: ValidatedPack)

    private class FakeSource(
        var update: Update? = null,
        var result: ChannelManifestResult? = null,
        private val gate: CompletableDeferred<ChannelManifestResult>? = null,
    ) : PackUpdateSource {
        var latestCalls = 0
        var downloadCalls = 0

        override suspend fun latest(): ChannelManifestResult {
            latestCalls++
            return gate?.await() ?: result ?: update?.let {
                ChannelManifestResult.Success(
                    ChannelManifest(
                        schemaVersion = 1,
                        packVersion = it.validated.packVersion,
                        builtAt = it.validated.builtAt,
                        counts = it.validated.counts,
                        sha256 = it.validated.sha256,
                        sizeBytes = it.bytes.size,
                        packFile = "pack-${it.validated.packVersion}.zip",
                    ),
                    LATEST,
                )
            } ?: ChannelManifestResult.Recoverable("offline")
        }

        override suspend fun download(
            manifest: ChannelManifest,
            target: File,
            onProgress: (Int) -> Unit,
        ): PackDownloadResult {
            downloadCalls++
            val bytes = requireNotNull(update).bytes
            target.writeBytes(bytes)
            onProgress(100)
            return PackDownloadResult.Success
        }
    }

    private companion object {
        const val LATEST = "https://packs.example.com/channel/latest.json"
    }
}
