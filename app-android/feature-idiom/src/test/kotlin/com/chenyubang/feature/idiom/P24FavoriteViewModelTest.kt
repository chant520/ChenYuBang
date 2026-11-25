package com.chenyubang.feature.idiom

import com.chenyubang.core.data.FavoriteIdiom
import com.chenyubang.core.data.IdiomLearningStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** P24 ViewModel：favoriteIdioms 映射三态 + 缺行不出现 + 读取失败兜底。 */
@OptIn(ExperimentalCoroutinesApi::class)
class P24FavoriteViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun row(
        id: String,
        status: String,
    ) = FavoriteIdiom(id, "成语$id", "cheng yu", "cy", 3, status)

    @Test
    fun loadProjectsStatusToThreeStates() =
        kotlinx.coroutines.runBlocking {
            val model =
                P24FavoriteViewModel(
                    loadFavorite = {
                        listOf(
                            row("a", "未学"),
                            row("b", "学过"),
                            row("c", "已掌握"),
                        )
                    },
                )
            model.load()
            assertEquals(3, model.state.value.items.size)
            assertEquals(
                listOf(
                    IdiomLearningStatus.UNLEARNED,
                    IdiomLearningStatus.LEARNED,
                    IdiomLearningStatus.MASTERED,
                ),
                model.state.value.items.map { it.status },
            )
            assertFalse(model.state.value.loading)
            assertFalse(model.state.value.failed)
        }

    @Test
    fun emptyFavoriteShowsEmptyState() =
        kotlinx.coroutines.runBlocking {
            val model = P24FavoriteViewModel(loadFavorite = { emptyList() })
            model.load()
            assertTrue(model.state.value.isEmpty)
            assertEquals(0, model.state.value.items.size)
        }

    @Test
    fun readFailureKeepsNoRowsAndFlagsFailed() =
        kotlinx.coroutines.runBlocking {
            val model =
                P24FavoriteViewModel(
                    loadFavorite = { error("db down") },
                )
            model.load()
            assertTrue(model.state.value.failed)
            assertFalse(model.state.value.isEmpty)
        }
}
