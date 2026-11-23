import Foundation
import XCTest
@testable import ChenYuBang

/// 复习路由（工作包 3.1 / 3.3）：入口来源、本轮小结随栈entry 存活、迟到回调不串轮。
/// 与 `RouterStateTests` 分文件是因为该文件已接近 SwiftLint `file_length` 上限（400 行，CI strict）。
final class RouterReviewTests: XCTestCase {
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
        XCTAssertFalse(router.finishReview(
            queueEntryID: router.currentEntry.id,
            outcome: ReviewRoundOutcome(total: 0, answered: 0, correct: 0)
        ))
        XCTAssertEqual(router.currentEntry.route, .queue)
        XCTAssertNil(router.currentEntry.reviewOutcome)
        XCTAssertTrue(router.finishReview(
            queueEntryID: router.currentEntry.id,
            outcome: ReviewRoundOutcome(total: 1, answered: 1, correct: 1)
        ))
        XCTAssertEqual(router.currentEntry.reviewOutcome, ReviewRoundOutcome(total: 1, answered: 1, correct: 1))
        let summaryID = router.currentEntry.id
        let summary = router.currentEntry
        XCTAssertFalse(router.retryReview(queueIDs: []))
        XCTAssertEqual(router.currentEntry.id, summaryID)
        XCTAssertTrue(router.retryReview(queueIDs: ["mistake-b"]))
        XCTAssertNil(router.currentEntry.reviewOutcome)
        XCTAssertTrue(router.finishReview(
            queueEntryID: router.currentEntry.id,
            outcome: ReviewRoundOutcome(total: 2, answered: 1, correct: 0)
        ))
        XCTAssertEqual(router.currentEntry.reviewOutcome, ReviewRoundOutcome(total: 2, answered: 1, correct: 0))
        router.back()
        XCTAssertEqual(router.currentEntry, summary)
        XCTAssertEqual(router.currentEntry.id, summaryID)
        XCTAssertEqual(router.currentEntry.queueIDs, ["mistake-a"])
        XCTAssertEqual(router.currentEntry.reviewOutcome, ReviewRoundOutcome(total: 1, answered: 1, correct: 1))
        router.back()
        XCTAssertTrue(router.fullScreenEntries.isEmpty)
        XCTAssertEqual(router.currentEntry.id, origin)
        XCTAssertNil(router.reviewSession)
        router.selectTab(.idiom)
        router.push(.idiomDetail, recordID: "idiom-fixture")
        XCTAssertFalse(router.beginReview(source: .idiom(id: "other", word: "其他"), queueIDs: ["mistake-a"]))
        XCTAssertTrue(
            router.beginReview(source: .idiom(id: "idiom-fixture", word: "一字千金"), queueIDs: ["mistake-a"])
        )
        XCTAssertEqual(router.reviewSession?.source, .idiom(id: "idiom-fixture", word: "一字千金"))
    }

    /// 上一轮的完成回调不能把当前轮直接推到小结；反之当前轮 entry 必须能正常收口。
    func testLateReviewCompletionCannotFinishAnotherQueue() {
        let router = RouterState()
        let round = ReviewRoundOutcome(total: 1, answered: 1, correct: 1)
        router.selectTab(.mistake)
        router.beginReview(source: .global, queueIDs: ["fixture-a"])
        let oldEntryID = router.currentEntry.id
        router.back()
        router.beginReview(source: .global, queueIDs: ["fixture-b"])
        XCTAssertFalse(router.finishReview(queueEntryID: oldEntryID, outcome: round))
        XCTAssertEqual(router.currentEntry.route, .queue)
        XCTAssertTrue(router.finishReview(queueEntryID: router.currentEntry.id, outcome: round))
        router.retryReview(queueIDs: ["fixture-c"])
        XCTAssertFalse(router.finishReview(queueEntryID: oldEntryID, outcome: round))
        XCTAssertTrue(router.finishReview(queueEntryID: router.currentEntry.id, outcome: round))
    }

    /// 小结页里点进的成语/例题详情回退仍回小结，再退一次才回来源页（IA 跳转表 21 / 25）。
    func testSummaryDetailsReturnToSummaryThenOrigin() {
        let router = RouterState()
        router.selectTab(.mistake)
        let origin = router.currentEntry
        XCTAssertTrue(router.beginReview(source: .global, queueIDs: ["fixture-a"]))
        XCTAssertFalse(router.push(.idiomDetail, recordID: "fixture-idiom"))
        XCTAssertTrue(router.finishReview(
            queueEntryID: router.currentEntry.id,
            outcome: ReviewRoundOutcome(total: 1, answered: 1, correct: 1)
        ))
        let summary = router.currentEntry
        XCTAssertTrue(router.push(.idiomDetail, recordID: "fixture-idiom"))
        router.back()
        XCTAssertEqual(router.currentEntry, summary)
        router.back()
        XCTAssertEqual(router.currentEntry, origin)
        XCTAssertNil(router.reviewSession)
    }
}
