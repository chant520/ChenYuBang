package com.chenyubang.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.InputStream
import java.lang.reflect.InvocationTargetException

/** 验证计划第 2 节补充项 #10/#11：ClearData 只清六表、PackVersion 三级兜底与下载上限。 */
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
    fun packVersionKeepsFallbackOrderMockIsolationAndDownloadBound() =
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            val builtinCalls = CountingSource(PackUpdateFetch.Unavailable)
            val builtin = PackVersion(context, builtinCalls).load()
            assertEquals(PackVersionStatus.BUILTIN, builtin.status)
            assertEquals("v1.0.0", builtin.version)
            assertEquals(PackFixtureTest.IDIOM_COUNT, builtin.idiomCount)
            assertEquals(PackFixtureTest.EXAMPLE_COUNT, builtin.exampleCount)
            assertEquals(0, builtinCalls.calls)

            val recoverCalls = CountingSource(PackUpdateFetch.Recoverable("网络失败"))
            val recoverable = PackVersion(context, recoverCalls).check("https://example.com/pack.zip")
            assertEquals(PackVersionStatus.RECOVERABLE_FAIL, recoverable.status)
            assertEquals("v1.0.0", recoverable.version)
            assertTrue(recoverable.canRetry)

            val remoteCalls = CountingSource(PackUpdateFetch.Success(PackFixtureTest.bytes()))
            val remote =
                PackVersion(context, remoteCalls, builtinAsset = "missing.zip").load("https://example.com/pack.zip")
            assertEquals(PackVersionStatus.UPDATE_OK, remote.status)
            assertEquals(1, remoteCalls.calls)

            val mockCalls = CountingSource(PackUpdateFetch.Unavailable)
            val mock =
                PackVersion(context, mockCalls, builtinAsset = "missing.zip").load("https://example.com/pack.zip")
            assertEquals(PackVersionStatus.TEST_MOCK, mock.status)
            assertEquals("测试数据（TEST）", mock.version)
            assertTrue(mock.isTest)
            assertEquals(3, mock.mockIdiomCount)
            assertEquals(2, mock.mockExampleCount)
            assertEquals(0, mock.idiomCount)
            assertEquals(0, mock.exampleCount)
            assertEquals("题库未获取成功", mock.reason)
            assertEquals(PackVersionStatus.CHECKING, PackVersion(context, mockCalls).checking(mock).status)

            val http = HttpPackUpdateSource().fetch("http://example.com/pack.zip")
            assertTrue(http is PackUpdateFetch.Recoverable)
            assertTrue((http as PackUpdateFetch.Recoverable).reason.contains("HTTPS"))
            assertBoundedDownloadRejectsOversize()
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

    private fun assertBoundedDownloadRejectsOversize() {
        val limit = 32 * 1024 * 1024
        val stream =
            object : InputStream() {
                var remaining = limit + 1

                override fun read(): Int =
                    if (remaining > 0) {
                        remaining--
                        1
                    } else {
                        -1
                    }

                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    if (remaining <= 0) return -1
                    val size = minOf(length, remaining)
                    java.util.Arrays.fill(buffer, offset, offset + size, 1.toByte())
                    remaining -= size
                    return size
                }
            }
        val method =
            HttpPackUpdateSource::class.java.getDeclaredMethod(
                "readBounded",
                InputStream::class.java,
                Int::class.javaPrimitiveType,
            )
        method.isAccessible = true
        val thrown =
            assertThrows(InvocationTargetException::class.java) {
                method.invoke(HttpPackUpdateSource(), stream, limit)
            }
        assertTrue(thrown.targetException is IllegalArgumentException)
    }

    private class CountingSource(private val result: PackUpdateFetch) : PackUpdateSource {
        var calls = 0

        override suspend fun fetch(url: String?): PackUpdateFetch {
            calls++
            return result
        }
    }
}
