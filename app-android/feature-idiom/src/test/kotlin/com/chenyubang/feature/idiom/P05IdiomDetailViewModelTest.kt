package com.chenyubang.feature.idiom

import com.chenyubang.core.data.IdiomEntity
import com.chenyubang.core.data.IdiomLearningState
import com.chenyubang.core.data.IdiomLearningStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A6/A7 写失败提示与 D1 待复习计数状态：均落在 ViewModel，不依赖页面合成。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class P05IdiomDetailViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun idiom(id: String) =
        IdiomEntity(
            id = id,
            word = "按部就班",
            pinyin = "àn bù jiù bān",
            initials = "abjb",
            sense = "依照一定条理办事",
            color = "贬义",
            frequency = 5,
        )

    private fun model(
        writeStatus: suspend (String, IdiomLearningStatus) -> Unit = { _, _ -> },
        writeFavorite: suspend (String, Boolean) -> Unit = { _, _ -> },
        loadState: suspend (String) -> IdiomLearningState = {
            IdiomLearningState(it, IdiomLearningStatus.UNLEARNED, false)
        },
        loadPendingCount: suspend (String) -> Int = { 0 },
    ) = P05IdiomDetailViewModel(
        loadIdiom = { idiom("idiom-1") },
        loadExamples = { emptyList() },
        countUserExamples = { 0 },
        loadState = loadState,
        writeStatus = writeStatus,
        writeFavorite = writeFavorite,
        loadPendingCount = loadPendingCount,
    )

    @Test
    fun loadFoldsPendingCountIntoState() =
        runBlocking {
            val model = model(loadPendingCount = { 3 })
            model.load("idiom-1")
            assertEquals(3, model.state.value.pendingCount)
            assertFalse(model.state.value.loading)
        }

    @Test
    fun updateStatusWritesAndRefreshesState() =
        runBlocking {
            var written: Pair<String, IdiomLearningStatus>? = null
            val model =
                model(
                    writeStatus = { id, status -> written = id to status },
                    loadState = { IdiomLearningState(it, IdiomLearningStatus.LEARNED, false) },
                )
            model.load("idiom-1")
            model.updateStatus(IdiomLearningStatus.LEARNED)
            assertEquals("idiom-1" to IdiomLearningStatus.LEARNED, written)
            assertEquals(IdiomLearningStatus.LEARNED, model.state.value.state.status)
            assertFalse(model.state.value.updateFailed)
        }

    @Test
    fun writeFailureFlagsUpdateFailedAndKeepsOldState() =
        runBlocking {
            val old = IdiomLearningState("idiom-1", IdiomLearningStatus.UNLEARNED, false)
            val model =
                model(
                    writeStatus = { _, _ -> error("disk io") },
                    writeFavorite = { _, _ -> error("disk io") },
                    loadState = { old },
                )
            model.load("idiom-1")
            model.updateStatus(IdiomLearningStatus.MASTERED)
            assertTrue(model.state.value.updateFailed)
            assertEquals(old, model.state.value.state)
            model.updateFavorite(true)
            assertTrue(model.state.value.updateFailed)
            assertEquals(old, model.state.value.state)
        }

    @Test
    fun successfulWriteClearsPreviousFailureFlag() =
        runBlocking {
            var fail = true
            val model =
                model(
                    writeStatus = { _, _ -> if (fail) error("disk io") },
                    writeFavorite = { _, _ -> if (fail) error("disk io") },
                    loadState = { IdiomLearningState(it, IdiomLearningStatus.LEARNED, false) },
                )
            model.load("idiom-1")
            model.updateStatus(IdiomLearningStatus.LEARNED)
            assertTrue(model.state.value.updateFailed)
            fail = false
            model.updateFavorite(true)
            assertFalse(model.state.value.updateFailed)
        }

    @Test
    fun requestReviewRequeriesAndPassesLatestCount() =
        runBlocking {
            var count = 1
            var started: Int? = null
            val model = model(loadPendingCount = { count })
            model.load("idiom-1")
            count = 2
            model.requestReview { started = it }
            assertEquals(2, started)
            assertEquals(2, model.state.value.pendingCount)
        }

    @Test
    fun requestReviewWithZeroCountDoesNotStart() =
        runBlocking {
            var started: Int? = null
            val model = model(loadPendingCount = { 0 })
            model.load("idiom-1")
            model.requestReview { started = it }
            assertNull(started)
            assertEquals(0, model.state.value.pendingCount)
        }

    @Test
    fun failedRequeryKeepsCountAndDoesNotStart() =
        runBlocking {
            var fail = false
            var started: Int? = null
            val model =
                model(
                    loadPendingCount = { if (fail) error("disk io") else 2 },
                )
            model.load("idiom-1")
            fail = true
            model.requestReview { started = it }
            assertEquals(2, model.state.value.pendingCount)
            assertNull(started)
        }

    @Test
    fun latestRequestReviewCancelsPreviousQuery() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            var started: Int? = null
            val model =
                model(
                    loadPendingCount = {
                        calls++
                        if (calls == 2) gate.await()
                        2
                    },
                )
            model.load("idiom-1")
            model.requestReview { started = it }
            model.requestReview { started = it }
            gate.complete(Unit)
            assertEquals(3, calls)
            assertEquals(2, started)
        }
}
