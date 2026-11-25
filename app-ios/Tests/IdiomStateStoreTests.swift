@testable import CangyuData
import Foundation
import GRDB
import XCTest

/// A6 / A7 数据层前置：默认读投影、字段保护、首次写入默认值、显式值与幂等。
final class IdiomStateStoreTests: XCTestCase {
    private let idiomA = "11111111-1111-4111-8111-111111111111"
    private let idiomB = "22222222-2222-4222-8222-222222222222"
    private let idiomC = "33333333-3333-4333-8333-333333333333"
    private var queue: DatabaseQueue!
    private var store: IdiomStateStore!

    override func setUpWithError() throws {
        queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        store = IdiomStateStore(queue: queue)
        try queue.write { db in
            for idiomID in [idiomA, idiomB, idiomC] {
                try db.execute(sql: """
                    INSERT INTO idiom(id, word, pinyin, initials, sense, color, frequency)
                    VALUES (?, '按部就班', 'an bu jiu ban', 'abjb', '释义', '贬义', 5)
                    """, arguments: [idiomID])
            }
        }
    }

    override func tearDownWithError() throws {
        store = nil
        queue = nil
    }

    func testMissingStateProjectsDefaultsWithoutWriting() throws {
        let state = try store.state(idiomID: idiomA)
        XCTAssertEqual(state, IdiomLearningState(idiomID: idiomA, status: .notStarted, favorite: false))
        try queue.read { db in
            XCTAssertNil(try Row.fetchOne(
                db, sql: "SELECT * FROM idiom_state WHERE idiomId = ?", arguments: [idiomA]
            ))
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM idiom_state"), 0)
        }
    }

    func testStatusAcceptsOnlyContractValues() {
        XCTAssertEqual(IdiomLearningStatus.allCases.map(\.rawValue), ["未学", "学过", "已掌握"])
        XCTAssertNil(IdiomLearningStatus(rawValue: ""))
        XCTAssertNil(IdiomLearningStatus(rawValue: "已学"))
        XCTAssertNil(IdiomLearningStatus(rawValue: "待复习"))
    }

    func testSetStatusPreservesExistingFavorite() throws {
        try queue.write { db in
            try db.execute(sql: "INSERT INTO idiom_state(idiomId, status, favorite) VALUES (?, '学过', 1)",
                           arguments: [idiomA])
        }
        try store.setStatus(idiomID: idiomA, status: .mastered)
        try assertRawState(idiomA, status: "已掌握", favorite: true)
    }

    func testSetFavoritePreservesExistingStatus() throws {
        try queue.write { db in
            try db.execute(sql: "INSERT INTO idiom_state(idiomId, status, favorite) VALUES (?, '已掌握', 0)",
                           arguments: [idiomA])
        }
        try store.setFavorite(idiomID: idiomA, favorite: true)
        try assertRawState(idiomA, status: "已掌握", favorite: true)
    }

    func testFirstStatusWriteInsertsFavoriteFalse() throws {
        try store.setStatus(idiomID: idiomA, status: .studied)
        try assertRawState(idiomA, status: "学过", favorite: false)
        XCTAssertEqual(try count("idiom_state"), 1)
    }

    func testFirstFavoriteWriteInsertsNotStartedStatus() throws {
        try store.setFavorite(idiomID: idiomA, favorite: true)
        try assertRawState(idiomA, status: "未学", favorite: true)
        XCTAssertEqual(try count("idiom_state"), 1)
    }

    func testFirstNotStartedStatusWriteInsertsFavoriteFalse() throws {
        try store.setStatus(idiomID: idiomA, status: .notStarted)
        try assertRawState(idiomA, status: "未学", favorite: false)
        XCTAssertEqual(try count("idiom_state"), 1)
    }

    func testFirstFavoriteFalseWriteInsertsNotStartedStatus() throws {
        try store.setFavorite(idiomID: idiomA, favorite: false)
        try assertRawState(idiomA, status: "未学", favorite: false)
        XCTAssertEqual(try count("idiom_state"), 1)
    }

    func testExplicitNotStartedKeepsRowAndFavorite() throws {
        try queue.write { db in
            try db.execute(sql: "INSERT INTO idiom_state(idiomId, status, favorite) VALUES (?, '已掌握', 1)",
                           arguments: [idiomA])
        }
        try store.setStatus(idiomID: idiomA, status: .notStarted)
        try assertRawState(idiomA, status: "未学", favorite: true)
        XCTAssertEqual(try count("idiom_state"), 1)
    }

    func testCancelFavoriteKeepsRowAndStatus() throws {
        try queue.write { db in
            try db.execute(sql: "INSERT INTO idiom_state(idiomId, status, favorite) VALUES (?, '学过', 1)",
                           arguments: [idiomA])
        }
        try store.setFavorite(idiomID: idiomA, favorite: false)
        try assertRawState(idiomA, status: "学过", favorite: false)
        XCTAssertEqual(try count("idiom_state"), 1)
    }

    func testRepeatedWritesAreIdempotent() throws {
        try store.setStatus(idiomID: idiomA, status: .studied)
        try store.setFavorite(idiomID: idiomA, favorite: true)
        let before = try queue.read { db in
            try Row.fetchAll(db, sql: "SELECT * FROM idiom_state ORDER BY idiomId")
        }
        try store.setStatus(idiomID: idiomA, status: .studied)
        try store.setFavorite(idiomID: idiomA, favorite: true)
        let after = try queue.read { db in
            try Row.fetchAll(db, sql: "SELECT * FROM idiom_state ORDER BY idiomId")
        }
        XCTAssertEqual(after, before)
        XCTAssertEqual(after.count, 1)
        try assertRawState(idiomA, status: "学过", favorite: true)
    }

    func testWrittenStateRoundTripsBackupAndClear() throws {
        try store.setStatus(idiomID: idiomA, status: .mastered)
        try store.setFavorite(idiomID: idiomA, favorite: true)
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("idiom-state-test-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }

        let service = BackupService(queue: queue)
        let exported = try service.export(to: directory)
        let validated = try service.validate(Data(contentsOf: exported.url))
        let saved = try XCTUnwrap(validated.payload.idiomStates.first)
        XCTAssertEqual(saved.idiomId, idiomA)
        XCTAssertEqual(saved.status, "已掌握")
        XCTAssertTrue(saved.favorite)

        _ = try UserDataCleaner(queue: queue).clear()
        XCTAssertEqual(try count("idiom_state"), 0)
        XCTAssertEqual(try count("idiom"), 3)
    }

    private func assertRawState(
        _ idiomID: String,
        status: String,
        favorite: Bool,
        file: StaticString = #filePath,
        line: UInt = #line
    ) throws {
        try queue.read { db in
            let row = try XCTUnwrap(Row.fetchOne(
                db, sql: "SELECT status, favorite FROM idiom_state WHERE idiomId = ?", arguments: [idiomID]
            ), file: file, line: line)
            XCTAssertEqual(row["status"] as String?, status, file: file, line: line)
            XCTAssertEqual(row["favorite"] as Bool?, favorite, file: file, line: line)
        }
    }

    private func count(_ table: String) throws -> Int {
        try queue.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") ?? -1 }
    }
}
