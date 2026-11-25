import CangyuData
import CangyuTokens
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ChenYuBang

private struct SnapshotSelfTestReader: SelfTestLibraryReading {
    let scoped: [IdiomWordSense]
    let library: [IdiomRecord]

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

    func scopedWordSenses() throws -> [IdiomWordSense] { scoped }
    func allIdiomsForMatch() throws -> [IdiomRecord] { library }
}

private struct SnapshotSelfTestRandom: SelfTestRandomSource {
    var state: UInt64 = 7

    mutating func next(upperBound: Int) -> Int {
        state = state &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
        return Int((state >> 32) % UInt64(upperBound))
    }
}

private enum SnapshotFixtureError: Error {
    case missingQuestion
}

/// P25 的 8 类代表态 × 浅深，共 16 帧。所有帧都由真实 ViewModel / engine 到达，不手工拼死题目。
/// CI 录制兼容 TEST_RUNNER_SNAPSHOT_RECORD；未显式录制时始终是 verify 门禁。
@MainActor
final class P25SelfTestSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["TEST_RUNNER_SNAPSHOT_RECORD"] == "1"
            || ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func loadedModel(scopeCount: Int = 4, libraryCount: Int = 10) async -> P25SelfTestViewModel {
        let model = P25SelfTestViewModel(
            reader: SnapshotSelfTestReader(scopeCount: scopeCount, libraryCount: libraryCount),
            randomSource: SnapshotSelfTestRandom(),
            onExit: {}
        )
        await model.load()
        return model
    }

    private func current(_ model: P25SelfTestViewModel) throws -> P25QuestionState {
        guard case .question(let question) = model.state else { throw SnapshotFixtureError.missingQuestion }
        return question
    }

    private func finish(_ model: P25SelfTestViewModel, correctCount: Int = 7) throws {
        for index in 0..<10 {
            let state = try current(model)
            let optionID = index < correctCount
                ? state.question.targetID
                : try XCTUnwrap(state.question.options.first { $0.idiomID != state.question.targetID }).idiomID
            model.pick(optionID)
            model.next()
        }
    }

    private func assertP25(
        _ model: P25SelfTestViewModel,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P25SelfTestContent(
            state: model.state,
            exitAsking: model.exitAsking,
            actions: P25SelfTestActions(
                onPick: { _ in },
                onNext: {},
                onAskExit: {},
                onCancelExit: {},
                onConfirmExit: {},
                onLeave: {},
                onRetryRound: {},
                onRetryLoad: {}
            ),
            colors: dark ? Tokens.dark : Tokens.light
        )
        .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 390, height: 844)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testDefinitionQuestionUnansweredLight() async throws {
        let model = await loadedModel()
        XCTAssertEqual(try current(model).question.type, .definitionToIdiom)
        assertP25(model, dark: false)
    }

    func testDefinitionQuestionUnansweredDark() async throws {
        let model = await loadedModel()
        XCTAssertEqual(try current(model).question.type, .definitionToIdiom)
        assertP25(model, dark: true)
    }

    func testIdiomQuestionUnansweredLight() async throws {
        let model = await loadedModel()
        let first = try current(model)
        model.pick(first.question.targetID)
        model.next()
        XCTAssertEqual(try current(model).question.type, .idiomToDefinition)
        assertP25(model, dark: false)
    }

    func testIdiomQuestionUnansweredDark() async throws {
        let model = await loadedModel()
        let first = try current(model)
        model.pick(first.question.targetID)
        model.next()
        XCTAssertEqual(try current(model).question.type, .idiomToDefinition)
        assertP25(model, dark: true)
    }

    func testCorrectAnswerLight() async throws {
        let model = await loadedModel()
        model.pick(try current(model).question.targetID)
        XCTAssertTrue(try current(model).isCorrect)
        assertP25(model, dark: false)
    }

    func testCorrectAnswerDark() async throws {
        let model = await loadedModel()
        model.pick(try current(model).question.targetID)
        XCTAssertTrue(try current(model).isCorrect)
        assertP25(model, dark: true)
    }

    func testSelectedWrongAnswerLight() async throws {
        let model = await loadedModel()
        let question = try current(model).question
        let wrong = try XCTUnwrap(question.options.first { $0.idiomID != question.targetID })
        model.pick(wrong.idiomID)
        XCTAssertFalse(try current(model).isCorrect)
        assertP25(model, dark: false)
    }

    func testSelectedWrongAnswerDark() async throws {
        let model = await loadedModel()
        let question = try current(model).question
        let wrong = try XCTUnwrap(question.options.first { $0.idiomID != question.targetID })
        model.pick(wrong.idiomID)
        XCTAssertFalse(try current(model).isCorrect)
        assertP25(model, dark: true)
    }

    func testSummaryLight() async throws {
        let model = await loadedModel()
        try finish(model)
        assertP25(model, dark: false)
    }

    func testSummaryDark() async throws {
        let model = await loadedModel()
        try finish(model)
        assertP25(model, dark: true)
    }

    func testEmptyScopeLight() async {
        let model = await loadedModel(scopeCount: 0)
        XCTAssertEqual(model.state, .emptyScope)
        assertP25(model, dark: false)
    }

    func testEmptyScopeDark() async {
        let model = await loadedModel(scopeCount: 0)
        XCTAssertEqual(model.state, .emptyScope)
        assertP25(model, dark: true)
    }

    func testInsufficientCandidatesLight() async {
        let model = await loadedModel(scopeCount: 1, libraryCount: 3)
        XCTAssertEqual(model.state, .insufficientCandidates)
        assertP25(model, dark: false)
    }

    func testInsufficientCandidatesDark() async {
        let model = await loadedModel(scopeCount: 1, libraryCount: 3)
        XCTAssertEqual(model.state, .insufficientCandidates)
        assertP25(model, dark: true)
    }

    func testExitConfirmationLight() async throws {
        let model = await loadedModel()
        _ = try current(model)
        model.askExit()
        XCTAssertTrue(model.exitAsking)
        assertP25(model, dark: false)
    }

    func testExitConfirmationDark() async throws {
        let model = await loadedModel()
        _ = try current(model)
        model.askExit()
        XCTAssertTrue(model.exitAsking)
        assertP25(model, dark: true)
    }
}
