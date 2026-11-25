import CangyuData
import Foundation
import XCTest
@testable import ChenYuBang

final class RouterStateTests: XCTestCase {
    func testRegistryKeepsAll24KeysAndFourRoots() {
        XCTAssertEqual(AppRoute.allCases.map(\.rawValue), [
            "onboarding", "tab/recommend", "library", "search", "idiom/{id}", "favorites", "tab/examples",
            "example/{id}", "tab/mistakes", "mistakes/idiom/{id}", "mistake/{id}",
            "review/queue", "review/summary", "tab/me", "settings/scope", "settings/backup",
            "settings/pack", "capture/camera", "capture/crop", "capture/ocr", "capture/match",
            "capture/done", "sheet/import-mode", "dialog/clear"
        ])
        XCTAssertEqual(AppTab.allCases.map(\.label), ["成语", "例题", "错题", "我的"])
        XCTAssertEqual(AppRoute.profile.owner, .profile)
        XCTAssertEqual(RouteRegistry.fabRoutes, Set(AppTab.allCases.map(\.root)))
    }

    func testFavoritesPushFromProfileAndBack() {
        XCTAssertEqual(AppRoute.favorites.rawValue, "favorites")
        XCTAssertEqual(AppRoute.favorites.title, "我的收藏")
        XCTAssertEqual(AppRoute.favorites.owner, .idiom)
        XCTAssertEqual(AppRoute.favorites.presentation, .push)
        let router = RouterState()
        router.selectTab(.profile)
        XCTAssertTrue(router.push(.favorites))
        XCTAssertEqual(router.currentEntry.route, .favorites)
        XCTAssertEqual(router.entries(for: .profile).count, 2)
        router.back()
        XCTAssertEqual(router.currentEntry.route, .profile)
        XCTAssertEqual(router.entries(for: .profile).count, 1)
        // P24 空态「去成语库」：切到成语标签并压入成语库，返回各标签栈独立。
        XCTAssertTrue(router.push(.favorites))
        router.selectTab(.idiom)
        XCTAssertTrue(router.push(.library))
        XCTAssertEqual(router.currentEntry.route, .library)
        XCTAssertEqual(router.entries(for: .profile).count, 2)
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

    func testSuccessfulTabEntryIncrementsEpochIncludingReselection() {
        let router = RouterState()
        XCTAssertEqual(router.tabEntryEpoch, Dictionary(uniqueKeysWithValues: AppTab.allCases.map { ($0, 0) }))
        router.selectTab(.example)
        XCTAssertEqual(router.tabEntryEpoch[.example], 1)
        XCTAssertEqual(router.tabEntryEpoch[.idiom], 0)
        router.selectTab(.example)
        XCTAssertEqual(router.tabEntryEpoch[.example], 2)
        router.push(.library)
        router.selectTab(.example)
        XCTAssertEqual(router.tabEntryEpoch[.example], 3)
        XCTAssertEqual(router.currentEntry.route, .examples)
        router.openSearch()
        router.selectTab(.profile)
        XCTAssertEqual(router.tabEntryEpoch[.profile], 0)
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

    func testCaptureCallbacksFiveStepsAndDoneForcesMistakeRootAndTop() throws {
        let router = RouterState()
        router.selectTab(.mistake)
        router.push(.mistakeDetail, recordID: "old-fixture")
        router.selectTab(.idiom)
        XCTAssertTrue(router.beginCapture())
        let workflowID = try XCTUnwrap(router.captureWorkflow?.id)
        let imageURL = URL(fileURLWithPath: "/test-fixture/image.jpg")
        let idioms = [SavedIdiom(id: "idiom-anbu", word: "按部就班")]
        XCTAssertFalse(router.saved(
            workflowID: workflowID, userExampleID: "saved-fixture", mistakeID: nil, idioms: idioms
        ))
        XCTAssertFalse(router.captured(workflowID: UUID(), imageURL: imageURL))
        XCTAssertTrue(router.captured(workflowID: workflowID, imageURL: imageURL))
        XCTAssertTrue(router.cropped(workflowID: workflowID, imageURL: imageURL))
        XCTAssertFalse(router.recognized(workflowID: workflowID, entryID: router.currentEntry.id, text: " "))
        XCTAssertTrue(router.recognized(workflowID: workflowID, entryID: router.currentEntry.id, text: "实际业务识别回调的测试输入"))
        XCTAssertFalse(router.saved(
            workflowID: workflowID, userExampleID: "saved-fixture", mistakeID: nil, idioms: []
        ))
        XCTAssertEqual(router.currentEntry.route, .match)
        XCTAssertTrue(router.saved(
            workflowID: workflowID, userExampleID: "saved-fixture", mistakeID: "mistake-fixture", idioms: idioms
        ))
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
    func testWorkflowFromSearchDetailRestoresSameEntriesAndState() throws {
        let router = RouterState()
        router.openSearch()
        router.push(.idiomDetail, recordID: "idiom-fixture")
        let origin = router.currentEntry
        let state = RouteViewState(query: "按", scrollAnchor: "detail-anchor")
        router.updateViewState(entryID: origin.id, state: state)
        let originalStack = router.fullScreenEntries
        XCTAssertTrue(router.beginReview(
            source: .idiom(id: "idiom-fixture", word: "一字千金"), queueIDs: ["mistake-fixture"]
        ))
        XCTAssertEqual(router.suspendedFullScreenEntries, originalStack)
        XCTAssertFalse(router.beginCapture())
        router.back()
        XCTAssertEqual(router.fullScreenEntries, originalStack)
        XCTAssertEqual(router.currentEntry.viewState, state)
        XCTAssertTrue(router.beginCapture())
        XCTAssertFalse(router.beginReview(
            source: .idiom(id: "idiom-fixture", word: "一字千金"), queueIDs: ["mistake-fixture"]
        ))
        let workflowID = try XCTUnwrap(router.captureWorkflow?.id)
        router.cancelCapture()
        XCTAssertEqual(router.fullScreenEntries, originalStack)
        XCTAssertTrue(router.suspendedFullScreenEntries.isEmpty)
        XCTAssertFalse(router.captured(workflowID: workflowID, imageURL: URL(fileURLWithPath: "/fixture.jpg")))
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

    func testOcrRetakeRestartsAtCameraAndDropsArtifacts() throws {
        let router = RouterState()
        router.beginCapture()
        let workflowID = try XCTUnwrap(router.captureWorkflow?.id)
        let image = URL(fileURLWithPath: "/test-fixture/image.jpg")
        router.captured(workflowID: workflowID, imageURL: image)
        router.cropped(workflowID: workflowID, imageURL: image)
        XCTAssertTrue(router.recognized(
            workflowID: workflowID, entryID: router.currentEntry.id, text: "ocr-text"
        ))
        router.back()
        XCTAssertEqual(router.currentEntry.route, .ocr)
        XCTAssertTrue(router.retakeFromOcr(workflowID: workflowID))
        XCTAssertEqual(router.fullScreenEntries.map(\.route), [.camera])
        XCTAssertNotNil(router.captureWorkflow)
        XCTAssertNil(router.captureWorkflow?.imageURL)
        XCTAssertNil(router.captureWorkflow?.croppedURL)
        XCTAssertNil(router.captureWorkflow?.recognizedText)
        XCTAssertFalse(router.retakeFromOcr(workflowID: workflowID))
        XCTAssertFalse(router.retakeFromOcr(workflowID: UUID()))
    }

    func testOcrManualEntryAdvancesWithEmptyTextAndKeepsEditsOnReturn() throws {
        let router = RouterState()
        router.beginCapture()
        let workflowID = try XCTUnwrap(router.captureWorkflow?.id)
        let image = URL(fileURLWithPath: "/test-fixture/image.jpg")
        router.captured(workflowID: workflowID, imageURL: image)
        router.cropped(workflowID: workflowID, imageURL: image)
        let ocrEntryID = router.currentEntry.id
        XCTAssertFalse(router.recognizedManually(workflowID: workflowID, entryID: UUID()))
        XCTAssertTrue(router.recognizedManually(workflowID: workflowID, entryID: ocrEntryID))
        XCTAssertEqual(router.currentEntry.route, .match)
        XCTAssertNil(router.captureWorkflow?.recognizedText)
        router.back()
        XCTAssertEqual(router.currentEntry.route, .ocr)
        XCTAssertEqual(router.currentEntry.id, ocrEntryID)
        XCTAssertTrue(router.recognized(workflowID: workflowID, entryID: ocrEntryID, text: "A. 补上的字"))
        XCTAssertEqual(router.captureWorkflow?.recognizedText, "A. 补上的字")
        router.back()
        XCTAssertEqual(router.currentEntry.route, .ocr)
        XCTAssertEqual(router.captureWorkflow?.recognizedText, "A. 补上的字")
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
