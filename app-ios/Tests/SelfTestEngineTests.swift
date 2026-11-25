import XCTest
@testable import ChenYuBang

private struct SeededSelfTestRandom: SelfTestRandomSource {
    var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next(upperBound: Int) -> Int {
        precondition(upperBound > 0)
        state = state &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
        return Int((state >> 32) % UInt64(upperBound))
    }
}

private enum EngineFixtureError: Error {
    case notReady
}

/// P24 纯引擎：资格过滤、范围边界、10 题交替、循环目标、四选一与内存判分。
final class SelfTestEngineTests: XCTestCase {
    private func candidate(_ id: String, word: String? = nil, sense: String? = nil) -> SelfTestCandidate {
        SelfTestCandidate(id: id, word: word ?? "成语\(id)", sense: sense ?? "释义\(id)")
    }

    private func library(_ count: Int) -> [SelfTestCandidate] {
        (0..<count).map { candidate("i\($0)") }
    }

    private func makeSession(
        scoped: [SelfTestCandidate],
        library: [SelfTestCandidate],
        seed: UInt64 = 7
    ) throws -> SelfTestSession {
        var random = SeededSelfTestRandom(seed: seed)
        switch SelfTestRoundBuilder.make(scoped: scoped, library: library, randomSource: &random) {
        case .ready(let session): return session
        case .emptyScope: throw EngineFixtureError.notReady
        case .insufficientCandidates: throw EngineFixtureError.notReady
        }
    }

    func testTenQuestionsAlternateTypesAndAlwaysHaveFourUniqueOptions() throws {
        let all = library(12)
        let scoped = Array(all.prefix(5))
        let session = try makeSession(scoped: scoped, library: all)
        let scopedIDs = Set(scoped.map(\.id))
        let libraryIDs = Set(all.map(\.id))
        let candidatesByID = Dictionary(uniqueKeysWithValues: all.map { ($0.id, $0) })

        XCTAssertEqual(session.questions.count, 10)
        for (index, question) in session.questions.enumerated() {
            let expectedType: SelfTestQuestionType = index.isMultiple(of: 2)
                ? .definitionToIdiom : .idiomToDefinition
            XCTAssertEqual(question.type, expectedType)
            XCTAssertTrue(scopedIDs.contains(question.targetID), "正确目标不得放宽到范围外")
            XCTAssertEqual(question.options.count, 4)
            XCTAssertEqual(Set(question.options.map(\.idiomID)).count, 4)
            XCTAssertEqual(question.options.filter { $0.idiomID == question.targetID }.count, 1)
            XCTAssertTrue(question.options.allSatisfy { libraryIDs.contains($0.idiomID) })
            XCTAssertEqual(question.options.filter { $0.idiomID != question.targetID }.count, 3)
            if expectedType == .definitionToIdiom {
                XCTAssertEqual(question.prompt, candidatesByID[question.targetID]?.sense)
                XCTAssertTrue(question.options.allSatisfy { candidatesByID[$0.idiomID]?.word == $0.text })
            } else {
                XCTAssertEqual(question.prompt, candidatesByID[question.targetID]?.word)
                XCTAssertTrue(question.options.allSatisfy { candidatesByID[$0.idiomID]?.sense == $0.text })
            }
        }
    }

    func testShuffledTargetSequenceCyclesWhenScopeHasFewerThanTen() throws {
        let all = library(8)
        let session = try makeSession(scoped: Array(all.prefix(3)), library: all)
        let firstThree = session.questions.prefix(3).map(\.targetID)

        XCTAssertEqual(Set(firstThree).count, 3, "洗牌后的目标序列先覆盖三个不同目标")
        for (index, question) in session.questions.enumerated() {
            XCTAssertEqual(question.targetID, firstThree[index % 3])
        }
    }

    func testDistractorsUseFullEligibleLibraryWithoutWideningTargets() throws {
        let all = library(9)
        let scopedIDs: Set<String> = ["i0", "i1"]
        let libraryIDs = Set(all.map(\.id))
        let session = try makeSession(scoped: Array(all.prefix(2)), library: all)

        for question in session.questions {
            XCTAssertTrue(scopedIDs.contains(question.targetID))
            XCTAssertTrue(question.options.allSatisfy { libraryIDs.contains($0.idiomID) })
            // 另一条范围内目标最多占一个干扰位，其余干扰位必须来自全库范围外。
            XCTAssertGreaterThanOrEqual(question.options.filter { !scopedIDs.contains($0.idiomID) }.count, 2)
        }
    }

