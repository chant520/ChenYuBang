package com.chenyubang.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** ClearData 只清六表；题库启动三级兜底与更新失败保留版本。 */
@Suppress("MagicNumber", "LongMethod", "TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackupClearAndPackTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun clearDataRemovesOnlySixUserTablesAndKeepsThemeAndContent() =
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = BackupDb.open(context)
            seedContentAndUsers()
            val result = ClearData(database).clear()
            assertTrue(result.clearedAt.matches(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")))
            for (table in listOf(
                "user_example",
                "user_example_idiom",
                "mistake",
                "mistake_idiom",
                "review_log",
                "idiom_state",
            )) {
                assertEquals(table, 0, BackupDb.count(database, table))
            }
            assertEquals(2, BackupDb.count(database, "idiom"))
            assertEquals(1, BackupDb.count(database, "example"))
            assertEquals(2, BackupDb.count(database, "exam_scope"))
            assertEquals(1, BackupDb.count(database, "idiom_scope"))
            assertEquals(1, BackupDb.count(database, "example_idiom"))
            assertEquals("theme-light", setting("theme"))
            assertEquals(result.clearedAt, setting("lastClearedAt"))
            assertEquals(0, BackupRows.dangling(database.openHelper.writableDatabase))
        }

    @Test
    fun packBootstrapKeepsFallbackOrderAndTestIsolation() =
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = BackupDb.open(context)
            val importer = PackImporter(database, context)
            val source = CountingSource()
            val builtin = PackUpdateCoordinator(context, importer, source)
            builtin.bootstrap()
            assertEquals(PackVersionStatus.BUILTIN, builtin.state.value?.status)
            assertEquals("v1.0.0", builtin.state.value?.version)
            assertEquals(PackFixtureTest.IDIOM_COUNT, builtin.state.value?.idiomCount)
            assertEquals(PackFixtureTest.EXAMPLE_COUNT, builtin.state.value?.exampleCount)
            assertEquals(0, source.latestCalls)

            val recoverable = builtin.check()
            assertEquals(PackCheckOutcome.FAILED, recoverable)
            assertEquals(PackVersionStatus.RECOVERABLE_FAIL, builtin.state.value?.status)
            assertEquals("v1.0.0", builtin.state.value?.version)
            assertTrue(builtin.state.value?.canRetry == true)
            assertEquals(1, source.latestCalls)

            val directory = createTempDir("pack-mock")
            try {
                val store = PackInstallStore(context, directory, builtinAsset = "missing.zip")
                val mock = PackUpdateCoordinator(context, importer, source, store)
                mock.bootstrap()
                assertEquals(PackVersionStatus.TEST_MOCK, mock.state.value?.status)
                assertEquals("测试数据（TEST）", mock.state.value?.version)
                assertTrue(mock.state.value?.isTest == true)
                assertEquals(3, mock.state.value?.mockIdiomCount)
                assertEquals(2, mock.state.value?.mockExampleCount)
                assertEquals(3, mock.state.value?.idiomCount)
                assertEquals(2, mock.state.value?.exampleCount)
                assertEquals("题库未获取成功", mock.state.value?.reason)
                assertEquals(3, BackupDb.count(database, "idiom"))
                assertEquals(2, BackupDb.count(database, "example"))
                assertEquals(PackCheckOutcome.FAILED, mock.check())
                assertEquals(PackVersionStatus.RECOVERABLE_FAIL, mock.state.value?.status)
                assertEquals("测试数据（TEST）", mock.state.value?.version)
                mock.continueOffline()
                assertEquals(PackVersionStatus.TEST_MOCK, mock.state.value?.status)
                assertEquals(3, mock.state.value?.idiomCount)
                assertEquals(2, mock.state.value?.exampleCount)
            } finally {
                directory.deleteRecursively()
            }

            var opened = 0
            val insecure =
                HttpPackUpdateSource("http://example.com/latest.json", connectionOpener = {
                    opened++
                    error("must not open HTTP")
                }).latest()
            assertTrue(insecure is ChannelManifestResult.Recoverable)
            assertTrue((insecure as ChannelManifestResult.Recoverable).reason.contains("HTTPS"))
            assertEquals(0, opened)
        }

    private fun seedContentAndUsers() {
        BackupDb.seedIdioms(database)
        BackupDb.seedLocalRows(database)
        with(BackupDb) {
            exec(
                database,
                "INSERT INTO example (id, stem, optionA, optionB, optionC, optionD, answer, explain, " +
                    "examType, year, region, questionType) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf("example-1", "题干", "甲", "乙", "丙", "丁", "A", "解析", "国考", 2023, "中央", "单选"),
            )
            exec(
                database,
                "INSERT INTO example_idiom (exampleId, idiomId) VALUES (?,?)",
                arrayOf("example-1", BackupFixture.IDIOM_1),
            )
            exec(
                database,
                "INSERT INTO idiom_scope (idiomId, scopeId) VALUES (?,?)",
                arrayOf(BackupFixture.IDIOM_1, "old-scope-id"),
            )
            exec(
                database,
                "INSERT INTO mistake_idiom (mistakeId, idiomId) VALUES (?,?)",
                arrayOf(BackupFixture.MISTAKE_A, BackupFixture.IDIOM_1),
            )
            exec(
                database,
                "INSERT INTO user_example_idiom (userExampleId, idiomId) VALUES (?,?)",
                arrayOf(BackupFixture.EXAMPLE_A, BackupFixture.IDIOM_1),
            )
            exec(
                database,
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('theme', 'theme-light')",
            )
        }
    }

    private fun setting(key: String): String? =
        BackupDb.nullableText(database, "SELECT value FROM settings WHERE key = ?", arrayOf(key))

    private class CountingSource : PackUpdateSource {
        var latestCalls = 0
        var downloadCalls = 0

        override suspend fun latest(): ChannelManifestResult {
            latestCalls++
            return ChannelManifestResult.Recoverable("网络失败")
        }

        override suspend fun download(
            manifest: ChannelManifest,
            target: java.io.File,
            onProgress: (Int) -> Unit,
        ): PackDownloadResult {
            downloadCalls++
            return PackDownloadResult.Recoverable("不应下载")
        }
    }
}
