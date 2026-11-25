import Foundation
import XCTest
@testable import ChenYuBang

/// P13 → P24 全屏路由：只在「我的」启动，退出回原栈，且不串 D1 / 拍照工作流。
final class RouterSelfTestTests: XCTestCase {
    func testSelfTestStartsOnlyFromProfileAndRestoresOrigin() {
        let router = RouterState()
        XCTAssertFalse(router.beginSelfTest())

        router.selectTab(.profile)
        let origin = router.currentEntry
        XCTAssertTrue(router.beginSelfTest())
        XCTAssertEqual(router.currentEntry.route, .selfTest)
        XCTAssertEqual(AppRoute.selfTest.rawValue, "review/self-test")
        XCTAssertEqual(AppRoute.selfTest.owner, .review)
        XCTAssertEqual(AppRoute.selfTest.presentation, .fullScreen)
        XCTAssertEqual(AppRoute.selfTest.title, "成语自测")
        XCTAssertFalse(router.tabBarVisible)
        XCTAssertFalse(router.fabVisible)
        XCTAssertNil(router.reviewSession)
        XCTAssertNil(router.captureWorkflow)
        XCTAssertFalse(router.push(.idiomDetail, recordID: "fixture"))
        XCTAssertFalse(router.beginCapture())
        XCTAssertFalse(router.beginReview(source: .global, queueIDs: ["fixture"]))

        router.dismissFullScreen()
        XCTAssertEqual(router.currentEntry, origin)
        XCTAssertEqual(router.currentEntry.route, .profile)
        XCTAssertTrue(router.fullScreenEntries.isEmpty)
        XCTAssertTrue(router.suspendedFullScreenEntries.isEmpty)
        XCTAssertTrue(router.tabBarVisible)
    }

    func testLeavingAndReenteringCreatesFreshRouteEntry() {
        let router = RouterState()
        router.selectTab(.profile)
        XCTAssertTrue(router.beginSelfTest())
        let first = router.currentEntry.id

        router.back()
        XCTAssertEqual(router.currentEntry.route, .selfTest)
        XCTAssertEqual(router.selfTestBackRequest, 1)
        router.dismissFullScreen()
        XCTAssertEqual(router.currentEntry.route, .profile)
        XCTAssertTrue(router.beginSelfTest())
        XCTAssertNotEqual(router.currentEntry.id, first, "新一轮由新 entry 持有新的内存 ViewModel")
    }

    func testFirstLaunchAndOtherWorkflowsBlockSelfTest() {
        let firstLaunch = RouterState(needsFirstLaunch: true)
        XCTAssertFalse(firstLaunch.beginSelfTest())

        let capture = RouterState()
        XCTAssertTrue(capture.beginCapture())
        XCTAssertFalse(capture.beginSelfTest())

        let review = RouterState()
        review.selectTab(.mistake)
        XCTAssertTrue(review.beginReview(source: .global, queueIDs: ["fixture"]))
        XCTAssertFalse(review.beginSelfTest())
    }
}
