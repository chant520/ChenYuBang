package com.chenyubang.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 阶段 6 数据层契约：A6 / A7 独立 upsert、C9 精确切换与既有复习 / 备份 / 清空回归。 */
@Suppress("MagicNumber", "TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase6DataContractTest {
    private lateinit var db: AppDatabase
    private lateinit var idiomStates: IdiomStateDao
    private lateinit var reviews: ReviewDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        idiomStates = db.idiomStateDao()
        reviews = db.reviewDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun missingStateReadDefaultsWithoutWriting() =
        runBlocking {
            assertEquals(
                IdiomLearningState("idiom-a", IdiomLearningStatus.UNLEARNED, false),
                idiomStates.state("idiom-a"),
            )
            assertEquals(
                IdiomLearningState("idiom-b", IdiomLearningStatus.UNLEARNED, false),
                idiomStates.state("idiom-b"),
            )
            assertNull(rawState("idiom-a"))
            assertNull(rawState("idiom-b"))
            assertEquals(0, rawCount("idiom_state"))
        }

    @Test
    fun statusWritePreservesExistingFavorite() =
        runBlocking {
            insertRawState("idiom-a", "学过", favorite = true)
            idiomStates.setStatus("idiom-a", IdiomLearningStatus.MASTERED)
            assertEquals(mapOf("idiomId" to "idiom-a", "status" to "已掌握", "favorite" to "1"), rawState("idiom-a"))
        }

    @Test
    fun favoriteWritePreservesExistingStatus() =
        runBlocking {
            insertRawState("idiom-a", "已掌握", favorite = false)
            idiomStates.setFavorite("idiom-a", favorite = true)
            assertEquals(mapOf("idiomId" to "idiom-a", "status" to "已掌握", "favorite" to "1"), rawState("idiom-a"))
        }

    @Test
    fun firstStatusWriteInsertsDefaultFavorite() =
        runBlocking {
            idiomStates.setStatus("idiom-a", IdiomLearningStatus.LEARNED)
            assertEquals(mapOf("idiomId" to "idiom-a", "status" to "学过", "favorite" to "0"), rawState("idiom-a"))
            assertEquals(1, rawCount("idiom_state"))
        }

    @Test
    fun firstFavoriteWriteInsertsDefaultStatus() =
        runBlocking {
            idiomStates.setFavorite("idiom-a", favorite = true)
            assertEquals(mapOf("idiomId" to "idiom-a", "status" to "未学", "favorite" to "1"), rawState("idiom-a"))
            assertEquals(1, rawCount("idiom_state"))
        }

    @Test
    fun explicitUnlearnedKeepsRow() =
        runBlocking {
            idiomStates.setStatus("idiom-a", IdiomLearningStatus.UNLEARNED)
            assertEquals(mapOf("idiomId" to "idiom-a", "status" to "未学", "favorite" to "0"), rawState("idiom-a"))
            assertEquals(1, rawCount("idiom_state"))
        }

    @Test
    fun unfavoriteKeepsRowAndStatus() =
        runBlocking {
            insertRawState("idiom-a", "学过", favorite = true)
            idiomStates.setFavorite("idiom-a", favorite = false)
            assertEquals(mapOf("idiomId" to "idiom-a", "status" to "学过", "favorite" to "0"), rawState("idiom-a"))
            assertEquals(1, rawCount("idiom_state"))
        }

    @Test
    fun repeatedStateWritesAreIdempotent() =
        runBlocking {
            idiomStates.setStatus("idiom-a", IdiomLearningStatus.LEARNED)
            idiomStates.setStatus("idiom-a", IdiomLearningStatus.LEARNED)
            idiomStates.setFavorite("idiom-a", favorite = true)
            idiomStates.setFavorite("idiom-a", favorite = true)
            assertEquals(mapOf("idiomId" to "idiom-a", "status" to "学过", "favorite" to "1"), rawState("idiom-a"))
            assertEquals(1, rawCount("idiom_state"))
        }

    @Test
    fun statusTypeContainsOnlyThreeExactValues() {
        assertEquals(
            listOf("未学", "学过", "已掌握"),
            IdiomLearningStatus.entries.map { it.storedValue },
        )
        assertEquals(
            listOf("待复习", "已掌握"),
            MistakeReviewState.entries.map { it.storedValue },
        )
    }

    @Test
    fun statusParserRejectsUnknownValues() {
        for (value in listOf("", "待复习", "mastered")) {
            assertThrows(IllegalStateException::class.java) {
                IdiomLearningStatus.fromStored(value)
            }
        }
    }

    @Test
    fun missingMistakeIdReturnsNotFoundWithoutInserting() =
        runBlocking {
            seedMistakes()
            val before = rawCount("mistake")
            assertEquals(
                MistakeStateUpdateResult.NOT_FOUND,
                reviews.setState("missing-mistake", MistakeReviewState.MASTERED),
            )
            assertEquals(
                MistakeStateUpdateResult.NOT_FOUND,
                reviews.setState("ue-shared", MistakeReviewState.MASTERED),
            )
            assertEquals(before, rawCount("mistake"))
            assertNull(db.mistakeDao().byId("missing-mistake"))
            assertNull(db.mistakeDao().byId("ue-shared"))
        }

    @Test
    fun stateChangeTouchesOnlyTargetState() =
        runBlocking {
            seedMistakes()
            val targetBefore = requireNotNull(rawRow("mistake", "m-target"))
            val otherBefore = requireNotNull(rawRow("mistake", "m-other"))
            val exampleBefore = requireNotNull(rawRow("user_example", "ue-shared"))
            val linksBefore = rawRows("SELECT mistakeId, idiomId FROM mistake_idiom ORDER BY mistakeId, idiomId")
            val logsBefore = rawRows("SELECT id, mistakeId, result, at FROM review_log ORDER BY id")

            assertEquals(MistakeStateUpdateResult.UPDATED, reviews.setState("m-target", MistakeReviewState.MASTERED))

            val targetAfter = requireNotNull(rawRow("mistake", "m-target"))
            assertEquals("已掌握", targetAfter["state"])
            assertEquals(targetBefore.filterKeys { it != "state" }, targetAfter.filterKeys { it != "state" })
            assertEquals(otherBefore, rawRow("mistake", "m-other"))
            assertEquals(exampleBefore, rawRow("user_example", "ue-shared"))
            assertEquals(
                linksBefore,
                rawRows("SELECT mistakeId, idiomId FROM mistake_idiom ORDER BY mistakeId, idiomId"),
            )
            assertEquals(logsBefore, rawRows("SELECT id, mistakeId, result, at FROM review_log ORDER BY id"))
        }

    @Test
    fun masteredLeavesPendingQueuesButRemainsInAllMistakes() =
        runBlocking {
            seedMistakes()
            reviews.setState("m-target", MistakeReviewState.MASTERED)

            assertEquals(listOf("m-other"), reviews.pendingRows(null).map { it.mistakeId })
            assertEquals(listOf("m-other"), reviews.pendingRows("idiom-a").map { it.mistakeId })
            assertTrue(reviews.pendingRows("idiom-b").isEmpty())
            assertEquals(1, reviews.pendingCount(null))
            assertEquals(1, reviews.pendingCount("idiom-a"))
            assertEquals(0, reviews.pendingCount("idiom-b"))

            val all = db.mistakeQueryDao().mistakesByTime().first()
            assertEquals(listOf("m-other", "m-target"), all.map { it.mistake.id })
            assertEquals("已掌握", all.last().mistake.state)
        }

    @Test
    fun pendingReturnsToQueuesWithOriginalCreatedAtOrder() =
        runBlocking {
            seedMistakes()
            reviews.setState("m-target", MistakeReviewState.MASTERED)
            reviews.setState("m-target", MistakeReviewState.PENDING)

            assertEquals(listOf("m-other", "m-target"), reviews.pendingRows(null).map { it.mistakeId })
            assertEquals(listOf("m-other", "m-target"), reviews.pendingRows("idiom-a").map { it.mistakeId })
            assertEquals(listOf("m-target"), reviews.pendingRows("idiom-b").map { it.mistakeId })
            assertEquals("2026-11-20T09:00:00Z", rawRow("mistake", "m-target")?.get("createdAt"))
        }

    @Test
    fun repeatedMistakeStateChangesAreIdempotent() =
        runBlocking {
            seedMistakes()
            assertEquals(MistakeStateUpdateResult.UPDATED, reviews.setState("m-target", MistakeReviewState.MASTERED))
            val mastered = rawRow("mistake", "m-target")
            assertEquals(MistakeStateUpdateResult.UPDATED, reviews.setState("m-target", MistakeReviewState.MASTERED))
            assertEquals(mastered, rawRow("mistake", "m-target"))

            assertEquals(MistakeStateUpdateResult.UPDATED, reviews.setState("m-target", MistakeReviewState.PENDING))
            val pending = rawRow("mistake", "m-target")
            assertEquals(MistakeStateUpdateResult.UPDATED, reviews.setState("m-target", MistakeReviewState.PENDING))
            assertEquals(pending, rawRow("mistake", "m-target"))
            assertEquals(2, rawCount("mistake"))
        }

    @Test
    fun reviewRecordDoesNotChangeEitherManualState() =
        runBlocking {
            seedMistakes()
            reviews.setState("m-target", MistakeReviewState.MASTERED)
            reviews.record("m-target", ReviewScoring.RESULT_CORRECT, "2026-11-25T10:00:00Z")
            assertEquals("已掌握", rawRow("mistake", "m-target")?.get("state"))

            reviews.setState("m-target", MistakeReviewState.PENDING)
            reviews.record("m-target", ReviewScoring.RESULT_WRONG, "2026-11-25T11:00:00Z")
            assertEquals("待复习", rawRow("mistake", "m-target")?.get("state"))
            assertEquals(3, rawRows("SELECT id FROM review_log WHERE mistakeId = 'm-target'").size)
        }

    @Test
    fun newStateWritesAreCoveredByBackupSnapshotAndClear() =
        runBlocking {
            seedMistakes()
            idiomStates.setStatus("idiom-a", IdiomLearningStatus.LEARNED)
            idiomStates.setFavorite("idiom-a", favorite = true)
            reviews.setState("m-target", MistakeReviewState.MASTERED)

            val snapshot = BackupSnapshotReader.read(db)
            assertEquals(BackupIdiomState("idiom-a", "学过", true), snapshot.idiomStates.single())
            assertEquals("已掌握", snapshot.mistakes.single { it.id == "m-target" }.state)

            ClearData(db).clear()
            for (table in listOf("idiom_state", "mistake", "mistake_idiom", "review_log", "user_example")) {
                assertEquals(table, 0, rawCount(table))
            }
            assertEquals(3, rawCount("idiom"))
        }

    private suspend fun seedMistakes() {
        db.idiomDao().upsertAll(
            listOf(
                idiom("idiom-a"),
                idiom("idiom-b"),
                idiom("idiom-c"),
            ),
        )
        db.userExampleDao().insertUserExample(
            UserExampleEntity(
                id = "ue-shared",
                imagePath = "images/shared.jpg",
                ocrText = "识别正文",
                stem = "共享录入题",
                optionA = "甲",
                optionB = "乙",
                optionC = "丙",
                optionD = "丁",
                myAnswer = "B",
                isMistake = true,
                examType = "国考",
                year = 2026,
                region = "中央",
                createdAt = "2026-11-20T09:00:00Z",
            ),
        )
        db.mistakeDao().upsert(mistake("m-target", "2026-11-20T09:00:00Z"))
        db.mistakeDao().upsert(mistake("m-other", "2026-11-22T09:00:00Z"))
        db.userExampleDao().insertMistakeIdioms(
            listOf(
                MistakeIdiomEntity("m-target", "idiom-a"),
                MistakeIdiomEntity("m-target", "idiom-b"),
                MistakeIdiomEntity("m-other", "idiom-a"),
            ),
        )
        db.userExampleDao().insertUserExampleIdioms(
            listOf(UserExampleIdiomEntity("ue-shared", "idiom-a")),
        )
        reviews.insertLog(ReviewLogEntity("log-target", "m-target", "对", "2026-11-21T09:00:00Z"))
        reviews.insertLog(ReviewLogEntity("log-other", "m-other", "错", "2026-11-23T09:00:00Z"))
    }

    private fun idiom(id: String) =
        IdiomEntity(
            id = id,
            word = "成语$id",
            pinyin = "cheng yu",
            initials = "cy",
            sense = "测试释义",
            color = "red",
            frequency = 1,
        )

    private fun mistake(
        id: String,
        createdAt: String,
    ) = MistakeEntity(
        id = id,
        userExampleId = "ue-shared",
        imagePath = "images/$id.jpg",
        ocrText = "识别正文$id",
        stem = "快照题干$id",
        optionA = "甲",
        optionB = "乙",
        optionC = "丙",
        optionD = "丁",
        myAnswer = "B",
        reason = "语境误用",
        tagNames = listOf("误用", "搭配"),
        examType = "国考",
        year = 2026,
        region = "中央",
        state = "待复习",
        createdAt = createdAt,
        lastReviewedAt = "2026-11-24T09:00:00Z",
        reviewCount = 3,
    )

    private suspend fun insertRawState(
        idiomId: String,
        status: String,
        favorite: Boolean,
    ) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO idiom_state (idiomId, status, favorite) VALUES (?, ?, ?)",
            arrayOf(idiomId, status, if (favorite) 1 else 0),
        )
    }

    private fun rawState(idiomId: String): Map<String, String?>? =
        db.openHelper.readableDatabase
            .query("SELECT * FROM idiom_state WHERE idiomId = ?", arrayOf(idiomId))
            .use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    buildMap {
                        for (index in 0 until cursor.columnCount) {
                            val value = if (cursor.isNull(index)) null else cursor.getString(index)
                            put(cursor.getColumnName(index), value)
                        }
                    }
                }
            }

    private fun rawRow(
        table: String,
        id: String,
    ): Map<String, String?>? =
        db.openHelper.readableDatabase.query("SELECT * FROM $table WHERE id = ?", arrayOf(id)).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                buildMap {
                    for (index in 0 until cursor.columnCount) {
                        put(cursor.getColumnName(index), if (cursor.isNull(index)) null else cursor.getString(index))
                    }
                }
            }
        }

    private fun rawRows(
        sql: String,
        args: Array<Any?> = emptyArray(),
    ): List<List<String?>> =
        db.openHelper.readableDatabase.query(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        List(cursor.columnCount) { index ->
                            if (cursor.isNull(index)) null else cursor.getString(index)
                        },
                    )
                }
            }
        }

    private fun rawCount(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
