import CangyuData
import GRDB
import XCTest

/// idiom_fts（GRDB FTS5）搜索四输入 + 重建 + 空结果 + 用户表不受影响（规范 §4.2 / §4.6）。
/// 用 GRDB 内存库 DatabaseQueue()，macOS CI 的 xcodebuild test 执行。样例对齐 idioms.sample.json。
final class IdiomSearchTests: XCTestCase {
    private var queue: DatabaseQueue!

    override func setUpWithError() throws {
        queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        try queue.write { db in
            try insertSampleIdioms(db)
            try IdiomSearch.rebuildIdiomFts(db)
        }
    }

    override func tearDownWithError() throws {
        queue = nil
    }

    func testSearchFullIdiom() throws {
        let result = try queue.read { db in try IdiomSearch.searchIdioms(db, query: "按部就班") }
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result.first?.word, "按部就班")
    }

    func testSearchSingleChar() throws {
        let result = try queue.read { db in try IdiomSearch.searchIdioms(db, query: "按") }
        XCTAssertTrue(result.contains { $0.word == "按部就班" })
    }

    func testSingleCharFtsWorksWithoutLike() throws {
        let result = try queue.read { db in
            try IdiomRecord.fetchAll(
                db,
                sql: """
                    SELECT idiom.* FROM idiom
                    JOIN idiom_fts ON idiom.id = idiom_fts.idiomid
                    WHERE idiom_fts MATCH ? ORDER BY idiom.word
                    """,
                arguments: ["word:\"按\"* OR sense:\"按\""]
            )
        }
        XCTAssertEqual(result.map(\.word), ["按部就班"])
    }

    func testSearchFullPinyin() throws {
        let result = try queue.read { db in try IdiomSearch.searchIdioms(db, query: "anbujiuban") }
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result.first?.word, "按部就班")
    }

    func testSearchPinyinPrefix() throws {
        let result = try queue.read { db in try IdiomSearch.searchIdioms(db, query: "anbu") }
        XCTAssertEqual(result.first?.word, "按部就班")
    }

    func testSearchSpecialCharacters() throws {
        for query in ["\u{0000}", "a\u{0000}b", "\"", "*", "OR", "a\"b"] {
            XCTAssertTrue(try queue.read { db in
                try IdiomSearch.searchIdioms(db, query: query)
            }.isEmpty)
        }
    }

    func testRebuildRollsBackWithContentTransaction() throws {
        XCTAssertThrowsError(try queue.write { db in
            try db.execute(sql: "UPDATE idiom SET word = '临时词条' WHERE word = '按部就班'")
            try IdiomSearch.rebuildIdiomFts(db)
            throw NSError(domain: "RollbackTest", code: 1)
        })
        let result = try queue.read { db in try IdiomSearch.searchIdioms(db, query: "按部就班") }
        XCTAssertEqual(result.first?.word, "按部就班")
        XCTAssertTrue(try queue.read { db in
            try IdiomSearch.searchIdioms(db, query: "临时词条")
        }.isEmpty)
    }

    func testSearchFullPinyinWithSpaces() throws {
        let result = try queue.read { db in try IdiomSearch.searchIdioms(db, query: "an bu jiu ban") }
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result.first?.word, "按部就班")
    }

    func testSearchInitials() throws {
        let abjb = try queue.read { db in try IdiomSearch.searchIdioms(db, query: "abjb") }
        XCTAssertEqual(abjb.first?.word, "按部就班")
        let bcxc = try queue.read { db in try IdiomSearch.searchIdioms(db, query: "bcxc") }
        XCTAssertEqual(bcxc.first?.word, "别出心裁")
    }

    func testSearchInitialsPrefix() throws {
        let result = try queue.read { db in try IdiomSearch.searchIdioms(db, query: "ab") }
        XCTAssertTrue(result.contains { $0.word == "按部就班" })
    }

    func testSearchReturnsEmptyForNoMatch() throws {
        XCTAssertTrue(try queue.read { db in try IdiomSearch.searchIdioms(db, query: "zzzz") }.isEmpty)
        XCTAssertTrue(try queue.read { db in try IdiomSearch.searchIdioms(db, query: "") }.isEmpty)
        XCTAssertTrue(try queue.read { db in try IdiomSearch.searchIdioms(db, query: "不存在") }.isEmpty)
    }

    func testRebuildPopulatesFts() throws {
        XCTAssertEqual(try count("idiom_fts"), 3)
        XCTAssertEqual(try count("idiom"), 3)
    }

    func testRebuildIsIdempotent() throws {
        try queue.write { db in try IdiomSearch.rebuildIdiomFts(db) }
        XCTAssertEqual(try count("idiom_fts"), 3)
        let result = try queue.read { db in try IdiomSearch.searchIdioms(db, query: "abjb") }
        XCTAssertEqual(result.first?.word, "按部就班")
    }

    func testRebuildLeavesUserTablesUntouched() throws {
        try queue.write { db in
            try db.execute(sql: "INSERT INTO settings(key, value) VALUES('theme', 'light')")
            try db.execute(sql: """
                INSERT INTO idiom_state(idiomId, status, favorite)
                VALUES('11111111-1111-4111-8111-111111111111', '学过', 0)
                """)
            try db.execute(sql: """
                INSERT INTO mistake(id, stem, optionA, optionB, optionC, optionD, state, createdAt)
                VALUES('m1', 's', 'a', 'b', 'c', 'd', '待复习', '2026-01-01T00:00:00Z')
                """)
            try IdiomSearch.rebuildIdiomFts(db)
        }
        XCTAssertEqual(try count("settings"), 1)
        XCTAssertEqual(try count("idiom_state"), 1)
        XCTAssertEqual(try count("mistake"), 1)
        XCTAssertEqual(try count("idiom_fts"), 3)
    }

    private func count(_ table: String) throws -> Int {
        return try queue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") ?? 0
        }
    }

    private func insertSampleIdioms(_ db: Database) throws {
        let sql = """
            INSERT INTO idiom(id, word, pinyin, initials, sense, color, frequency)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """
        let rows: [StatementArguments] = [
            ["11111111-1111-4111-8111-111111111111", "按部就班", "an bu jiu ban", "abjb",
             "按：依照；部、班：门类与次序。原指安排事务依照一定条理，后多指照老规矩办事，缺乏变通。", "贬义", 5],
            ["22222222-2222-4222-8222-222222222222", "别出心裁", "bie chu xin cai", "bcxc",
             "心裁：出于自心的创造与裁断。指想出的办法独创一格，与众不同。", "褒义", 4],
            ["33333333-3333-4333-8333-333333333333", "墨守成规", "mo shou cheng gui", "mscg",
             "墨守：战国时墨翟善于守城，后指固执旧法；成规：既定的规则。指固执旧法，一成不变。", "贬义", 4]
        ]
        for row in rows {
            try db.execute(sql: sql, arguments: row)
        }
    }
}
