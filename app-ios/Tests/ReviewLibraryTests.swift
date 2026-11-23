import CangyuData
import GRDB
import XCTest

/// 复习队列与结果落库（开发规范第 8.2 节）：过滤 / 排序 / 事实源兜底 / 回写事务。
final class ReviewLibraryTests: XCTestCase {
    func testQueueFiltersPendingAndSortsByCreatedAtDesc() throws {
        let queue = try seededQueue()
        try queue.read { db in
            let items = try ReviewLibrary.queue(db)
            XCTAssertEqual(items.map(\.id), ["m3", "m2", "m1", "m5"])
            XCTAssertEqual(items.map(\.createdAt), [
                "2026-11-16T00:00:00Z", "2026-11-15T00:00:00Z",
                "2026-11-14T00:00:00Z", "2026-11-01T00:00:00Z"
            ])
        }
    }

    func testQueueIdiomFilterMatchesAnyLinkedIdiom() throws {
        let queue = try seededQueue()
        try queue.read { db in
            // m1 同时归属 i1 / i2，两边的单成语队列都应含它（命中任一勾选成语即入队）。
            XCTAssertEqual(try ReviewLibrary.queue(db, idiomID: "i1").map(\.id), ["m2", "m1"])
            XCTAssertEqual(try ReviewLibrary.queue(db, idiomID: "i2").map(\.id), ["m3", "m1", "m5"])
        }
    }

    func testQueuePrefersUserExampleContentAndFallsBackToSnapshot() throws {
        let queue = try seededQueue()
        try queue.read { db in
            let items = try ReviewLibrary.queue(db)
            let byID = Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
            // 事实源：e1 选项齐全、myAnswer = B，快照列被 COALESCE 掉。
            let factSourced = try XCTUnwrap(byID["m1"])
            XCTAssertEqual(factSourced.stem, "录入题事实源题干")
            XCTAssertEqual(factSourced.options, ["下列", "揠苗助长", nil, "缘木求鱼"])
            XCTAssertEqual(factSourced.myAnswer, "B")
            // 快照兜底：m3 无对应录入题，正文取 mistake 快照；非法 myAnswer 归 nil。
            let historical = try XCTUnwrap(byID["m3"])
            XCTAssertEqual(historical.stem, "历史导入的错题，没有对应录入题")
            XCTAssertEqual(historical.options, ["甲", nil, nil, nil])
            XCTAssertNil(historical.myAnswer)
            // 事实源空串视同缺项：e2 未录选项，m2 选项全 nil。
            let withoutOptions = try XCTUnwrap(byID["m2"])
            XCTAssertEqual(withoutOptions.options, [nil, nil, nil, nil])
            XCTAssertNil(withoutOptions.myAnswer)
        }
    }

    func testPendingCountPerIdiom() throws {
        let queue = try seededQueue()
        try queue.read { db in
            XCTAssertEqual(try ReviewLibrary.pendingCount(db, idiomID: "i1"), 2)
            XCTAssertEqual(try ReviewLibrary.pendingCount(db, idiomID: "i2"), 3)
            XCTAssertEqual(try ReviewLibrary.pendingCount(db, idiomID: "missing"), 0)
        }
    }

    func testIdiomsInRoundDeduplicatesAndSortsByWord() throws {
        let queue = try seededQueue()
        try queue.read { db in
            let idioms = try ReviewLibrary.idiomsInRound(db, mistakeIDs: ["m1", "m2", "m1"])
            XCTAssertEqual(idioms.map(\.word), ["按部就班", "墨守成规"])
            XCTAssertEqual(try ReviewLibrary.idiomsInRound(db, mistakeIDs: []), [])
        }
    }