    func testBlankAndGloballyDuplicatedWordOrSenseAreIneligible() {
        let records = [
            candidate("valid", word: "  唯一词  ", sense: "  唯一释义  "),
            candidate("blankWord", word: " \n\t ", sense: "独立释义甲"),
            candidate("blankSense", word: "独立词条乙", sense: "\n"),
            candidate("  ", word: "空编号词", sense: "空编号释义"),
            candidate("duplicateIDA", word: "重复编号词甲", sense: "重复编号释义甲"),
            candidate("duplicateIDA", word: "重复编号词乙", sense: "重复编号释义乙"),
            candidate("wordDuplicateA", word: "重复词", sense: "释义甲"),
            candidate("wordDuplicateB", word: " 重复词 ", sense: "释义乙"),
            candidate("senseDuplicateA", word: "词条丙", sense: "重复释义"),
            candidate("senseDuplicateB", word: "词条丁", sense: " 重复释义 ")
        ]

        let eligible = SelfTestRoundBuilder.eligibleCandidates(from: records)
        XCTAssertEqual(eligible, [candidate("valid", word: "唯一词", sense: "唯一释义")])
    }

    func testDuplicateOutsideScopeStillDisqualifiesScopedTarget() {
        let all = [
            candidate("target", word: "同词", sense: "目标释义"),
            candidate("outside", word: " 同词 ", sense: "范围外释义"),
            candidate("other1"), candidate("other2"), candidate("other3")
        ]
        XCTAssertEqual(SelfTestRoundBuilder.eligibleCandidates(from: all).map(\.id), ["other1", "other2", "other3"])

        var random = SeededSelfTestRandom(seed: 1)
        guard case .emptyScope = SelfTestRoundBuilder.make(
            scoped: [all[0]], library: all, randomSource: &random
        ) else {
            return XCTFail("范围内唯一目标因全库重复失去资格，必须显示空范围而不是伪造题目")
        }
    }

    func testEmptyScopeTakesPrecedenceOverInsufficientFullPool() {
        var random = SeededSelfTestRandom(seed: 1)
        guard case .emptyScope = SelfTestRoundBuilder.make(
            scoped: [], library: library(3), randomSource: &random
        ) else {
            return XCTFail("空范围优先于候选项不足")
        }
    }

    func testFullEligiblePoolBelowFourIsInsufficientEvenWithScopedTarget() {
        var random = SeededSelfTestRandom(seed: 1)
        guard case .insufficientCandidates = SelfTestRoundBuilder.make(
            scoped: [candidate("i0")], library: library(3), randomSource: &random
        ) else {
            return XCTFail("全库不足四条不得伪造四选一")
        }
    }

    func testExactlyFourEligibleRecordsCanBuildEveryQuestion() throws {
        let all = library(4)
        let session = try makeSession(scoped: [all[0]], library: all)
        XCTAssertEqual(session.questions.count, 10)
        XCTAssertTrue(session.questions.allSatisfy { $0.options.count == 4 })
    }

    func testSelectionGradesByTargetIDLocksOptionsAndGuardsNext() throws {
        let session = try makeSession(scoped: library(1), library: library(5))
        var updated = session
        let first = try XCTUnwrap(updated.current)
        let wrong = try XCTUnwrap(first.options.first { $0.idiomID != first.targetID })

        XCTAssertFalse(updated.next(), "未判分不能下一题")
        XCTAssertTrue(updated.select(wrong.idiomID))
        XCTAssertEqual(updated.selectedOptionID, wrong.idiomID)
        XCTAssertEqual(updated.correctCount, 0)
        XCTAssertFalse(updated.select(first.targetID), "判分后重复点击不得改答案")
        XCTAssertEqual(updated.selectedOptionID, wrong.idiomID)
        XCTAssertTrue(updated.next())
        XCTAssertEqual(updated.index, 1)
        XCTAssertNil(updated.selectedOptionID)

        let second = try XCTUnwrap(updated.current)
        XCTAssertTrue(updated.select(second.targetID))
        XCTAssertEqual(updated.correctCount, 1)
    }

    func testOnlyFinalManualNextCreatesSummaryWithTenQuestionCounts() throws {
        var session = try makeSession(scoped: library(3), library: library(7))
        for index in 0..<10 {
            let question = try XCTUnwrap(session.current)
            let optionID = index < 7
                ? question.targetID
                : try XCTUnwrap(question.options.first { $0.idiomID != question.targetID }).idiomID
            XCTAssertTrue(session.select(optionID))
            XCTAssertNil(session.summary, "判分后仍停留本题，不能提前出现小结")
            XCTAssertTrue(session.next())
        }
        XCTAssertEqual(session.summary, SelfTestSummary(total: 10, correct: 7, wrong: 3))
        XCTAssertTrue(session.isComplete)
        XCTAssertNil(session.current)
        XCTAssertFalse(session.next())
    }

    func testSeededRandomnessIsReproducibleButChangesRoundOrder() throws {
        let all = library(10)
        let first = try makeSession(scoped: all, library: all, seed: 11)
        let same = try makeSession(scoped: all, library: all, seed: 11)
        let different = try makeSession(scoped: all, library: all, seed: 12)
        XCTAssertEqual(first.questions, same.questions)
        XCTAssertNotEqual(first.questions.map(\.targetID), different.questions.map(\.targetID))
    }
}
