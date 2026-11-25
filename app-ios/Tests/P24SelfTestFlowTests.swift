import CangyuData
import XCTest
@testable import ChenYuBang

private final class FakeSelfTestReader: @unchecked Sendable, SelfTestLibraryReading {
    var scoped: [IdiomWordSense]
    var library: [IdiomRecord]
    var fail = false
    var scopedReads = 0
    var libraryReads = 0

    init(scopeCount: Int = 4, libraryCount: Int = 10) {
        library = (0..<libraryCount).map { index in
            IdiomRecord(
                id: "i\(index)",
                word: "成语\(index)",
                pinyin: "chengyu\(index)",
                initials: "cy\(index)",
                sense: "释义\(index)",
                origin: nil,
                color: "中性",
                pitfalls: "[]",
                synonyms: "[]",
                antonyms: "[]",
                frequency: 0
            )
        }
        scoped = library.prefix(min(scopeCount, libraryCount)).map {
            IdiomWordSense(id: $0.id, word: $0.word, sense: $0.sense)
        }
    }

    func scopedWordSenses() throws -> [IdiomWordSense] {
        scopedReads += 1
        if fail { throw SelfTestFlowError.read }
        return scoped
    }

    func allIdiomsForMatch() throws -> [IdiomRecord] {
        libraryReads += 1
        if fail { throw SelfTestFlowError.read }
        return library
    }
}

private struct FlowRandomSource: SelfTestRandomSource {
    var state: UInt64 = 7

    mutating func next(upperBound: Int) -> Int {
        state = state &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
        return Int((state >> 32) % UInt64(upperBound))
    }
}

private enum SelfTestFlowError: Error {
    case read
    case missingQuestion
}

@MainActor
final class P24SelfTestFlowTests: XCTestCase {
    private func makeModel(_ reader: FakeSelfTestReader, exited: @escaping () -> Void = {}) -> P24SelfTestViewModel {
        P24SelfTestViewModel(reader: reader, randomSource: FlowRandomSource(), onExit: exited)
    }

    private func current(_ model: P24SelfTestViewModel) throws -> P24QuestionState {
        guard case .question(let question) = model.state else { throw SelfTestFlowError.missingQuestion }
        return question
    }

    private func finishRound(_ model: P24SelfTestViewModel, correctCount: Int = 7) throws {
        for index in 0..<10 {
            let state = try current(model)
            let optionID = index < correctCount
                ? state.question.targetID
                : try XCTUnwrap(state.question.options.first { $0.idiomID != state.question.targetID }).idiomID
            if index == 9 { XCTAssertEqual(state.nextButtonTitle, "查看结果") }
            model.pick(optionID)
            model.next()
        }
    }

    func testLoadStartsLoadingThenBuildsFirstDefinitionQuestion() async {
        let reader = FakeSelfTestReader()
        let model = makeModel(reader)
        XCTAssertEqual(model.state, .loading)

        await model.load()

        let question = try current(model)
        XCTAssertEqual(question.number, 1)
        XCTAssertEqual(question.total, 10)
        XCTAssertEqual(question.question.type, .definitionToIdiom)
        XCTAssertEqual(question.progressText, "第 1 题 / 共 10 题")
        XCTAssertNil(question.selectedOptionID)
        XCTAssertEqual(reader.scopedReads, 1)
        XCTAssertEqual(reader.libraryReads, 1)
    }

    func testReadFailureIsRetryableWithoutFabricatingQuestions() async {
        let reader = FakeSelfTestReader()
        reader.fail = true
        let model = makeModel(reader)

        await model.load()
        XCTAssertEqual(model.state, .error)

        reader.fail = false
        await model.load()
        XCTAssertEqual(try current(model).number, 1)
        XCTAssertEqual(reader.scopedReads, 2)
        XCTAssertEqual(reader.libraryReads, 1, "首次范围读失败时尚未进入全库读取")
    }

    func testEmptyScopeAndInsufficientCandidatesAreDistinctStates() async {
        let emptyModel = makeModel(FakeSelfTestReader(scopeCount: 0))
        await emptyModel.load()
        XCTAssertEqual(emptyModel.state, .emptyScope)

        let insufficientModel = makeModel(FakeSelfTestReader(scopeCount: 1, libraryCount: 3))
        await insufficientModel.load()
        XCTAssertEqual(insufficientModel.state, .insufficientCandidates)
    }