    func testRecordWritesReviewLogAndUpdatesMistakeWithoutStateChange() throws {
        let queue = try seededQueue()
        try queue.write { db in
            try ReviewLibrary.record(db, mistakeID: "m1", result: ReviewLibrary.resultRight,
                                     reviewedAt: "2026-11-23T00:00:00Z")
            try ReviewLibrary.record(db, mistakeID: "m1", result: ReviewLibrary.resultWrong,
                                     reviewedAt: "2026-11-23T01:00:00Z")
            let logCount = try Int.fetchOne(
                db, sql: "SELECT COUNT(*) FROM review_log WHERE mistakeId = 'm1'") ?? 0
            XCTAssertEqual(logCount, 2)
            let results = try String.fetchAll(
                db, sql: "SELECT result FROM review_log WHERE mistakeId = 'm1' ORDER BY at")
            XCTAssertEqual(results, ["对", "错"])
            let reviewCount = try Int.fetchOne(
                db, sql: "SELECT reviewCount FROM mistake WHERE id = 'm1'") ?? -1
            let lastAt = try String.fetchOne(
                db, sql: "SELECT lastReviewedAt FROM mistake WHERE id = 'm1'")
            let state = try String.fetchOne(
                db, sql: "SELECT state FROM mistake WHERE id = 'm1'")
            XCTAssertEqual(reviewCount, 2)
            XCTAssertEqual(lastAt, "2026-11-23T01:00:00Z")
            XCTAssertEqual(state, "待复习")
        }
    }

    func testRecordThrowsForMissingMistakeAndLeavesNoRow() throws {
        let queue = try seededQueue()
        try queue.write { db in
            XCTAssertThrowsError(try ReviewLibrary.record(
                db, mistakeID: "gone", result: ReviewLibrary.resultRight,
                reviewedAt: "2026-11-23T00:00:00Z"
            )) { error in
                XCTAssertEqual(error as? ReviewLibrary.ReviewError, .mistakeNotFound("gone"))
            }
            let logCount = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM review_log") ?? 0
            XCTAssertEqual(logCount, 0)
        }
    }

    /// 固定数据集：两条成语、两条录入题、五条错题（含无录入题的历史行与已掌握行）。
    private func seededQueue() throws -> DatabaseQueue {
        let queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        try queue.write { db in
            let idioms = [("i1", "按部就班", "abjb"), ("i2", "墨守成规", "mscg")]
            for (id, word, initials) in idioms {
                try db.execute(sql: """
                    INSERT INTO idiom (id, word, pinyin, initials, sense, color, frequency)
                    VALUES (?, ?, ?, ?, '释义', '褒义', 5)
                    """, arguments: [id, word, "pin yin", initials])
            }
            try db.execute(sql: """
                INSERT INTO user_example (id, stem, optionA, optionB, optionC, optionD,
                                          myAnswer, isMistake, createdAt)
                VALUES ('e1', '录入题事实源题干', '下列', '揠苗助长', '', '缘木求鱼',
                        'B', 1, '2026-11-14T00:00:00Z')
                """)
            try db.execute(sql: """
                INSERT INTO user_example (id, stem, optionA, optionB, optionC, optionD,
                                          myAnswer, isMistake, createdAt)
                VALUES ('e2', '未录选项的录入题', '', '', '', '', '', 1, '2026-11-15T00:00:00Z')
                """)
            // id、userExampleId、题干快照、选项快照、myAnswer 快照、状态、录入时间
            // swiftlint:disable:next large_tuple
            let mistakes: [(String, String?, String, String, String, String, String)] = [
                ("m1", "e1", "录入题事实源题干", "", "", "待复习", "2026-11-14T00:00:00Z"),
                ("m2", "e2", "未录选项的录入题", "", "", "待复习", "2026-11-15T00:00:00Z"),
                ("m3", nil, "历史导入的错题，没有对应录入题", "甲", "X", "待复习",
                 "2026-11-16T00:00:00Z"),
                ("m4", "e1", "录入题事实源题干", "", "", "已掌握", "2026-11-13T00:00:00Z"),
                ("m5", nil, "最老的历史错题", "", "", "待复习", "2026-11-01T00:00:00Z")
            ]
            for (id, exampleID, stem, optionA, myAnswer, state, createdAt) in mistakes {
                try db.execute(sql: """
                    INSERT INTO mistake (id, userExampleId, stem, optionA, optionB, optionC,
                                         optionD, myAnswer, state, createdAt)
                    VALUES (?, ?, ?, ?, '', '', '', ?, ?, ?)
                    """, arguments: [id, exampleID, stem, optionA, myAnswer, state, createdAt])
            }
            let links = [("m1", "i1"), ("m1", "i2"), ("m2", "i1"), ("m3", "i2"), ("m5", "i2")]
            for (mistakeID, idiomID) in links {
                try db.execute(
                    sql: "INSERT INTO mistake_idiom (mistakeId, idiomId) VALUES (?, ?)",
                    arguments: [mistakeID, idiomID]
                )
            }
        }
        return queue
    }
}
