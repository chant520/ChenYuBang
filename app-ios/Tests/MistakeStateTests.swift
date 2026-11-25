import CangyuData
import GRDB
import XCTest

/// C9 错题掌握状态：精确更新、字段保护、队列进出、幂等与复习写日志路径。
final class MistakeStateTests: XCTestCase {
    private let idiomA = "11111111-1111-4111-8111-111111111111"
    private let idiomB = "22222222-2222-4222-8222-222222222222"
    private let exampleA = "10000000-0000-4000-8000-000000000001"
    private let exampleB = "10000000-0000-4000-8000-000000000002"
    private let exampleC = "10000000-0000-4000-8000-000000000003"
    private let targetMistake = "20000000-0000-4000-8000-000000000001"
    private let sharedMistake = "20000000-0000-4000-8000-000000000002"
    private let otherMistake = "20000000-0000-4000-8000-000000000003"
    private let otherIdiomMistake = "20000000-0000-4000-8000-000000000004"
    private var queue: DatabaseQueue!
    private var service: ReviewLibraryService!

    override func setUpWithError() throws {
        queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        service = ReviewLibraryService(queue: queue)
        try seedData()
    }

    override func tearDownWithError() throws {
        service = nil
        queue = nil
    }

    func testStateAcceptsOnlyContractValues() {
        XCTAssertEqual(MistakeReviewState.allCases.map(\.rawValue), ["待复习", "已掌握"])
        XCTAssertNil(MistakeReviewState(rawValue: "未学"))
        XCTAssertNil(MistakeReviewState(rawValue: ""))
    }

    func testSetStateUpdatesOnlyTargetAndPreservesAllFields() throws {
        let before = try snapshotAll()
        let targetBefore = try XCTUnwrap(before["mistake"]?.first { ($0["id"] as String) == targetMistake })
        XCTAssertEqual(try service.setState(mistakeID: targetMistake, state: .proficient), .updated)

        let after = try snapshotAll()
        let targetAfter = try XCTUnwrap(after["mistake"]?.first { ($0["id"] as String) == targetMistake })
        XCTAssertEqual(targetAfter["state"] as String?, "已掌握")
        let protectedColumns = [
            "id", "userExampleId", "imagePath", "ocrText", "stem", "optionA", "optionB", "optionC",
            "optionD", "myAnswer", "reason", "tagNames", "examType", "year", "region", "createdAt",
            "lastReviewedAt", "reviewCount"
        ]
        for column in protectedColumns {
            XCTAssertEqual(targetAfter[column] as DatabaseValue?, targetBefore[column] as DatabaseValue?, column)
        }
        for table in ["user_example", "user_example_idiom", "mistake_idiom", "review_log"] {
            XCTAssertEqual(after[table], before[table], table)
        }
        XCTAssertEqual(after["mistake"], before["mistake"]?.filter { ($0["id"] as String) != targetMistake })
    }

    func testMissingIDReturnsNotFoundWithoutInserting() throws {
        let before = try snapshotAll()
        XCTAssertEqual(try service.setState(mistakeID: "missing", state: .proficient), .notFound)
        let after = try snapshotAll()
        XCTAssertEqual(after, before)
        try queue.read { db in
            XCTAssertNil(try Row.fetchOne(db, sql: "SELECT * FROM mistake WHERE id = 'missing'"))
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake"), 4)
        }
    }

    func testRepeatedStateWriteIsIdempotent() throws {
        XCTAssertEqual(try service.setState(mistakeID: targetMistake, state: .proficient), .updated)
        let before = try snapshotAll()
        XCTAssertEqual(try service.setState(mistakeID: targetMistake, state: .proficient), .updated)
        XCTAssertEqual(try snapshotAll(), before)
    }

    func testProficientLeavesPendingQueuesButRemainsInAllMistakes() throws {
        let globalBefore = try service.queue().map(\.id)
        let idiomBefore = try service.queue(idiomID: idiomA).map(\.id)
        XCTAssertEqual(globalBefore, [targetMistake, sharedMistake, otherMistake, otherIdiomMistake])
        XCTAssertEqual(idiomBefore, [targetMistake, sharedMistake, otherMistake])

        XCTAssertEqual(try service.setState(mistakeID: targetMistake, state: .proficient), .updated)
        XCTAssertEqual(try service.queue().map(\.id), [sharedMistake, otherMistake, otherIdiomMistake])
        XCTAssertEqual(try service.queue(idiomID: idiomA).map(\.id), [sharedMistake, otherMistake])
        XCTAssertEqual(try service.pendingCount(idiomID: idiomA), 2)
        let allMistakes = try MistakeLibraryReader(queue: queue).rows(order: .byCreatedAt)
        XCTAssertEqual(allMistakes.map(\.id), globalBefore)
        XCTAssertEqual(allMistakes.first?.state, "已掌握")
        XCTAssertEqual(try MistakeLibraryReader(queue: queue).overview(), MistakeOverview(total: 4, pending: 3))

        XCTAssertEqual(try service.setState(mistakeID: targetMistake, state: .pending), .updated)
        XCTAssertEqual(try service.queue().map(\.id), globalBefore)
        XCTAssertEqual(try service.queue(idiomID: idiomA).map(\.id), idiomBefore)
        XCTAssertEqual(try service.pendingCount(idiomID: idiomA), 3)
    }

