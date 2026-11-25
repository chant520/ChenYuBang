package com.chenyubang.feature.review

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class SelfTestViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val library = (1..12).map { SelfTestCandidate("id-$it", "成语$it", "释义$it") }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadingEmptyAndInsufficientStates() =
        runTest(dispatcher) {
            var data = emptyList<SelfTestCandidate>() to library
            val empty = SelfTestViewModel({ data }, Random(1))
            assertTrue(empty.state.value is SelfTestUiState.Loading)
            advanceUntilIdle()
            assertEquals(SelfTestUiState.EmptyScope, empty.state.value)

            data = library.take(1) to library.take(3)
            val insufficient = SelfTestViewModel({ data }, Random(1))
            advanceUntilIdle()
            assertEquals(SelfTestUiState.InsufficientCandidates, insufficient.state.value)
        }

    @Test
    fun nextIsGuardedUntilPickAndSummaryCountsCorrectAndWrong() =
        runTest(dispatcher) {
            val model = SelfTestViewModel({ library.take(2) to library }, Random(5))
            advanceUntilIdle()
            val first = model.state.value as SelfTestUiState.Question
            model.next()
            assertEquals(first, model.state.value)

            var correct = 0
            repeat(10) { index ->
                val question = model.state.value as SelfTestUiState.Question
                assertEquals(index + 1, question.questionNumber)
                val pickCorrect = index % 2 == 0
                val option =
                    if (pickCorrect) {
                        question.current.targetId
                    } else {
                        question.current.options.first {
                            it.idiomId != question.current.targetId
                        }.idiomId
                    }
                model.pick(option)
                if (pickCorrect) correct++
                model.next()
            }
            val summary = model.state.value as SelfTestUiState.Summary
            assertEquals(10, summary.result.questionCount)
            assertEquals(correct, summary.result.correctCount)
            assertEquals(10 - correct, summary.result.wrongCount)
        }

    @Test
    fun retryCreatesTransientRoundAndLoadFailureCanRecover() =
        runTest(dispatcher) {
            var fail = true
            val model =
                SelfTestViewModel(
                    loadData = {
                        if (fail) error("read failed")
                        library.take(1) to library
                    },
                    random = Random(9),
                )
            advanceUntilIdle()
            assertTrue(model.state.value is SelfTestUiState.Error)
            fail = false
            model.retry()
            advanceUntilIdle()
            assertTrue(model.state.value is SelfTestUiState.Question)
        }
}
