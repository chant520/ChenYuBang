package com.chenyubang.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ExampleDao 三查询单测（工作包 1.5 / 1.6，规范 §4.1）：
 * byId 缺行返回 null、listByExample 一题多成语、listByIdiom 多对多（一个成语关联多条真题、一条真题关联多个成语）。
 * JVM + Robolectric 内存 Room，与 IdiomDaoFtsTest 同基建。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ExampleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.exampleDao()
        runBlocking {
            val examples = sampleExamples()
            examples.forEach { example ->
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO example " +
                        "(id, stem, optionA, optionB, optionC, optionD, answer, explain, examType, year, " +
                        "region, questionType, imagePath) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        example.id, example.stem, example.optionA, example.optionB, example.optionC,
                        example.optionD, example.answer, example.explain, example.examType, example.year,
                        example.region, example.questionType, example.imagePath,
                    ),
                )
            }
            exampleLinks().forEach { link ->
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO example_idiom (exampleId, idiomId) VALUES (?, ?)",
                    arrayOf(link.exampleId, link.idiomId),
                )
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun byIdReturnsRow() =
        runBlocking {
            val row = dao.byId(ID_EX_A)
            assertEquals("2023", row?.year.toString())
            assertEquals("国考", row?.examType)
            assertEquals("言语理解", row?.questionType)
        }

    @Test
    fun byIdMissingReturnsNull() =
        runBlocking {
            assertNull(dao.byId("missing-0000-0000-0000-000000000000"))
        }

    @Test
    fun listByExampleReturnsAllLinkedIdioms() =
        runBlocking {
            val links = dao.listByExample(ID_EX_A)
            assertEquals(listOf(ID_IDIOM_ANBU, ID_IDIOM_MOSHOU), links.map { it.idiomId })
        }

    @Test
    fun listByExampleEmptyWhenUnlinked() =
        runBlocking {
            assertEquals(emptyList<ExampleIdiomEntity>(), dao.listByExample(ID_EX_UNLINKED))
        }

    @Test
    fun listByIdiomReturnsAllLinkedExamplesNewestFirst() =
        runBlocking {
            // 墨守成规关联 exA(2023)、exC(2020)，倒序应为 exA、exC
            val rows = dao.listByIdiom(ID_IDIOM_MOSHOU)
            assertEquals(listOf(ID_EX_A, ID_EX_C), rows.map { it.id })
        }

    @Test
    fun listByIdiomEmptyWhenNoLinkedExample() =
        runBlocking {
            assertEquals(emptyList<ExampleEntity>(), dao.listByIdiom(ID_IDIOM_NONE))
        }

    @Test
    fun manyToManyRoundTrip() =
        runBlocking {
            // 墨守成规同时关联 exA(2023, 国考) 与 exC(2020, 省考)，两个成语都在各自成语下可查
            assertEquals(listOf(ID_EX_A, ID_EX_C), dao.listByIdiom(ID_IDIOM_MOSHOU).map { it.id })
            val exC = dao.byId(ID_EX_C)
            assertEquals("省考", exC?.examType)
            val exCLinks = dao.listByExample(ID_EX_C).map { it.idiomId }
            assertEquals(listOf(ID_IDIOM_BIECHU, ID_IDIOM_MOSHOU), exCLinks)
        }

    private fun sampleExamples(): List<ExampleEntity> =
        listOf(
            example(ID_EX_A, year = 2023, examType = "国考", region = "中央", questionType = "言语理解"),
            example(ID_EX_B, year = 2022, examType = "省考", region = "湖北", questionType = "逻辑填空"),
            example(ID_EX_C, year = 2020, examType = "省考", region = "贵州", questionType = "逻辑填空"),
            example(ID_EX_UNLINKED, year = 2021, examType = "国考", region = "中央", questionType = "言语理解"),
        )

    private fun example(
        id: String,
        year: Int,
        examType: String,
        region: String,
        questionType: String,
    ) = ExampleEntity(
        id = id,
        stem = "题干：空格处应填？",
        optionA = "按部就班",
        optionB = "别出心裁",
        optionC = "墨守成规",
        optionD = "循序渐进",
        answer = "A",
        explain = "一句解析：语境强调按次序办，用 A 最贴切。",
        examType = examType,
        year = year,
        region = region,
        questionType = questionType,
    )

    private fun exampleLinks(): List<ExampleIdiomEntity> =
        listOf(
            ExampleIdiomEntity(ID_EX_A, ID_IDIOM_ANBU),
            ExampleIdiomEntity(ID_EX_A, ID_IDIOM_MOSHOU),
            ExampleIdiomEntity(ID_EX_B, ID_IDIOM_BIECHU),
            ExampleIdiomEntity(ID_EX_C, ID_IDIOM_MOSHOU),
            ExampleIdiomEntity(ID_EX_C, ID_IDIOM_BIECHU),
        )

    private companion object {
        const val ID_EX_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val ID_EX_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        const val ID_EX_C = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        const val ID_EX_UNLINKED = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
        const val ID_IDIOM_ANBU = "11111111-1111-4111-8111-111111111111"
        const val ID_IDIOM_BIECHU = "22222222-2222-4222-8222-222222222222"
        const val ID_IDIOM_MOSHOU = "33333333-3333-4333-8333-333333333333"
        const val ID_IDIOM_NONE = "99999999-9999-4999-8999-999999999999"
    }
}