    func testReviewRecordDoesNotChangeProficientState() throws {
        XCTAssertEqual(try service.setState(mistakeID: targetMistake, state: .proficient), .updated)
        try service.record(mistakeID: targetMistake, result: ReviewLibrary.resultRight,
                           reviewedAt: "2026-11-25T00:00:00Z")
        try queue.read { db in
            let row = try XCTUnwrap(Row.fetchOne(
                db, sql: "SELECT state, reviewCount, lastReviewedAt FROM mistake WHERE id = ?",
                arguments: [targetMistake]
            ))
            XCTAssertEqual(row["state"] as String?, "已掌握")
            XCTAssertEqual(row["reviewCount"] as Int?, 3)
            XCTAssertEqual(row["lastReviewedAt"] as String?, "2026-11-25T00:00:00Z")
            XCTAssertEqual(try Int.fetchOne(
                db, sql: "SELECT COUNT(*) FROM review_log WHERE mistakeId = ?", arguments: [targetMistake]
            ), 3)
        }
    }

    private func seedData() throws {
        try queue.write { db in
            for idiomID in [idiomA, idiomB] {
                try db.execute(sql: """
                    INSERT INTO idiom(id, word, pinyin, initials, sense, color, frequency)
                    VALUES (?, '按部就班', 'an bu jiu ban', 'abjb', '释义', '贬义', 5)
                    """, arguments: [idiomID])
            }
            try seedExample(db, id: exampleA, stem: "目标录入题",
                            createdAt: "2026-11-24T00:00:00Z", idiomID: idiomA)
            try seedExample(db, id: exampleB, stem: "其他录入题",
                            createdAt: "2026-11-22T00:00:00Z", idiomID: idiomA)
            try seedExample(db, id: exampleC, stem: "其他成语录入题",
                            createdAt: "2026-11-21T00:00:00Z", idiomID: idiomB)
            try db.execute(sql: """
                INSERT INTO mistake(id, userExampleId, imagePath, ocrText, stem, optionA, optionB,
                                    optionC, optionD, myAnswer, reason, tagNames, examType, year, region,
                                    state, createdAt, lastReviewedAt, reviewCount)
                VALUES (?, ?, 'images/user.jpg', 'OCR 原文', '目标错题', '甲', '乙', '丙', '丁', 'B',
                        '望文生义', '["望文生义"]', '国考', 2026, '中央', '待复习',
                        '2026-11-24T00:00:00Z', '2026-11-24T01:00:00Z', 2)
                """, arguments: [targetMistake, exampleA])
            try seedMistake(db, id: sharedMistake, example: (exampleA, "同一录入题的另一错题"),
                            createdAt: "2026-11-23T00:00:00Z", idiomID: idiomA)
            try seedMistake(db, id: otherMistake, example: (exampleB, "同一成语的其他错题"),
                            createdAt: "2026-11-22T00:00:00Z", idiomID: idiomA)
            try seedMistake(db, id: otherIdiomMistake, example: (exampleC, "其他成语的错题"),
                            createdAt: "2026-11-21T00:00:00Z", idiomID: idiomB)
            try seedReview(db, id: "30000000-0000-4000-8000-000000000001",
                           mistakeID: targetMistake, result: "对")
            try seedReview(db, id: "30000000-0000-4000-8000-000000000002",
                           mistakeID: targetMistake, result: "错")
            try seedReview(db, id: "30000000-0000-4000-8000-000000000003",
                           mistakeID: sharedMistake, result: "错")
        }
    }

    private func seedExample(
        _ db: Database,
        id: String,
        stem: String,
        createdAt: String,
        idiomID: String
    ) throws {
        try db.execute(sql: """
            INSERT INTO user_example(id, imagePath, ocrText, stem, optionA, optionB, optionC,
                                     optionD, myAnswer, isMistake, examType, year, region, createdAt)
            VALUES (?, 'images/user.jpg', 'OCR 原文', ?, '甲', '乙', '丙', '丁', 'B', 1,
                    '国考', 2026, '中央', ?)
            """, arguments: [id, stem, createdAt])
        try db.execute(sql: "INSERT INTO user_example_idiom(userExampleId, idiomId) VALUES (?, ?)",
                       arguments: [id, idiomID])
    }

    private func seedMistake(
        _ db: Database,
        id: String,
        example: (id: String, stem: String),
        createdAt: String,
        idiomID: String
    ) throws {
        try db.execute(sql: """
            INSERT INTO mistake(id, userExampleId, stem, optionA, optionB, optionC, optionD,
                                state, createdAt, reviewCount)
            VALUES (?, ?, ?, '甲', '乙', '丙', '丁', '待复习', ?, 0)
            """, arguments: [id, example.id, example.stem, createdAt])
        try db.execute(sql: "INSERT INTO mistake_idiom(mistakeId, idiomId) VALUES (?, ?)",
                       arguments: [id, idiomID])
    }

    private func seedReview(_ db: Database, id: String, mistakeID: String, result: String) throws {
        try db.execute(sql: "INSERT INTO review_log(id, mistakeId, result, at) VALUES (?, ?, ?, ?)",
                       arguments: [id, mistakeID, result, "2026-11-24T01:00:00Z"])
    }

    private func snapshotAll() throws -> [String: [Row]] {
        try queue.read { db in
            var output: [String: [Row]] = [:]
            for table in ["mistake", "user_example", "user_example_idiom", "mistake_idiom", "review_log"] {
                output[table] = try Row.fetchAll(db, sql: "SELECT * FROM \(table) ORDER BY 1, 2")
            }
            return output
        }
    }
}
