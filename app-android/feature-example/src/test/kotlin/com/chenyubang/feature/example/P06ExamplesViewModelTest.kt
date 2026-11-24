package com.chenyubang.feature.example

import com.chenyubang.core.data.ExampleEntity
import com.chenyubang.core.data.ExampleListItem
import com.chenyubang.core.data.PackCheckOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class P06ExamplesViewModelTest {
    private val row =
        ExampleListItem(
            example =
                ExampleEntity(
                    id = "e-1",
                    stem = "例题题干",
                    optionA = "A",
                    optionB = "B",
                    optionC = "C",
                    optionD = "D",
                    answer = "A",
                    explain = "解析",
                    examType = "国考",
                    year = 2026,
                    region = "中央",
                    questionType = "言语理解",
                ),
            idiomCount = 2,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repeatedEnterAndLocalRetryDoNotRecheck() =
        kotlinx.coroutines.runBlocking {
            var checks = 0
            val model =
                P06ExamplesViewModel(
                    loadExamples = { listOf(row) },
                    refreshTrigger =
                        ExampleRefreshTrigger {
                            checks++
                            PackCheckOutcome.UNCHANGED
                        },
                )

            model.enter()
            model.enter()
            yield()
            model.retry()
            yield()

            assertEquals(1, checks)
            assertEquals(listOf(row), model.state.value.items)
            assertFalse(model.state.value.loading)
            assertFalse(model.state.value.updateFailed)
        }

    @Test
    fun updateFailureKeepsLocalRowsAndShowsRecoverableWarning() =
        kotlinx.coroutines.runBlocking {
            val model =
                P06ExamplesViewModel(
                    loadExamples = { listOf(row) },
                    refreshTrigger = ExampleRefreshTrigger { PackCheckOutcome.FAILED },
                )

            model.enter()
            yield()

            assertTrue(model.state.value.updateFailed)
            assertFalse(model.state.value.failed)
            assertEquals(listOf(row), model.state.value.items)
        }

    @Test
    fun successfulUpdateReloadsLocalRows() =
        kotlinx.coroutines.runBlocking {
            var reads = 0
            val model =
                P06ExamplesViewModel(
                    loadExamples = {
                        reads++
                        listOf(row.copy(idiomCount = reads))
                    },
                    refreshTrigger = ExampleRefreshTrigger { PackCheckOutcome.UPDATED },
                )

            model.enter()
            yield()

            assertEquals(2, reads)
            assertEquals(2, model.state.value.items.single().idiomCount)
        }
}
