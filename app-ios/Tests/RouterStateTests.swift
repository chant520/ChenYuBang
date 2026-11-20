import Foundation
import XCTest
@testable import ChenYuBang

final class RouterStateTests: XCTestCase {
    func testRegistryKeepsAll23KeysAndFourRoots() {
        XCTAssertEqual(AppRoute.allCases.map(\.rawValue), [
            "onboarding", "tab/recommend", "library", "search", "idiom/{id}", "tab/examples",
            "example/{id}", "tab/mistakes", "mistakes/idiom/{id}", "mistake/{id}",
            "review/queue", "review/summary", "tab/me", "settings/scope", "settings/backup",
            "settings/pack", "capture/camera", "capture/crop", "capture/ocr", "capture/match",
            "capture/done", "sheet/import-mode", "dialog/clear"
        ])
        XCTAssertEqual(AppTab.allCases.map(\.label), ["成语", "例题", "错题", "我的"])
        XCTAssertEqual(AppRoute.profile.owner, .profile)
        XCTAssertEqual(RouteRegistry.fabRoutes, Set(AppTab.allCases.map(\.root)))
    }

    func testAllPushRoutesAcceptRealReferencesButRejectMissingIDs() {
        let router = RouterState()
        let details: Set<AppRoute> = [.idiomDetail, .exampleDetail, .idiomMistakes, .mistakeDetail]
        for route in AppRoute.allCases where route.presentation == .push {
            if details.contains(route) {
                XCTAssertFalse(router.push(route))
                XCTAssertFalse(router.push(route, recordID: "  "))
            }
            XCTAssertTrue(router.push(route, recordID: "test-fixture-reference"))
            XCTAssertEqual(router.currentEntry.route, route)
            router.back()
            XCTAssertEqual(router.currentEntry.route, .recommend)
        }
        for route in AppRoute.allCases where route.presentation != .push {
            XCTAssertFalse(router.push(route))
        }
    }

    func testRepeatedP05P07PushCreatesDistinctEntriesAndBackOneLayer() {
        let router = RouterState()
        XCTAssertTrue(router.push(.idiomDetail, recordID: "idiom-fixture"))
        let first = router.currentEntry.id
        XCTAssertTrue(router.push(.exampleDetail, recordID: "example-fixture"))
        XCTAssertTrue(router.push(.idiomDetail, recordID: "idiom-fixture"))
        XCTAssertNotEqual(first, router.currentEntry.id)
        XCTAssertEqual(router.entries(for: .idiom).count, 4)
        router.back()
        XCTAssertEqual(router.currentEntry.route, .exampleDetail)
        router.back()
        XCTAssertEqual(router.currentEntry.id, first)
    }

    func testTabSwitchPreservesStacksFiltersAndScrollAndReselectConsumesTopOnce() throws {
        let router = RouterState()
        let rootID = router.currentEntry.id
        let state = RouteViewState(query: "按", filters: ["scope": "国考"], scrollAnchor: "row-anchor")
        router.updateViewState(entryID: rootID, state: state)
        router.push(.library)
        let libraryID = router.currentEntry.id
        for tab in AppTab.allCases where tab != .idiom {
            router.selectTab(tab)
            XCTAssertEqual(router.currentEntry.route, tab.root)
        }
        router.selectTab(.idiom)
        XCTAssertEqual(router.currentEntry.id, libraryID)
        XCTAssertEqual(router.entries(for: .idiom).first?.viewState, state)
        XCTAssertNil(router.topRequests[.idiom])
        router.selectTab(.idiom)
        XCTAssertEqual(router.currentEntry.id, rootID)
        XCTAssertEqual(router.currentEntry.viewState.query, "按")
        XCTAssertEqual(router.currentEntry.viewState.filters, state.filters)
        XCTAssertNil(router.currentEntry.viewState.scrollAnchor)
        let token = try XCTUnwrap(router.topRequests[.idiom])
        router.consumeTopRequest(for: .idiom, token: UUID())
        XCTAssertEqual(router.topRequests[.idiom], token)
        router.consumeTopRequest(for: .idiom, token: token)
        XCTAssertNil(router.topRequests[.idiom])
        router.updateViewState(entryID: rootID, state: state)
        router.selectTab(.profile)
        router.selectTab(.idiom)
        XCTAssertEqual(router.currentEntry.viewState.scrollAnchor, "row-anchor")
        XCTAssertNil(router.topRequests[.idiom])
    }

