import CangyuData
import XCTest

@testable import ChenYuBang

/// 复习读库口子的夹具：只搬运数据并把落库调用记下来。协议需求非 actor 隔离，
/// 夹具本身不能标 `@MainActor`，全部访问都发生在测试与 VM 的 MainActor 上下文里。
private final class FakeReviewReader: ReviewQueueReading {
    var items: [ReviewQueueItem] = []
    var idioms: [LinkedIdiom] = []
    /// 错题 id 到该错题命中成语 id 的关联，模拟 `mistake_idiom`：本轮只回队列内错题的成语。
    var idiomsByMistake: [String: [String]] = [:]
    var logs: [(mistakeID: String, result: String)] = []
    var failQueue = false
    var failIdioms = false
    var failRecord = false

    func queue(idiomID: String?) throws -> [ReviewQueueItem] {
        if failQueue { throw ReviewFakeError.queue }
        return items
    }

    func pendingCount(idiomID: String) throws -> Int {
        items.count
    }

    func idiomsInRound(mistakeIDs: [String]) throws -> [LinkedIdiom] {
        if failIdioms { throw ReviewFakeError.idioms }
        let hit = Set(mistakeIDs.flatMap { idiomsByMistake[$0] ?? [] })
        return idioms.filter { hit.contains($0.id) }
    }

    func record(mistakeID: String, result: String, reviewedAt: String) throws {
        if failRecord { throw ReviewFakeError.record }
        logs.append((mistakeID, result))
    }
}

private enum ReviewFakeError: Error {
    case queue
    case idioms
    case record
}

/// P11 判分与落库、P12 本轮数字（开发规范第 8.2 节）。夹具题都带 A–D 四选项，
/// 除明确标注「无选项」「首次未答」的题外，其余都可比对。
@MainActor
final class P11ReviewFlowTests: XCTestCase {
    private func item(
        _ id: String,
        first: String?,
        stem: String = "填入横线处最恰当的一项：改革不能______"
    ) -> ReviewQueueItem {
        ReviewQueueItem(
            id: id,
            stem: stem,
            options: ["甲", "乙", "丙", "丁"],
            myAnswer: first,
            createdAt: "2026-11-1\(id.suffix(1))T08:00:00Z"
        )
    }

    private func noOptionsItem(_ id: String) -> ReviewQueueItem {
        ReviewQueueItem(
            id: id,
            stem: "本题录入时没填选项",
            options: [nil, nil, nil, nil],
            myAnswer: "A",
            createdAt: "2026-11-10T08:00:00Z"
        )
    }

    private func makeModel(
        _ reader: FakeReviewReader,
        ids: [String],
        scope: ReviewScope = .global()
    ) -> (P11ReviewViewModel, () -> ReviewRoundOutcome?) {
        var captured: ReviewRoundOutcome?
        let model = P11ReviewViewModel(
            reader: reader,
            scope: scope,
            queueIDs: ids,
            onRoundFinished: { outcome in captured = outcome },
            onExit: {}
        )
        return (model, { captured })
    }

    func testComparableQuestionsWriteOneLogEachWithLibraryLiterals() async {
        let reader = FakeReviewReader()
        reader.items = [item("m1", first: "A"), item("m2", first: "C")]
        let (model, outcome) = makeModel(reader, ids: ["m1", "m2"])
        await model.load()

        model.pick("A")   // 与首次一致 → 答错
        XCTAssertTrue(model.state.judged)
        XCTAssertTrue(model.state.recorded)
        XCTAssertEqual(model.state.wrongCount, 1)
        XCTAssertEqual(model.state.correctCount, 0)
        model.next()

        model.pick("B")   // 与首次不同 → 答对
        XCTAssertEqual(model.state.correctCount, 1)
        XCTAssertEqual(model.state.answeredCount, 2)
        model.next()

        XCTAssertEqual(reader.logs.map(\.result), [ReviewLibrary.resultWrong, ReviewLibrary.resultRight])
        XCTAssertEqual(reader.logs.map(\.mistakeID), ["m1", "m2"])
        XCTAssertEqual(outcome(), ReviewRoundOutcome(total: 2, answered: 2, correct: 1))
    }

    func testUncomparableQuestionsAreNotLoggedAndOnlyCountInTotal() async {
        let reader = FakeReviewReader()
        // m1 首次未答（无法比对）；m2 一个选项都没录（无法作答）。
        reader.items = [item("m1", first: nil), noOptionsItem("m2")]
        let (model, outcome) = makeModel(reader, ids: ["m1", "m2"])
        await model.load()

        model.pick("C")
        XCTAssertEqual(model.state.picked, "C")
        XCTAssertTrue(model.state.recorded, "无法比对的题按口径直接放行，不卡在判分区")
        XCTAssertEqual(model.state.answeredCount, 0, "无法比对不计入判分题数")
        XCTAssertEqual(model.state.correctCount, 0)
        model.next()

        // 无选项题：载入即按口径跳过，作答区不可选，只计题量。
        XCTAssertFalse(model.state.canAnswer)
        XCTAssertTrue(model.state.judged)
        XCTAssertTrue(model.state.canGoNext)
        model.next()

        XCTAssertTrue(reader.logs.isEmpty, "两条不可比对口径都不写 review_log")
        XCTAssertEqual(outcome(), ReviewRoundOutcome(total: 2, answered: 0, correct: 0))
    }

