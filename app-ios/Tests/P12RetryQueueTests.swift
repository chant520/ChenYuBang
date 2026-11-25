import CangyuData
import XCTest

@testable import ChenYuBang

private final class StubRetryReviewReader: ReviewQueueReading {
    var items: [ReviewQueueItem] = []
    var queuedIdiomIDs: [String?] = []
    var failsQueue = false

    func queue(idiomID: String?) throws -> [ReviewQueueItem] {
        queuedIdiomIDs.append(idiomID)
        if failsQueue { throw StubRetryReviewError.queue }
        return items
    }

    func pendingCount(idiomID: String) throws -> Int {
        items.count
    }

    func idiomsInRound(mistakeIDs: [String]) throws -> [LinkedIdiom] {
        []
    }

    func record(mistakeID: String, result: String, reviewedAt: String) throws {}
}

private enum StubRetryReviewError: Error {
    case queue
}

/// P12 C9 再复习：只重查当前范围的最新待复习队列，不复活已掌握题或复用旧 queueIDs。
@MainActor
final class P12RetryQueueTests: XCTestCase {
    func testRetryStartsCurrentScopeQueueInsteadOfOldSummaryIDs() async {
        let reader = StubRetryReviewReader()
        reader.items = [item("mistake-fresh")]
        let model = makeModel(reader, oldQueueIDs: ["mistake-mastered"])
        var started: [[String]] = []

        await model.retryRound { ids in
            started.append(ids)
            return true
        }

        XCTAssertEqual(started, [["mistake-fresh"]])
        XCTAssertEqual(reader.queuedIdiomIDs, ["idiom-1"])
        XCTAssertNil(model.state.retryIssue)
        XCTAssertFalse(model.state.retrying)
    }

    func testEmptyCurrentQueueDoesNotStartOrReuseOldIDs() async {
        let reader = StubRetryReviewReader()
        let model = makeModel(reader, oldQueueIDs: ["mistake-old"])
        var starts = 0

        await model.retryRound { _ in
            starts += 1
            return true
        }

        XCTAssertEqual(starts, 0)
        XCTAssertEqual(model.state.retryIssue, "当前范围已没有待复习的错题")
        XCTAssertFalse(model.state.retrying)
    }

    func testQueueRecoveryClearsIssueAndStartsLatestIDs() async {
        let reader = StubRetryReviewReader()
        let model = makeModel(reader, oldQueueIDs: ["mistake-old"])
        await model.retryRound { _ in true }
        XCTAssertNotNil(model.state.retryIssue)
        reader.items = [item("mistake-restored")]
        var started: [String] = []

        await model.retryRound { ids in
            started = ids
            return true
        }

        XCTAssertEqual(started, ["mistake-restored"])
        XCTAssertNil(model.state.retryIssue)
    }

    func testQueueReadFailureShowsRetryableMessageWithoutStarting() async {
        let reader = StubRetryReviewReader()
        reader.failsQueue = true
        let model = makeModel(reader, oldQueueIDs: ["mistake-old"])
        var starts = 0

        await model.retryRound { _ in
            starts += 1
            return true
        }

        XCTAssertEqual(starts, 0)
        XCTAssertEqual(model.state.retryIssue, "待复习队列读取失败，请重试")
    }

    private func makeModel(
        _ reader: StubRetryReviewReader,
        oldQueueIDs: [String]
    ) -> P12SummaryViewModel {
        P12SummaryViewModel(
            reader: reader,
            scope: .idiom(id: "idiom-1", word: "按部就班"),
            summary: ReviewRoundOutcome(total: 1, answered: 1, correct: 1),
            queueIDs: oldQueueIDs
        )
    }

    private func item(_ id: String) -> ReviewQueueItem {
        ReviewQueueItem(
            id: id,
            stem: "最新待复习错题",
            options: ["甲", "乙", "丙", "丁"],
            myAnswer: "A",
            createdAt: "2026-11-25T00:00:00Z"
        )
    }
}