    func testEachTabHasIndependentEntryArray() {
        let router = RouterState()
        for tab in AppTab.allCases {
            router.selectTab(tab)
            router.push(.library)
        }
        XCTAssertEqual(Set(AppTab.allCases.flatMap { router.entries(for: $0).map(\.id) }).count, 8)
        router.back()
        XCTAssertEqual(router.entries(for: .profile).count, 1)
        XCTAssertEqual(router.entries(for: .idiom).count, 2)
    }

    func testSearchHidesChromeAndReturnsToUnmodifiedStack() {
        let router = RouterState()
        router.push(.library)
        let origin = router.currentEntry
        XCTAssertTrue(router.openSearch())
        XCTAssertFalse(router.tabBarVisible)
        XCTAssertFalse(router.fabVisible)
        router.selectTab(.profile)
        XCTAssertEqual(router.selectedTab, .idiom)
        XCTAssertTrue(router.push(.idiomDetail, recordID: "fixture"))
        router.back()
        XCTAssertEqual(router.currentEntry.route, .search)
        router.back()
        XCTAssertEqual(router.currentEntry, origin)
        XCTAssertTrue(router.tabBarVisible)
        XCTAssertFalse(router.fabVisible)
    }

    func testFABOnlyOnFourRoots() {
        let router = RouterState()
        for tab in AppTab.allCases {
            router.selectTab(tab)
            XCTAssertTrue(router.fabVisible)
            router.push(.library)
            XCTAssertFalse(router.fabVisible)
            router.back()
        }
    }

    func testReviewSourcesRetryAndEmptyQueue() {
        let router = RouterState()
        XCTAssertFalse(router.beginReview(source: .global, queueIDs: ["fixture"]))
        router.selectTab(.mistake)
        let origin = router.currentEntry.id
        XCTAssertFalse(router.beginReview(source: .global, queueIDs: []))
        XCTAssertTrue(router.fullScreenEntries.isEmpty)
        XCTAssertTrue(router.beginReview(source: .global, queueIDs: ["mistake-a"]))
        XCTAssertEqual(router.reviewSession, ReviewSession(source: .global, tab: .mistake, originEntryID: origin))
        XCTAssertFalse(router.tabBarVisible)
        XCTAssertFalse(router.finishCapture())
        XCTAssertTrue(router.finishReview(queueEntryID: router.currentEntry.id))
        let summaryID = router.currentEntry.id
        XCTAssertFalse(router.retryReview(queueIDs: []))
        XCTAssertEqual(router.currentEntry.id, summaryID)
        XCTAssertTrue(router.retryReview(queueIDs: ["mistake-b"]))
        XCTAssertEqual(router.fullScreenEntries.count, 2)
        XCTAssertEqual(router.currentEntry.queueIDs, ["mistake-b"])
        router.back()
        XCTAssertEqual(router.currentEntry.id, summaryID)
        router.back()
        XCTAssertEqual(router.currentEntry.id, origin)
        XCTAssertNil(router.reviewSession)
        router.selectTab(.idiom)
        router.push(.idiomDetail, recordID: "idiom-fixture")
        XCTAssertFalse(router.beginReview(source: .idiom("other"), queueIDs: ["mistake-a"]))
        XCTAssertTrue(router.beginReview(source: .idiom("idiom-fixture"), queueIDs: ["mistake-a"]))
        XCTAssertEqual(router.reviewSession?.source, .idiom("idiom-fixture"))
    }

