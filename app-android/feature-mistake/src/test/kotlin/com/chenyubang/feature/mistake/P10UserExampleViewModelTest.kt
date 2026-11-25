package com.chenyubang.feature.mistake

import com.chenyubang.core.data.MistakeReviewState
import com.chenyubang.core.data.MistakeStateUpdateResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** C9：精确 mistake.id 写入、写入门控、失败提示与成功后重载/通知。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class P10UserExampleViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun detail(state: MistakeReviewState = MistakeReviewState.PENDING) =
        P10UserExampleDetail(
            id = "ue-1",
            stem = "题干",
            options = listOf("甲", "乙", "丙", "丁"),
            myAnswer = "B",
            isMistake = true,
            examType = null,
            year = null,
            reason = null,
            tagNames = emptyList(),
            idioms = listOf(P10IdiomRef("idiom-1", "按部就班")),
            imagePath = null,
            mistakeId = "mistake-latest",
            reviewState = state,
        )

    @Test
    fun successfulChangeWritesLatestMistakeReloadsAndNotifies() =
        runBlocking {
            var current = detail()
            val writes = mutableListOf<Pair<String, MistakeReviewState>>()
            var loads = 0
            var notifications = 0
            val model =
                P10UserExampleViewModel(
                    loadDetail = {
                        loads++
                        current
                    },
                    isImageReadable = { false },
                    setReviewState = { id, state ->
                        writes += id to state
                        current = detail(state)
                        MistakeStateUpdateResult.UPDATED
                    },
                    onDataChanged = { notifications++ },
                )
            model.load("ue-1")
            model.changeReviewState(MistakeReviewState.MASTERED)

            assertEquals(listOf("mistake-latest" to MistakeReviewState.MASTERED), writes)
            assertEquals(2, loads)
            assertEquals(1, notifications)
            assertEquals(MistakeReviewState.MASTERED, model.state.value.detail?.reviewState)
            assertFalse(model.state.value.loading)
            assertFalse(model.state.value.updatingReviewState)
            assertTrue(model.state.value.isMistake)
        }

    @Test
    fun consecutiveSuccessfulChangesReloadAndNotifyEachTime() =
        runBlocking {
            var current = detail()
            val writes = mutableListOf<MistakeReviewState>()
            var loads = 0
            var notifications = 0
            val model =
                P10UserExampleViewModel(
                    loadDetail = {
                        loads++
                        current
                    },
                    isImageReadable = { false },
                    setReviewState = { _, state ->
                        writes += state
                        current = detail(state)
                        MistakeStateUpdateResult.UPDATED
                    },
                    onDataChanged = { notifications++ },
                )
            model.load("ue-1")
            model.changeReviewState(MistakeReviewState.MASTERED)
            model.changeReviewState(MistakeReviewState.PENDING)

            assertEquals(listOf(MistakeReviewState.MASTERED, MistakeReviewState.PENDING), writes)
            assertEquals(3, loads)
            assertEquals(2, notifications)
            assertEquals(MistakeReviewState.PENDING, model.state.value.detail?.reviewState)
        }

    @Test
    fun notFoundShowsExplicitErrorWithoutReloadOrNotification() =
        runBlocking {
            var loads = 0
            var notifications = 0
            val model =
                P10UserExampleViewModel(
                    loadDetail = {
                        loads++
                        detail()
                    },
                    isImageReadable = { false },
                    setReviewState = { _, _ -> MistakeStateUpdateResult.NOT_FOUND },
                    onDataChanged = { notifications++ },
                )
            model.load("ue-1")
            model.changeReviewState(MistakeReviewState.MASTERED)

            assertEquals(P10EditTransitions.MISTAKE_NOT_FOUND, model.state.value.edit.errorMessage)
            assertEquals(MistakeReviewState.PENDING, model.state.value.detail?.reviewState)
            assertEquals(1, loads)
            assertEquals(0, notifications)
            assertFalse(model.state.value.updatingReviewState)
        }

    @Test
    fun writeExceptionShowsErrorAndAllowsRetry() =
        runBlocking {
            var fail = true
            var writes = 0
            val model =
                P10UserExampleViewModel(
                    loadDetail = { detail() },
                    isImageReadable = { false },
                    setReviewState = { _, _ ->
                        writes++
                        if (fail) error("disk failed")
                        MistakeStateUpdateResult.UPDATED
                    },
                )
            model.load("ue-1")
            model.changeReviewState(MistakeReviewState.MASTERED)
            assertEquals(P10EditTransitions.REVIEW_STATE_FAILED, model.state.value.edit.errorMessage)
            assertFalse(model.state.value.updatingReviewState)

            fail = false
            model.changeReviewState(MistakeReviewState.MASTERED)
            assertEquals(2, writes)
            assertFalse(model.state.value.loading)
            assertTrue(model.state.value.canChangeReviewState)
        }

    @Test
    fun duplicateAndConflictingClicksDuringStateWriteAreIgnored() =
        runBlocking {
            val result = CompletableDeferred<MistakeStateUpdateResult>()
            var writes = 0
            val model =
                P10UserExampleViewModel(
                    loadDetail = { detail() },
                    isImageReadable = { false },
                    setReviewState = { _, _ ->
                        writes++
                        result.await()
                    },
                )
            model.load("ue-1")
            model.changeReviewState(MistakeReviewState.MASTERED)
            model.changeReviewState(MistakeReviewState.MASTERED)
            model.changeReviewState(MistakeReviewState.PENDING)
            model.startEdit()
            model.save()
            assertTrue(model.handleBack())

            assertTrue(model.state.value.updatingReviewState)
            assertFalse(model.state.value.canEdit)
            assertFalse(model.state.value.edit.editing)
            assertEquals(1, writes)
            result.complete(MistakeStateUpdateResult.UPDATED)
            assertFalse(model.state.value.loading)
        }

    @Test
    fun editSaveBlocksStateChangeCancelAndDraftMutation() =
        runBlocking {
            val saved = CompletableDeferred<Unit>()
            var saves = 0
            var stateWrites = 0
            val model =
                P10UserExampleViewModel(
                    loadDetail = { detail() },
                    isImageReadable = { false },
                    saveEdit = {
                        saves++
                        saved.await()
                    },
                    setReviewState = { _, _ ->
                        stateWrites++
                        MistakeStateUpdateResult.UPDATED
                    },
                )
            model.load("ue-1")
            model.startEdit()
            model.changeReviewState(MistakeReviewState.MASTERED)
            model.save()
            model.save()
            model.cancelEdit()
            model.changeDraft { it.copy(stem = "不应写入") }

            assertEquals(1, saves)
            assertEquals(0, stateWrites)
            assertTrue(model.state.value.edit.saving)
            assertTrue(model.state.value.edit.editing)
            assertEquals("题干", model.state.value.edit.draft?.stem)
            assertTrue(model.handleBack())
            saved.complete(Unit)
            assertFalse(model.state.value.edit.editing)
        }

    @Test
    fun deleteInFlightBlocksDuplicateConfirmationAndDismiss() =
        runBlocking {
            val deleted = CompletableDeferred<Unit>()
            var deletes = 0
            var exits = 0
            val model =
                P10UserExampleViewModel(
                    loadDetail = { detail() },
                    isImageReadable = { false },
                    deleteExample = { _, _ ->
                        deletes++
                        deleted.await()
                    },
                    onExit = { exits++ },
                )
            model.load("ue-1")
            model.requestDelete(P10DeleteTarget.Marked)
            model.confirmDelete()
            model.confirmDelete()
            model.dismissDelete()
            assertTrue(model.handleBack())

            assertEquals(1, deletes)
            assertTrue(model.state.value.edit.deleting)
            assertFalse(model.state.value.canEdit)
            deleted.complete(Unit)
            assertEquals(1, exits)
        }

    @Test
    fun sameStateDoesNotWrite() =
        runBlocking {
            var writes = 0
            val model =
                P10UserExampleViewModel(
                    loadDetail = { detail() },
                    isImageReadable = { false },
                    setReviewState = { _, _ ->
                        writes++
                        MistakeStateUpdateResult.UPDATED
                    },
                )
            model.load("ue-1")
            model.changeReviewState(MistakeReviewState.PENDING)
            assertEquals(0, writes)
        }

    @Test
    fun unmarkedDetailHasNoReviewStateWrite() =
        runBlocking {
            var writes = 0
            val model =
                P10UserExampleViewModel(
                    loadDetail = { detail().copy(isMistake = false, mistakeId = null, reviewState = null) },
                    isImageReadable = { false },
                    setReviewState = { _, _ ->
                        writes++
                        MistakeStateUpdateResult.UPDATED
                    },
                )
            model.load("ue-1")
            model.changeReviewState(MistakeReviewState.MASTERED)
            assertEquals(0, writes)
        }
}
