package com.chenyubang.feature.review

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class P24SelfTestViewModelKeyTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun reenteringWithNewInstanceStartsFreshRoundAndReloadsLibrary() =
        runTest(dispatcher) {
            val owner =
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            val library = (1..12).map { SelfTestCandidate("id-$it", "成语$it", "释义$it") }
            var loadCount = 0
            val factory =
                viewModelFactory {
                    initializer {
                        loadCount++
                        SelfTestViewModel({ library.take(1) to library }, Random(21))
                    }
                }

            val first =
                ViewModelProvider(owner, factory).get(
                    p24SelfTestViewModelKey(11L),
                    SelfTestViewModel::class.java,
                )
            advanceUntilIdle()
            val question = (first.state.value as SelfTestUiState.Question).current
            first.pick(question.targetId)
            first.next()
            assertEquals(2, (first.state.value as SelfTestUiState.Question).questionNumber)

            val second =
                ViewModelProvider(owner, factory).get(
                    p24SelfTestViewModelKey(12L),
                    SelfTestViewModel::class.java,
                )
            advanceUntilIdle()
            val secondState = second.state.value as SelfTestUiState.Question
            assertNotSame(first, second)
            assertEquals(2, loadCount)
            assertEquals(1, secondState.questionNumber)
            assertNull(secondState.answer)
            assertEquals(2, (first.state.value as SelfTestUiState.Question).questionNumber)
            owner.viewModelStore.clear()
        }

    @Test
    fun viewModelKeyIsStableForSameInstanceAndDistinctForNewEntry() {
        assertEquals(p24SelfTestViewModelKey(11L), p24SelfTestViewModelKey(11L))
        assertNotEquals(p24SelfTestViewModelKey(11L), p24SelfTestViewModelKey(12L))
    }
}
