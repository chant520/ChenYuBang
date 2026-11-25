import CangyuData
import Foundation
import GRDB
import XCTest
@testable import ChenYuBang

/// P24 数据口子：范围内投影去重、全库干扰项来源，以及真实一轮自测对用户表零写入。
final class IdiomSelfTestLibraryTests: XCTestCase {
    private var queue: DatabaseQueue!

    override func setUpWithError() throws {
        queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        try queue.write { db in
            for index in 0..<6 {
                try db.execute(
                    sql: """
                        INSERT INTO idiom(id, word, pinyin, initials, sense, color, frequency)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                    arguments: [
                        "i\(index)", "成语\(index)", "chengyu\(index)", "cy\(index)",
                        "释义\(index)", "中性", 0
                    ]
                )
            }
            // 同一成语挂两个 scope，只应返回一行；i4 / i5 仅作全库干扰项。
            try db.execute(
                sql: "INSERT INTO exam_scope(id, type, province, enabled) VALUES ('scope-a', '国考', NULL, 1)"
            )
            try db.execute(
                sql: "INSERT INTO exam_scope(id, type, province, enabled) VALUES ('scope-b', '省考', '广东', 1)"
            )
            for idiomID in ["i0", "i1", "i2"] {
                try db.execute(
                    sql: "INSERT INTO idiom_scope(idiomId, scopeId) VALUES (?, 'scope-a')",
                    arguments: [idiomID]
                )
            }
            try db.execute(sql: "INSERT INTO idiom_scope(idiomId, scopeId) VALUES ('i0', 'scope-b')")
            try db.execute(sql: """
                INSERT INTO idiom_state(idiomId, status, favorite)
                VALUES ('i0', '学过', 1)
                """)
            try db.execute(sql: """
                INSERT INTO mistake(id, stem, optionA, optionB, optionC, optionD, state, createdAt)
                VALUES ('m1', '题干', 'A', 'B', 'C', 'D', '待复习', '2026-11-25T08:00:00Z')
                """)
            try db.execute(sql: """
                INSERT INTO review_log(id, mistakeId, result, at)
                VALUES ('r1', 'm1', '对', '2026-11-25T08:30:00Z')
                """)
        }
    }

    override func tearDownWithError() throws {
        queue = nil
    }

    func testScopedWordSensesAreDistinctAndFullLibraryRemainsUnfiltered() throws {
        let reader = IdiomLibraryReader(queue: queue)
        let scoped = try reader.scopedWordSenses()
        let full = try reader.allIdiomsForMatch()

        XCTAssertEqual(scoped.map(\.id), ["i0", "i1", "i2"])
        XCTAssertEqual(scoped.map(\.word), ["成语0", "成语1", "成语2"])
        XCTAssertEqual(scoped.map(\.sense), ["释义0", "释义1", "释义2"])
        XCTAssertEqual(full.count, 6)
        XCTAssertEqual(Set(full.map(\.id)), Set((0..<6).map { "i\($0)" }))
    }

    func testScopedProjectionIsEmptyWhenScopeHasNoRows() throws {
        try queue.write { db in
            try db.execute(sql: "DELETE FROM idiom_scope")
        }
        XCTAssertTrue(try IdiomLibraryReader(queue: queue).scopedWordSenses().isEmpty)
    }

    @MainActor
    func testCompleteSelfTestRoundDoesNotWriteLearningOrMistakeTables() async throws {
        let before = try userTableSnapshot()
        let model = P24SelfTestViewModel(reader: IdiomLibraryReader(queue: queue), onExit: {})
        await model.load()

        for _ in 0..<10 {
            guard case .question(let state) = model.state else {
                return XCTFail("真实库应能构建 10 题")
            }
            model.pick(state.question.targetID)
            model.next()
        }

        guard case .summary = model.state else {
            return XCTFail("真实库完成一轮后应显示小结")
        }
        XCTAssertEqual(try userTableSnapshot(), before)
    }

    /// 连行数与全部列值一起钉住，避免只查行数时漏掉已有行被改写。
    private func userTableSnapshot() throws -> [String] {
        try queue.read { db in
            let tables = ["idiom_state", "mistake", "review_log"]
            return try tables.flatMap { table -> [String] in
                let rows = try Row.fetchAll(db, sql: "SELECT * FROM \(table) ORDER BY rowid")
                return ["\(table):\(rows.count)"] + rows.map { row in
                    let columns = row.indices.map { index -> String in
                        let name = row.columnNames[index]
                        let value: DatabaseValue = row[index].1
                        let encoded: String
                        switch value {
                        case .null: encoded = "null"
                        case .int64(let number): encoded = String(number)
                        case .double(let number): encoded = String(number)
                        case .string(let text): encoded = text
                        case .blob(let data): encoded = data.base64EncodedString()
                        }
                        return "\(name)=\(encoded)"
                    }
                    return "\(table)|\(columns.joined(separator: "|"))"
                }
            }
        }
    }
}