    func testCaptureCallbacksFiveStepsAndDoneForcesMistakeRootAndTop() throws {
        let router = RouterState()
        router.selectTab(.mistake)
        router.push(.mistakeDetail, recordID: "old-fixture")
        router.selectTab(.idiom)
        XCTAssertTrue(router.beginCapture())
        let workflowID = try XCTUnwrap(router.captureWorkflow?.id)
        let imageURL = URL(fileURLWithPath: "/test-fixture/image.jpg")
        XCTAssertFalse(router.saved(workflowID: workflowID, mistakeID: "fixture"))
        XCTAssertFalse(router.captured(workflowID: UUID(), imageURL: imageURL))
        XCTAssertTrue(router.captured(workflowID: workflowID, imageURL: imageURL))
        XCTAssertTrue(router.cropped(workflowID: workflowID, imageURL: imageURL))
        XCTAssertFalse(router.recognized(workflowID: workflowID, entryID: router.currentEntry.id, text: " "))
        XCTAssertTrue(router.recognized(workflowID: workflowID, entryID: router.currentEntry.id, text: "实际业务识别回调的测试输入"))
        XCTAssertFalse(router.saved(workflowID: workflowID, mistakeID: "fixture"))
        XCTAssertTrue(router.matched(workflowID: workflowID, draftID: "draft-fixture"))
        XCTAssertEqual(router.currentEntry.route, .match)
        XCTAssertTrue(router.saved(workflowID: workflowID, mistakeID: "saved-fixture"))
        XCTAssertEqual(router.fullScreenEntries.map(\.route), RouteRegistry.captureSteps)
        XCTAssertFalse(router.canGoBack)
        router.back()
        XCTAssertEqual(router.currentEntry.route, .done)
        XCTAssertTrue(router.finishCapture())
        XCTAssertEqual(router.selectedTab, .mistake)
        XCTAssertEqual(router.currentEntry.route, .mistakes)
        XCTAssertEqual(router.entries(for: .mistake).count, 1)
        XCTAssertNotNil(router.topRequests[.mistake])
        XCTAssertNil(router.captureWorkflow)
    }

    func testCaptureBackFirstStepCancelsAndStaleCallbacksCannotAdvance() throws {
        let router = RouterState()
        router.push(.library)
        let origin = router.currentEntry
        router.beginCapture()
        let oldID = try XCTUnwrap(router.captureWorkflow?.id)
        router.back()
        XCTAssertEqual(router.currentEntry, origin)
        XCTAssertNil(router.captureWorkflow)
        router.beginCapture()
        let newID = try XCTUnwrap(router.captureWorkflow?.id)
        let imageURL = URL(fileURLWithPath: "/test-fixture/image.jpg")
        XCTAssertFalse(router.captured(workflowID: oldID, imageURL: imageURL))
        router.captured(workflowID: newID, imageURL: imageURL)
        router.cropped(workflowID: newID, imageURL: imageURL)
        router.back()
        XCTAssertEqual(router.currentEntry.route, .crop)
        router.cancelCapture()
        XCTAssertEqual(router.currentEntry, origin)
        XCTAssertTrue(router.fullScreenEntries.isEmpty)
    }

    func testCropBackReturnsToCameraAndClearsImages() throws {
        let router = RouterState()
        router.beginCapture()
        let workflowID = try XCTUnwrap(router.captureWorkflow?.id)
        let image = URL(fileURLWithPath: "/test-fixture/image.jpg")
        XCTAssertTrue(router.captured(workflowID: workflowID, imageURL: image))
        XCTAssertEqual(router.currentEntry.route, .crop)
        router.back()
        XCTAssertEqual(router.currentEntry.route, .camera)
        XCTAssertNotNil(router.captureWorkflow)
        XCTAssertNil(router.captureWorkflow?.imageURL)
        XCTAssertNil(router.captureWorkflow?.croppedURL)
    }

    func testWorkflowFromSearchDetailRestoresSameEntriesAndState() throws {
        let router = RouterState()
        router.openSearch()
        router.push(.idiomDetail, recordID: "idiom-fixture")
        let origin = router.currentEntry
        let state = RouteViewState(query: "按", scrollAnchor: "detail-anchor")
        router.updateViewState(entryID: origin.id, state: state)
        let originalStack = router.fullScreenEntries
        XCTAssertTrue(router.beginReview(source: .idiom("idiom-fixture"), queueIDs: ["mistake-fixture"]))
        XCTAssertEqual(router.suspendedFullScreenEntries, originalStack)
        XCTAssertFalse(router.beginCapture())
        router.back()
        XCTAssertEqual(router.fullScreenEntries, originalStack)
        XCTAssertEqual(router.currentEntry.viewState, state)
        XCTAssertTrue(router.beginCapture())
        XCTAssertFalse(router.beginReview(source: .idiom("idiom-fixture"), queueIDs: ["mistake-fixture"]))
        let workflowID = try XCTUnwrap(router.captureWorkflow?.id)
        router.cancelCapture()
        XCTAssertEqual(router.fullScreenEntries, originalStack)
        XCTAssertTrue(router.suspendedFullScreenEntries.isEmpty)
        XCTAssertFalse(router.captured(workflowID: workflowID, imageURL: URL(fileURLWithPath: "/fixture.jpg")))
    }

