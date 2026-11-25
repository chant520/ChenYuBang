package com.chenyubang.app

import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.feature.capture.CropFrame
import com.chenyubang.feature.capture.IdiomRef
import com.chenyubang.feature.capture.OcrDraft
import com.chenyubang.feature.mistake.MistakeViewMode
import com.chenyubang.feature.review.ReviewSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterStateTest {
    @Test
    fun exactRegistryAndOwnership() {
        val keys =
            listOf(
                "onboarding", "tab/recommend", "library", "search", "idiom/{id}", "favorites", "tab/examples",
                "example/{id}", "tab/mistakes", "mistakes/idiom/{id}", "mistake/{id}", "review/queue",
                "review/summary", "tab/me", "settings/scope", "settings/backup", "settings/pack",
                "capture/camera", "capture/crop", "capture/ocr", "capture/match", "capture/done",
                "sheet/import-mode", "dialog/clear",
            )
        assertEquals(keys, Route.entries.map { it.key })
        assertEquals(24, Route.entries.map { it.key }.toSet().size)
        assertEquals(
            listOf(1, 5, 2, 3, 2, 4, 7),
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
    fun favoritesPushesFromMeAndBackReturnsToProfile() {
        assertEquals("favorites", Route.Favorites.key)
        assertEquals("我的收藏", Route.Favorites.title)
        assertEquals(RouteOwner.Idiom, Route.Favorites.owner)
        assertEquals(Presentation.Push, Route.Favorites.presentation)
        val profile = RouterState(tab = AppTab.Me)
        val favorites = profile.push(Route.Favorites)
        assertEquals(Route.Favorites, favorites.current.route)
        assertEquals(profile.stack, favorites.back().stack)
        assertTrue(favorites.showTabs)
        assertFalse(favorites.showFab)
        // 「去成语库」可从收藏二级页切到成语标签并 push 成语库，返回仍按各标签独立栈。
        val library = favorites.selectTab(AppTab.Idiom).push(Route.Library)
        assertEquals(Route.Library, library.current.route)
        assertEquals(profile.stack, favorites.back().stack)
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
        val lastRound = ReviewSummary(questionCount = 2, answerCount = 2, correctCount = 1, idioms = emptyList())
        val summary = queue.finishReview(2, lastRound)
        val retry = summary.retryReview(1)
        assertEquals(lastRound, retry.reviewSummary)
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
            val savedIdioms =
                listOf(
                    IdiomRef("first-idiom-id", "按部就班"),
                    IdiomRef("second-idiom-id", "别出心裁"),
                )
            val done = flow.captureSaved("saved-ue-id", savedIdioms)
            assertEquals(Route.Done, done.current.route)
            assertEquals(source.stack, done.cancelCapture().stack)
            assertEquals(savedIdioms, done.capturedIdioms)
            val detail = done.captureDestination(Route.Idiom)
            assertEquals(Route.Idiom, detail.current.route)
            assertEquals("first-idiom-id", detail.current.id)
            assertEquals(Route.Mistakes, done.captureDestination(Route.Mistakes).current.route)
            assertEquals(Route.Camera, done.captureDestination(Route.Camera).current.route)
            assertTrue(done.captureDestination(Route.Camera).capturedIdioms.isEmpty())
        }
    }

    @Test
    fun captureAndEditBumpContentRevisionForSourceRefresh() {
        val match =
            RouterState(tab = AppTab.Mistake)
                .startCapture()
                .nextCapture()
                .nextCapture()
                .attachOcrText(OcrDraft(stem = "题干", options = listOf("甲")))
        val done = match.captureSaved("saved-id", listOf(IdiomRef("idiom-id", "按部就班")))
        assertEquals(1, done.contentRevision)
        assertEquals(2, done.bumpContentRevision().contentRevision)
    }

    @Test
    fun doneDisablesSystemBackWhileOtherRoutesKeepIt() {
        val base = RouterState(tab = AppTab.Mistake)
        val match =
            base.startCapture().nextCapture().nextCapture()
                .attachOcrText(OcrDraft(stem = "题干", options = listOf("甲")))
        assertEquals(Route.Match, match.current.route)
        assertTrue(systemBackEnabled(match))

        val done = match.captureSaved("saved-id", listOf(IdiomRef("idiom-id", "按部就班")))
        assertEquals(Route.Done, done.current.route)
        assertFalse(systemBackEnabled(done))
        // P21 只能通过三个去处离开；系统返回键在 Done 不触发 back()。
        assertEquals(Route.Idiom, done.captureDestination(Route.Idiom).current.route)
        assertEquals(Route.Mistakes, done.captureDestination(Route.Mistakes).current.route)
        assertEquals(Route.Camera, done.captureDestination(Route.Camera).current.route)

        // 其它路由返回能力不受影响。
        assertFalse(systemBackEnabled(base))
        assertTrue(systemBackEnabled(base.push(Route.Library)))
        assertTrue(systemBackEnabled(RouterState(tab = AppTab.Me).present(Route.Clear)))
    }

    /**
     * 同照片连录两次（P21 走「再拍一张」回 P17，拿同一张本机图与同一份草稿）必须落在新的 Match 实例上。
     * 本例只钉 instance；`p20FormViewModelKey` 随 instance 变化由 feature-capture 的
     * `P20FormViewModelKeyTest` 钉住，两段合起来即「二次录入不复用 P20 表单状态」。
     */
    @Test
    fun samePhotoRecordedTwiceLandsOnFreshMatchInstance() {
        val draft =
            OcrDraft(stem = "新厂长按部就班，提出了一整套创新方案", options = listOf("按部就班", "墨守成规"))

        fun shoot(atCamera: RouterState): RouterState =
            atCamera
                .attachCaptureImage("/tmp/same-photo.jpg")
                .nextCapture()
                .attachCropImage("/tmp/same-crop.jpg", frame())
                .attachOcrText(draft)

        val first = shoot(RouterState(tab = AppTab.Idiom).startCapture())
        assertEquals(Route.Match, first.current.route)

        val again =
            first
                .captureSaved("ue-same-photo", listOf(IdiomRef("idiom-id", "按部就班")))
                .captureDestination(Route.Camera)
        val second = shoot(again)

        assertEquals(Route.Match, second.current.route)
        // 照片路径、裁切图与草稿文字逐条相同：两次 P20 的差别只能来自 capture 实例。
        assertEquals(first.captureImagePath, second.captureImagePath)
        assertEquals(first.captureCropPath, second.captureCropPath)
        assertEquals(first.captureDraft, second.captureDraft)
        assertNotEquals(first.current.instance, second.current.instance)
    }

    @Test
    fun cameraBackClearsCaptureSession() {
        val source = RouterState(tab = AppTab.Example)
        val returned = source.startCapture().back()
        assertEquals(source.stack, returned.stack)
        assertEquals(null, returned.captureBaseDepth)
        assertTrue(returned.capturedIdioms.isEmpty())
        assertTrue(returned.showFab)
    }

    @Test
    fun cropBackReturnsToCameraAndClearsImages() {
        val flow =
            RouterState(tab = AppTab.Example).startCapture()
                .attachCaptureImage("/tmp/capture-a.jpg").nextCapture()
        assertEquals(Route.Crop, flow.current.route)
        val cropped = flow.attachCropImage("/tmp/crop-b.jpg", frame())
        assertEquals(Route.Ocr, cropped.current.route)
        assertEquals("/tmp/capture-a.jpg", cropped.captureImagePath)
        assertEquals("/tmp/crop-b.jpg", cropped.captureCropPath)
        assertEquals(frame(), cropped.captureCrop)
        val back = flow.back()
        assertEquals(Route.Camera, back.current.route)
        assertEquals(null, back.captureImagePath)
        assertEquals(null, back.captureCropPath)
        assertTrue(back.captureBaseDepth != null)
    }

    @Test
    fun ocrBackKeepsCropAndNewCropClearsDraft() {
        val crop = frame()
        val ocr = cropFlow().attachCropImage("/tmp/crop-b.jpg", crop)
        val backToCrop = ocr.back()
        assertEquals(Route.Crop, backToCrop.current.route)
        assertEquals(crop, backToCrop.captureCrop)
        assertEquals("/tmp/crop-b.jpg", backToCrop.captureCropPath)

        val draft = OcrDraft(stem = "题干", options = listOf("甲", "乙", "丙", "丁"))
        val match = ocr.attachOcrText(draft)
        assertEquals(Route.Match, match.current.route)
        val backToOcr = match.back()
        assertEquals(Route.Ocr, backToOcr.current.route)
        assertEquals(draft, backToOcr.captureDraft)
        val recropped = backToOcr.back().attachCropImage("/tmp/crop-c.jpg", crop)
        assertEquals(null, recropped.captureDraft)
    }

    @Test
    fun retakeFromOcrRestartsAtCameraWithNothingKept() {
        val ocr = cropFlow().attachCropImage("/tmp/crop-b.jpg", frame())
        val retake = ocr.retakeFromOcr()
        assertEquals(Route.Camera, retake.current.route)
        assertEquals(2, retake.stack.size)
        assertEquals(1, retake.captureBaseDepth)
        assertEquals(null, retake.captureImagePath)
        assertEquals(null, retake.captureCropPath)
        assertEquals(null, retake.captureCrop)
        assertEquals(null, retake.captureDraft)
    }

    private fun cropFlow(): RouterState =
        RouterState(tab = AppTab.Example).startCapture()
            .attachCaptureImage("/tmp/capture-a.jpg").nextCapture()

    private fun frame(): CropFrame =
        CropFrame(left = 0.05f, top = 0.1f, right = 0.95f, bottom = 0.8f, quarterTurns = 2, fineDegrees = -3f)

    @Test
    fun goToMistakesAlwaysReturnsToRootAndTop() {
        val deep = RouterState(tab = AppTab.Mistake).push(Route.Mistake, "mistake-id")
        val otherTab = deep.selectTab(AppTab.Idiom).push(Route.Idiom, "idiom-id")
        val returned = otherTab.goToMistakes()
        assertEquals(Route.Mistakes, returned.current.route)
        assertEquals(1, returned.stack.size)
        assertEquals(1, returned.rootTopEpoch[AppTab.Mistake])
        val flow = otherTab.back().startCapture().nextCapture().nextCapture().nextCapture()
        val done = flow.captureSaved("saved-id", listOf(IdiomRef("idiom-id", "按部就班")))
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
    fun firstLaunchDefaultsShowPlainThreePartScope() {
        val defaults = ExamScopeSelection()
        assertTrue(defaults.national && defaults.provincial && defaults.institution)
        assertTrue(defaults.provinces.isEmpty())
        assertEquals("国考 · 省考 · 事业编", defaults.description)
        val cancelled = defaults.copy(national = false, provincial = false, institution = false)
        assertTrue(cancelled.isEmpty)
        assertEquals("未选考试范围", cancelled.description)
    }

    @Test
    fun mistakesViewDefaultsToIdiomGrouping() {
        assertEquals(MistakeViewMode.ByIdiom, RouterState(tab = AppTab.Mistake).mistakesView)
    }

    @Test
    fun backFromIdiomMistakesStopsAtIdiomGroupingView() {
        val grouped = RouterState(tab = AppTab.Mistake)
        val pushed = grouped.push(Route.IdiomMistakes, "idiom-id")
        assertEquals(Route.IdiomMistakes, pushed.current.route)
        val returned = pushed.back()
        assertEquals(Route.Mistakes, returned.current.route)
        // 返回停在「按成语分组」：视图存在 RouterState，页面内部不持有。
        assertEquals(MistakeViewMode.ByIdiom, returned.mistakesView)
        assertEquals(grouped.stack, returned.stack)
    }

    @Test
    fun mistakesViewSurvivesTabSwitchAndCaptureReturn() {
        val time = RouterState(tab = AppTab.Mistake).copy(mistakesView = MistakeViewMode.ByTime)
        assertEquals(MistakeViewMode.ByTime, time.goToMistakes().mistakesView)
        assertEquals(MistakeViewMode.ByTime, time.selectTab(AppTab.Idiom).goToMistakes().mistakesView)
        val done =
            time.startCapture().nextCapture().nextCapture().nextCapture()
                .captureSaved("saved-id", listOf(IdiomRef("idiom-id", "按部就班")))
        assertEquals(MistakeViewMode.ByTime, done.captureDestination(Route.Mistakes).mistakesView)
    }
}