    func testPickLocksImmediatelyAndNextIsGuardedUntilSelection() async throws {
        let model = makeModel(FakeSelfTestReader())
        await model.load()
        let first = try current(model)
        let wrong = try XCTUnwrap(first.question.options.first { $0.idiomID != first.question.targetID })

        model.next()
        XCTAssertEqual(try current(model).number, 1, "未选择不能推进")
        model.pick(wrong.idiomID)
        let graded = try current(model)
        XCTAssertEqual(graded.selectedOptionID, wrong.idiomID)
        XCTAssertFalse(graded.isCorrect)
        model.pick(first.question.targetID)
        XCTAssertEqual(try current(model).selectedOptionID, wrong.idiomID, "重复点击不能改判")

        model.next()
        let second = try current(model)
        XCTAssertEqual(second.number, 2)
        XCTAssertEqual(second.question.type, .idiomToDefinition)
        XCTAssertNil(second.selectedOptionID)
        XCTAssertEqual(second.nextButtonTitle, "下一题")
    }

    func testTenManualStepsProduceInMemorySummaryCounts() async throws {
        let model = makeModel(FakeSelfTestReader())
        await model.load()
        try finishRound(model, correctCount: 7)

        guard case .summary(let summary) = model.state else {
            return XCTFail("完成 10 题后应停留在本页小结")
        }
        XCTAssertEqual(summary, P24SummaryState(total: 10, correct: 7, wrong: 3))
    }

    func testRetryCreatesFreshTransientRoundWithoutRereadingLibrary() async throws {
        let reader = FakeSelfTestReader()
        let model = makeModel(reader)
        await model.load()
        try finishRound(model)

        model.retryRound()

        let fresh = try current(model)
        XCTAssertEqual(fresh.number, 1)
        XCTAssertEqual(fresh.total, 10)
        XCTAssertEqual(fresh.question.type, .definitionToIdiom)
        XCTAssertNil(fresh.selectedOptionID)
        XCTAssertEqual(fresh.correctCount, 0)
        XCTAssertEqual(reader.scopedReads, 1)
        XCTAssertEqual(reader.libraryReads, 1)
    }

    func testRetryRoundIsIgnoredBeforeSummary() async throws {
        let model = makeModel(FakeSelfTestReader())
        await model.load()
        let first = try current(model)
        model.pick(first.question.targetID)

        model.retryRound()

        XCTAssertEqual(try current(model).selectedOptionID, first.question.targetID)
    }

    func testUnfinishedExitNeedsConfirmationAndDiscardsProgress() async throws {
        var exits = 0
        let model = makeModel(FakeSelfTestReader(), exited: { exits += 1 })
        await model.load()
        let first = try current(model)
        model.pick(first.question.targetID)

        model.askExit()
        XCTAssertTrue(model.exitAsking)
        XCTAssertEqual(exits, 0)
        model.cancelExit()
        XCTAssertFalse(model.exitAsking)
        XCTAssertEqual(try current(model).selectedOptionID, first.question.targetID)

        model.askExit()
        model.confirmExit()
        XCTAssertEqual(exits, 1)
        XCTAssertFalse(model.exitAsking)
        XCTAssertEqual(model.state, .loading)
        model.next()
        XCTAssertEqual(model.state, .loading, "退出后旧 session 不得继续推进")
    }

    func testSummaryAndEmptyScopeExitDirectlyWithoutConfirmation() async throws {
        var summaryExits = 0
        let summaryModel = makeModel(FakeSelfTestReader(), exited: { summaryExits += 1 })
        await summaryModel.load()
        try finishRound(summaryModel)
        summaryModel.askExit()
        XCTAssertFalse(summaryModel.exitAsking)
        XCTAssertEqual(summaryExits, 1)

        var emptyExits = 0
        let emptyModel = makeModel(FakeSelfTestReader(scopeCount: 0), exited: { emptyExits += 1 })
        await emptyModel.load()
        emptyModel.askExit()
        XCTAssertFalse(emptyModel.exitAsking)
        XCTAssertEqual(emptyExits, 1)
    }
}
