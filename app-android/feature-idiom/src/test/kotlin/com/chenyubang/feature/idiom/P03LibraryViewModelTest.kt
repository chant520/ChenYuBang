package com.chenyubang.feature.idiom

import com.chenyubang.core.data.ExamScopeSelection
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
class P03LibraryViewModelTest {
    private val idiom = LibraryIdiom("i-1", "按部就班", "àn bù jiù bān", "ABJB", 5)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repeatedEntryAndScopeReloadDoNotRecheckButNewEpochDoes() =
        kotlinx.coroutines.runBlocking {
            var reads = 0
            var checks = 0
            val model =
                P03LibraryViewModel(
                    loadIdioms = {
                        reads++
                        listOf(idiom)
                    },
                    refreshTrigger =
                        LibraryRefreshTrigger {
                            checks++
                            PackCheckOutcome.UNCHANGED
                        },
                )

            val initialScope = ExamScopeSelection(provincial = false, institution = false)
            model.enter(initialScope, entryEpoch = 1)
            model.enter(initialScope, entryEpoch = 1)
            model.reload(initialScope)
            yield()

            assertEquals(1, checks)
            assertEquals(1, reads)
            assertFalse(model.state.value.loading)

            model.reload(ExamScopeSelection(national = true, provincial = true))
            yield()
            assertEquals(1, checks)
            assertEquals(2, reads)

            model.enter(ExamScopeSelection(national = true, provincial = true), entryEpoch = 2)
            yield()
            assertEquals(2, checks)
            assertEquals(3, reads)
        }

    @Test
    fun updateFailureKeepsLocalRowsAndLocalRetryDoesNotRequest() =
        kotlinx.coroutines.runBlocking {
            var checks = 0
            val model =
                P03LibraryViewModel(
                    loadIdioms = { listOf(idiom) },
                    refreshTrigger =
                        LibraryRefreshTrigger {
                            checks++
                            PackCheckOutcome.FAILED
                        },
                )

            model.enter(ExamScopeSelection(national = true), entryEpoch = 1)
            yield()

            assertTrue(model.state.value.updateFailed)
            assertEquals(listOf(idiom), model.state.value.page.items)
            model.retry()
            yield()
            assertFalse(model.state.value.loading)
            assertEquals(1, checks)
        }

    @Test
    fun successfulUpdateReloadsLocalRows() =
        kotlinx.coroutines.runBlocking {
            var reads = 0
            val model =
                P03LibraryViewModel(
                    loadIdioms = {
                        reads++
                        listOf(idiom.copy(word = if (reads == 1) "旧词" else "新词"))
                    },
                    refreshTrigger = LibraryRefreshTrigger { PackCheckOutcome.UPDATED },
                )

            model.enter(ExamScopeSelection(national = true), entryEpoch = 1)
            yield()

            assertEquals(2, reads)
            assertEquals("新词", model.state.value.page.items.single().word)
            assertFalse(model.state.value.updateFailed)
        }
}