    func testRecordFailureBlocksNextAndRetryDoesNotDoubleCount() async {
        let reader = FakeReviewReader()
        reader.items = [item("m1", first: "A")]
        reader.failRecord = true
        let (model, outcome) = makeModel(reader, ids: ["m1"])
        await model.load()

        model.pick("A")
        XCTAssertTrue(model.state.recordFailed)
        XCTAssertFalse(model.state.canGoNext, "成绩没落库前不给「下一题」，避免丢成绩")
        XCTAssertEqual(model.state.wrongCount, 1)

        reader.failRecord = false
        model.retryRecord()
        XCTAssertFalse(model.state.recordFailed)
        XCTAssertTrue(model.state.canGoNext)
        XCTAssertEqual(model.state.wrongCount, 1, "重试只补落库，不重复计数")
        XCTAssertEqual(reader.logs.map(\.mistakeID), ["m1"])

        model.next()
        XCTAssertEqual(outcome(), ReviewRoundOutcome(total: 1, answered: 1, correct: 0))
    }

    func testQueueIDsNarrowTheRoundAndKeepLibraryOrder() async {
        let reader = FakeReviewReader()
        reader.items = [item("m1", first: "A"), item("m2", first: "B"), item("m3", first: "C")]
        let (model, _) = makeModel(reader, ids: ["m3", "m1"])
        await model.load()
        XCTAssertEqual(model.state.items.map(\.id), ["m1", "m3"], "只收 beginReview 带下来的错题 id，读库给的顺序不动")
        XCTAssertEqual(model.state.progressText, "第 1 题 / 共 2 题")
    }

    func testQueueLoadFailureShowsRetryableBar() async {
        let reader = FakeReviewReader()
        reader.items = [item("m1", first: "A")]
        reader.failQueue = true
        let (model, outcome) = makeModel(reader, ids: ["m1"])
        await model.load()
        XCTAssertTrue(model.state.failed)
        XCTAssertTrue(model.state.items.isEmpty)
        XCTAssertNil(outcome())

        reader.failQueue = false
        await model.load()
        XCTAssertFalse(model.state.failed)
        XCTAssertEqual(model.state.items.map(\.id), ["m1"])
    }

    func testExitNeedsConfirmationAndDiscardsRoundProgress() async {
        let reader = FakeReviewReader()
        reader.items = [item("m1", first: "A")]
        var exited = 0
        let model = P11ReviewViewModel(
            reader: reader,
            scope: .global(),
            queueIDs: ["m1"],
            onRoundFinished: { _ in },
            onExit: { exited += 1 }
        )
        await model.load()
        model.pick("A")

        model.askExit()
        XCTAssertTrue(model.state.exitAsking)
        XCTAssertEqual(exited, 0, "只弹确认，确认前不退出")
        model.cancelExit()
        XCTAssertFalse(model.state.exitAsking)

        model.askExit()
        model.confirmExit()
        XCTAssertEqual(exited, 1)
        XCTAssertFalse(model.state.exitAsking)
    }

    func testPickIsIgnoredAfterJudging() async {
        let reader = FakeReviewReader()
        reader.items = [item("m1", first: "A")]
        let (model, _) = makeModel(reader, ids: ["m1"])
        await model.load()
        model.pick("A")
        model.pick("D")
        XCTAssertEqual(model.state.picked, "A", "本题判分后保持展示，不给改答案重算")
        XCTAssertEqual(reader.logs.count, 1)
    }

    func testSummaryNumbersComeFromRoundOutcomeAndIdiomsFromRoundIds() async {
        let reader = FakeReviewReader()
        reader.idioms = [
            LinkedIdiom(id: "i2", word: "按部就班"),
            LinkedIdiom(id: "i1", word: "墨守成规"),
            LinkedIdiom(id: "i3", word: "别出心裁")
        ]
        // i3 只挂在本轮队列之外的 m3 上，用来证明「涉及成语」确实按错题 id 收窄。
        reader.idiomsByMistake = ["m1": ["i2"], "m2": ["i1", "i2"], "m3": ["i3"]]
        let model = P12SummaryViewModel(
            reader: reader,
            scope: .idiom(id: "i1", word: "墨守成规"),
            summary: ReviewRoundOutcome(total: 5, answered: 3, correct: 2),
            queueIDs: ["m1", "m2"]
        )
        await model.load()
        XCTAssertEqual(model.state.total, 5)
        XCTAssertEqual(model.state.correct, 2)
        XCTAssertEqual(model.state.wrong, 1, "答错只算可比对题：5 题里 2 题无法比对不计入")
        XCTAssertEqual(model.state.scope.line, "只看『墨守成规』")
        XCTAssertEqual(model.state.idioms.map(\.id), ["i2", "i1"], "涉及成语按本轮队列的错题 id 取")
    }

    func testSummaryIdiomLoadFailureKeepsRoundNumbers() async {
        let reader = FakeReviewReader()
        reader.idioms = [LinkedIdiom(id: "i1", word: "墨守成规")]
        reader.failIdioms = true
        let model = P12SummaryViewModel(
            reader: reader,
            scope: .global(),
            summary: ReviewRoundOutcome(total: 2, answered: 1, correct: 1),
            queueIDs: ["m1", "m2"]
        )
        await model.load()
        XCTAssertTrue(model.state.failed)
        XCTAssertTrue(model.state.idioms.isEmpty)
        XCTAssertEqual(model.state.total, 2, "成语读取失败不影响本轮题量与对错")
    }
}
