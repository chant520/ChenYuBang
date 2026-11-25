package com.chenyubang.feature.me

import com.chenyubang.core.data.MistakeOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** P13 E2 ViewModel：五项取数口径 + 全空为 0 + 读失败兜底。 */
@OptIn(ExperimentalCoroutinesApi::class)
class P13OverviewTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun fiveStatsMapToTheirOwnSources() =
        kotlinx.coroutines.runBlocking {
            val model =
                P13OverviewViewModel(
                    loadLearned = { 18 },
                    loadMastered = { 6 },
                    loadFavorite = { 4 },
                    loadMistakeOverview = { MistakeOverview(total = 12, pending = 7) },
                )
            model.refresh()
            assertEquals(P13OverviewStats(18, 4, 12, 7, 6), model.state.value)
        }

    @Test
    fun learnedDiffersFromMasteredAndMistakeMasteredIsIgnored() =
        kotlinx.coroutines.runBlocking {
            // 已学=学过+已掌握；已掌握只取 idiom_state，错题 C9 的掌握不计入任何一项。
            val model =
                P13OverviewViewModel(
                    loadLearned = { 9 },
                    loadMastered = { 2 },
                    loadFavorite = { 0 },
                    loadMistakeOverview = { MistakeOverview(total = 30, pending = 5) },
                )
            model.refresh()
            assertEquals(9, model.state.value.learned)
            assertEquals(2, model.state.value.mastered)
            assertEquals(30, model.state.value.mistakes)
            assertEquals(5, model.state.value.pending)
        }

    @Test
    fun allEmptyShowsZeros() =
        kotlinx.coroutines.runBlocking {
            val model =
                P13OverviewViewModel(
                    loadLearned = { 0 },
                    loadMastered = { 0 },
                    loadFavorite = { 0 },
                    loadMistakeOverview = { MistakeOverview(0, 0) },
                )
            model.refresh()
            assertEquals(P13OverviewStats(), model.state.value)
        }

    @Test
    fun readFailureFallsBackToZeros() =
        kotlinx.coroutines.runBlocking {
            val model =
                P13OverviewViewModel(
                    loadLearned = { 5 },
                    loadMastered = { error("db down") },
                    loadFavorite = { 3 },
                    loadMistakeOverview = { MistakeOverview(2, 1) },
                )
            model.refresh()
            assertEquals(P13OverviewStats(), model.state.value)
        }
}
