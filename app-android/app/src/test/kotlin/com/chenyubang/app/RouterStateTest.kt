package com.chenyubang.app

import com.chenyubang.core.data.ExamScopeSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterStateTest {
    @Test
    fun exactRegistryAndOwnership() {
        val keys =
            listOf(
                "onboarding", "tab/recommend", "library", "search", "idiom/{id}", "tab/examples",
                "example/{id}", "tab/mistakes", "mistakes/idiom/{id}", "mistake/{id}", "review/queue",
                "review/summary", "tab/me", "settings/scope", "settings/backup", "settings/pack",
                "capture/camera", "capture/crop", "capture/ocr", "capture/match", "capture/done",
                "sheet/import-mode", "dialog/clear",
            )
        assertEquals(keys, Route.entries.map { it.key })
        assertEquals(23, Route.entries.map { it.key }.toSet().size)
        assertEquals(
            listOf(1, 4, 2, 3, 2, 4, 7),
            RouteOwner.entries.map {
                    owner ->
                Route.entries.count { it.owner == owner }
            },
        )
        assertEquals(RouteOwner.Capture, Route.ImportMode.owner)
        assertEquals(Presentation.Sheet, Route.ImportMode.presentation)
        assertEquals(Presentation.Dialog, Route.Clear.presentation)
    }

    @Test
    fun fourTabsAndFabWhitelist() {
        assertEquals(listOf("成语", "例题", "错题", "我的"), AppTab.entries.map { it.label })
        assertEquals(setOf(Route.Recommend, Route.Examples, Route.Mistakes, Route.Me), RouteRegistry.fabWhitelist)
        AppTab.entries.forEach { assertTrue(RouterState(tab = it).showFab) }
        assertFalse(RouterState().push(Route.Library).showFab)
        assertTrue(RouterState().push(Route.Library).showTabs)
        assertFalse(RouterState().push(Route.Search).showTabs)
        assertFalse(RouterState(tab = AppTab.Me).present(Route.Clear).showFab)
    }

    @Test
    fun idiomExamplePushReturnsOneScreenAtATime() {
        val detail = RouterState().push(Route.Library).push(Route.Idiom, "idiom-id")
        val example = detail.push(Route.Example, "example-id")
        val again = example.push(Route.Idiom, "idiom-id")
        assertEquals(example.stack, again.back().stack)
        assertEquals(detail.stack, again.back().back().stack)
        assertTrue(again.current.instance != detail.current.instance)
        val switched = example.selectTab(AppTab.Example).selectTab(AppTab.Idiom)
        assertEquals(example.stack, switched.stack)
        val reset = switched.selectTab(AppTab.Idiom)
        assertEquals(Route.Recommend, reset.current.route)
        assertEquals(1, reset.rootTopEpoch[AppTab.Idiom])
    }

    @Test
    fun reviewReturnsToItsOwnSourceAndRetryKeepsSummary() {
        val global = RouterState(tab = AppTab.Mistake)
        checkReview(global, null)
        val single = RouterState().push(Route.Library).push(Route.Idiom, "single-id")
        checkReview(single, "single-id")
        assertEquals(single, single.startReview(0))
    }

    private fun checkReview(
        source: RouterState,
        idiomId: String?,
    ) {
        val queue = source.startReview(2)
        assertEquals(idiomId, queue.review?.idiomId)
        assertFalse(queue.showTabs)
        assertEquals(source.stack, queue.back().stack)
        val summary = queue.finishReview(2)
        val retry = summary.retryReview(1)
        assertEquals(summary.stack, retry.back().stack)
        assertEquals(idiomId, retry.review?.idiomId)
        assertEquals(source.stack, retry.back().back().stack)
        assertEquals(source.stack, retry.exitReview().stack)
        val related = summary.push(Route.Idiom, "related-id")
        assertFalse(related.showTabs)
        assertEquals(summary.stack, related.back().stack)
    }

    @Test
    fun captureFiveStepsCancelClearsFlowWithoutSuccess() {
        AppTab.entries.forEach { tab ->
            val source = RouterState(tab = tab)
            var flow = source.startCapture()
            repeat(4) { step ->
                assertEquals(RouteRegistry.captureSteps[step], flow.current.route)
                val cancelled = flow.cancelCapture()
                assertEquals(source.stack, cancelled.stack)
                assertEquals(null, cancelled.captureBaseDepth)
                if (step < 3) flow = flow.nextCapture()
            }
            val done = flow.captureSaved("real-saved-idiom-id")
            assertEquals(Route.Done, done.current.route)
            assertEquals(source.stack, done.cancelCapture().stack)
            assertEquals(Route.Idiom, done.captureDestination(Route.Idiom).current.route)
            assertEquals(Route.Mistakes, done.captureDestination(Route.Mistakes).current.route)
            assertEquals(Route.Camera, done.captureDestination(Route.Camera).current.route)
        }
    }

    @Test
    fun cameraBackClearsCaptureSession() {
        val source = RouterState(tab = AppTab.Example)
        val returned = source.startCapture().back()
        assertEquals(source.stack, returned.stack)
        assertEquals(null, returned.captureBaseDepth)
        assertEquals(null, returned.capturedIdiomId)
        assertTrue(returned.showFab)
    }

    @Test
    fun goToMistakesAlwaysReturnsToRootAndTop() {
        val deep = RouterState(tab = AppTab.Mistake).push(Route.Mistake, "mistake-id")
        val otherTab = deep.selectTab(AppTab.Idiom).push(Route.Idiom, "idiom-id")
        val returned = otherTab.goToMistakes()
        assertEquals(Route.Mistakes, returned.current.route)
        assertEquals(1, returned.stack.size)
        assertEquals(1, returned.rootTopEpoch[AppTab.Mistake])
        val done = otherTab.back().startCapture().nextCapture().nextCapture().nextCapture().captureSaved("saved-id")
        assertEquals(1, done.captureDestination(Route.Mistakes).stack.size)
    }

    @Test
    fun dismissingModalDoesNotChangeStacks() {
        val backup = RouterState(tab = AppTab.Me).push(Route.Backup)
        assertEquals(backup.stacks, backup.present(Route.ImportMode).back().stacks)
        val profile = RouterState(tab = AppTab.Me)
        assertEquals(profile, profile.present(Route.Clear).dismissModal())
    }

    @Test
    fun firstLaunchAllCancelledIsValidAndProvinceDefaultsEmpty() {
        val defaults = ExamScopeSelection()
        assertTrue(defaults.national && defaults.provincial && defaults.institution)
        assertTrue(defaults.provinces.isEmpty())
        assertTrue(defaults.provinceMissing)
        val cancelled = defaults.copy(national = false, provincial = false, institution = false)
        assertTrue(cancelled.isEmpty)
        assertFalse(cancelled.provinceMissing)
        assertEquals("未选考试范围", cancelled.description)
    }
}
