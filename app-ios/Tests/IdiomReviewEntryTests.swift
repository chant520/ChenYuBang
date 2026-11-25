import CangyuData
import GRDB
import XCTest
@testable import ChenYuBang

/// P05「复习这个成语」入口（决策 H，工作包 3.1）：可点性只取该成语下 `state=待复习` 的错题数。
/// 从 `ExampleDetailTests` 拆出以满足 SwiftLint `type_body_length`（250 行）；口径见开发规范第 8.2 节。
final class IdiomReviewEntryTests: XCTestCase {
    private var queue: DatabaseQueue!

    override func setUpWithError() throws {
        queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
    }

    override func tearDownWithError() throws {
        queue = nil
    }

    /// 「复习这个成语」的可点性只取该成语下 `state=待复习` 的错题数（开发规范第 8.2 节队列口径）。
    @MainActor
    func testIdiomDetailViewModelEnablesReviewWhenIdiomHasPendingMistake() async throws {
        let ref = try insertIdiom(id: "idiom-anbu", word: "按部就班")
        try insertMistake(id: "mk-1", idiomID: ref.id, state: "待复习")
        let model = IdiomDetailViewModel(
            reader: IdiomLibraryReader(queue: queue),
            examplesReader: ExampleLibraryReader(queue: queue),
            reviewCounter: ReviewLibraryService(queue: queue)
        )
        await model.load(ref.id)
        XCTAssertEqual(model.pendingReviewCount, 1)
    }

    /// 已掌握的错题不入队；别的成语下的待复习错题也不算在本成语上（按 `mistake_idiom` 判定）。
    @MainActor
    func testIdiomDetailViewModelKeepsReviewDisabledForLearnedAndForeignMistakes() async throws {
        let ref = try insertIdiom(id: "idiom-anbu", word: "按部就班")
        let other = try insertIdiom(id: "idiom-moshou", word: "墨守成规")
        try insertMistake(id: "mk-1", idiomID: ref.id, state: "已掌握")
        try insertMistake(id: "mk-2", idiomID: other.id, state: "待复习")
        let model = IdiomDetailViewModel(
            reader: IdiomLibraryReader(queue: queue),
            examplesReader: ExampleLibraryReader(queue: queue),
            reviewCounter: ReviewLibraryService(queue: queue)
        )
        await model.load(ref.id)
        XCTAssertEqual(model.pendingReviewCount, 0, "保持置灰并给一句说明，不出现点了没反应的入口")
    }

    /// 未注入计数器时保持置灰（缺数据不假装可复习）。
    @MainActor
    func testIdiomDetailViewModelWithoutCounterKeepsReviewDisabled() async throws {
        let ref = try insertIdiom(id: "idiom-anbu", word: "按部就班")
        try insertMistake(id: "mk-1", idiomID: ref.id, state: "待复习")
        let model = IdiomDetailViewModel(
            reader: IdiomLibraryReader(queue: queue),
            examplesReader: ExampleLibraryReader(queue: queue)
        )
        await model.load(ref.id)
        XCTAssertEqual(model.pendingReviewCount, 0)
    }

    /// C9 状态回流：P10 切已掌握后 P05 重载为 0；切回待复习后计数与最新队列同时恢复。
    @MainActor
    func testIdiomDetailReloadReflectsReviewStateRoundTrip() async throws {
        let ref = try insertIdiom(id: "idiom-anbu", word: "按部就班")
        try insertMistake(id: "mk-1", idiomID: ref.id, state: "待复习")
        let service = ReviewLibraryService(queue: queue)
        let model = IdiomDetailViewModel(
            reader: IdiomLibraryReader(queue: queue),
            examplesReader: ExampleLibraryReader(queue: queue),
            reviewCounter: service
        )
        await model.load(ref.id)
        XCTAssertEqual(model.pendingReviewCount, 1)

        XCTAssertEqual(try service.setState(mistakeID: "mk-1", state: .proficient), .updated)
        await model.load(ref.id)
        XCTAssertEqual(model.pendingReviewCount, 0)
        XCTAssertTrue(try service.queue(idiomID: ref.id).isEmpty)

        XCTAssertEqual(try service.setState(mistakeID: "mk-1", state: .pending), .updated)
        await model.load(ref.id)
        XCTAssertEqual(model.pendingReviewCount, 1)
        XCTAssertEqual(try service.queue(idiomID: ref.id).map(\.id), ["mk-1"])
    }

    // MARK: - Fixtures

    @discardableResult
    private func insertIdiom(id: String, word: String) throws -> IdiomWordRef {
        try queue.write { db in
            try db.execute(
                sql: """
                    INSERT INTO idiom
                        (id, word, pinyin, initials, sense, origin, color, pitfalls, synonyms, antonyms, frequency)
                    VALUES (?, ?, 'pinyin', 'PY', '释义', NULL, '中', '[]', '[]', '[]', 1)
                    """,
                arguments: [id, word]
            )
        }
        return IdiomWordRef(id: id, word: word)
    }

    /// 库内直接写一条错题及其 `mistake_idiom` 归属：复习入口只看错题状态，不走拍照录入链路。
    private func insertMistake(id: String, idiomID: String, state: String) throws {
        try queue.write { db in
            try db.execute(
                sql: """
                    INSERT INTO mistake (id, userExampleId, stem, optionA, optionB, optionC,
                                         optionD, myAnswer, state, createdAt)
                    VALUES (?, NULL, '填入横线处最恰当的一项：改革不能______', '甲', '乙', '丙', '丁',
                            'A', ?, '2026-11-14T00:00:00Z')
                    """,
                arguments: [id, state]
            )
            try db.execute(
                sql: "INSERT INTO mistake_idiom (mistakeId, idiomId) VALUES (?, ?)",
                arguments: [id, idiomID]
            )
        }
    }
}