    func testOverlayOriginAndCancellationAreNonMutating() {
        let router = RouterState()
        XCTAssertFalse(router.openOverlay(.clear))
        XCTAssertFalse(router.openOverlay(.importMode))
        router.selectTab(.profile)
        let profile = router.currentEntry
        XCTAssertTrue(router.openOverlay(.clear))
        XCTAssertFalse(router.fabVisible)
        router.closeOverlay()
        XCTAssertEqual(router.currentEntry, profile)
        router.push(.backup)
        let backup = router.currentEntry
        XCTAssertFalse(router.openOverlay(.clear))
        XCTAssertTrue(router.openOverlay(.importMode))
        router.back()
        XCTAssertNil(router.overlay)
        XCTAssertEqual(router.currentEntry, backup)
    }

}

extension RouterStateTests {
    func testLateReviewCompletionCannotFinishAnotherQueue() {
        let router = RouterState()
        router.selectTab(.mistake)
        router.beginReview(source: .global, queueIDs: ["fixture-a"])
        let oldEntryID = router.currentEntry.id
        router.back()
        router.beginReview(source: .global, queueIDs: ["fixture-b"])
        XCTAssertFalse(router.finishReview(queueEntryID: oldEntryID))
        XCTAssertEqual(router.currentEntry.route, .queue)
        XCTAssertTrue(router.finishReview(queueEntryID: router.currentEntry.id))
        router.retryReview(queueIDs: ["fixture-c"])
        XCTAssertFalse(router.finishReview(queueEntryID: oldEntryID))
    }

    func testLateRecognitionCannotAdvanceRecroppedImage() throws {
        let router = RouterState()
        router.beginCapture()
        let workflowID = try XCTUnwrap(router.captureWorkflow?.id)
        let image = URL(fileURLWithPath: "/fixture.jpg")
        router.captured(workflowID: workflowID, imageURL: image)
        router.cropped(workflowID: workflowID, imageURL: image)
        let oldEntryID = router.currentEntry.id
        router.back()
        router.cropped(workflowID: workflowID, imageURL: image)
        XCTAssertNotEqual(oldEntryID, router.currentEntry.id)
        XCTAssertFalse(router.recognized(workflowID: workflowID, entryID: oldEntryID, text: "callback-payload"))
        XCTAssertNil(router.captureWorkflow?.recognizedText)
        XCTAssertTrue(router.recognized(
            workflowID: workflowID, entryID: router.currentEntry.id, text: "callback-payload"
        ))
    }

    func testSummaryDetailsReturnToSummaryThenOrigin() {
        let router = RouterState()
        router.selectTab(.mistake)
        let origin = router.currentEntry
        XCTAssertTrue(router.beginReview(source: .global, queueIDs: ["fixture-a"]))
        XCTAssertFalse(router.push(.idiomDetail, recordID: "fixture-idiom"))
        XCTAssertTrue(router.finishReview(queueEntryID: router.currentEntry.id))
        let summary = router.currentEntry
        XCTAssertTrue(router.push(.idiomDetail, recordID: "fixture-idiom"))
        router.back()
        XCTAssertEqual(router.currentEntry, summary)
        router.back()
        XCTAssertEqual(router.currentEntry, origin)
        XCTAssertNil(router.reviewSession)
    }

    func testGoToMistakesClearsDeepStackAndRequestsRootTop() {
        let router = RouterState()
        router.selectTab(.mistake)
        let rootID = router.currentEntry.id
        router.push(.mistakeDetail, recordID: "fixture-mistake")
        router.selectTab(.idiom)
        router.openSearch()
        router.goToMistakes()
        XCTAssertEqual(router.selectedTab, .mistake)
        XCTAssertEqual(router.currentEntry.id, rootID)
        XCTAssertEqual(router.entries(for: .mistake).count, 1)
        XCTAssertTrue(router.fullScreenEntries.isEmpty)
        XCTAssertNotNil(router.topRequests[.mistake])
    }

    func testFirstLaunchBlocksChromeAndNavigationUntilSaved() {
        let router = RouterState(needsFirstLaunch: true)
        XCTAssertEqual(router.currentEntry.route, .onboarding)
        XCTAssertFalse(router.tabBarVisible)
        XCTAssertFalse(router.fabVisible)
        XCTAssertFalse(router.push(.scope))
        XCTAssertFalse(router.openSearch())
        XCTAssertFalse(router.beginCapture())
        router.selectTab(.profile)
        XCTAssertEqual(router.selectedTab, .idiom)
        router.completeFirstLaunch()
        XCTAssertTrue(router.tabBarVisible)
        XCTAssertEqual(router.currentEntry.route, .recommend)
    }
}
