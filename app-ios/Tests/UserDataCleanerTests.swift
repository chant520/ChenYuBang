@testable import CangyuData
import Foundation
import GRDB
import XCTest

final class UserDataCleanerTests: PackDatabaseTestCase {
    private let clearedTables = ["review_log", "user_example_idiom", "mistake_idiom", "user_example",
                                 "mistake", "idiom_state"]
    private let contentTables = ["idiom", "example", "exam_scope", "idiom_scope", "example_idiom", "idiom_fts"]

    func testClearDeletesOnlyUserRowsAndWritesTimestampWithoutOrphans() throws {
        try seedData()
        let contentBefore = try snapshot(contentTables)
        let before = Date()
        let clearedAt = try UserDataCleaner(queue: queue).clear()
        let after = Date()

        XCTAssertGreaterThanOrEqual(clearedAt, before)
        XCTAssertLessThanOrEqual(clearedAt, after)
        for table in clearedTables { XCTAssertEqual(try count(table), 0, table) }
        XCTAssertEqual(try count("exam_scope"), 2)
        XCTAssertEqual(try snapshot(contentTables), contentBefore)
        let settings = try queue.read { db in
            try Row.fetchAll(db, sql: "SELECT key, value FROM settings ORDER BY key")
        }
        XCTAssertEqual(settings.count, 3)
        let values = Dictionary(uniqueKeysWithValues: settings.map { ($0["key"] as String, $0["value"] as String) })
        XCTAssertEqual(values["theme"], "dark")
        XCTAssertEqual(values["keep"], "yes")
        XCTAssertEqual(values["lastClearedAt"], Backup.stamp(clearedAt))
        let storedDate = try XCTUnwrap(ISO8601DateFormatter().date(from: try XCTUnwrap(values["lastClearedAt"])))
        XCTAssertLessThan(abs(storedDate.timeIntervalSince(clearedAt)), 1)
        try assertNoOrphans()
    }

    private func seedData() throws {
        try queue.write { db in
            for (id, word) in [("i1", "按部就班"), ("i2", "墨守成规")] {
                try db.execute(sql: """
                    INSERT INTO idiom(id, word, pinyin, initials, sense, color, frequency)
                    VALUES (?, ?, 'pin yin', 'initials', '释义', '褒义', 1)
                    """, arguments: [id, word])
            }
            try db.execute(sql: """
                INSERT INTO example(id, stem, optionA, optionB, optionC, optionD, answer, explain,
                                    examType, year, region, questionType)
                VALUES ('example-1', '内容例题', '甲', '乙', '丙', '丁', 'A', '解析',
                        '国考', 2026, '中央', '片段阅读')
                """)
            try db.execute(sql: """
                INSERT INTO example_idiom(exampleId, idiomId) VALUES ('example-1', 'i1')
                """)
            try db.execute(sql: """
                INSERT INTO idiom_fts(idiomid, word, pinyin, initials, sense)
                SELECT id, word, pinyin, initials, sense FROM idiom
                """)
            try db.execute(sql: """
                INSERT INTO exam_scope(id, type, province, enabled)
                VALUES ('scope-1', '国考', NULL, 1), ('scope-2', '省考', '湖北', 0)
                """)
            try db.execute(sql: "INSERT INTO idiom_scope(idiomId, scopeId) VALUES ('i1', 'scope-1')")
            try db.execute(sql: """
                INSERT INTO user_example(id, imagePath, stem, isMistake, createdAt)
                VALUES ('example-user-1', 'images/user.jpg', '用户录入题', 1, '2026-11-20T00:00:00Z'),
                       ('example-user-2', NULL, '未标错录入题', 0, '2026-11-20T00:00:00Z')
                """)
            try db.execute(sql: """
                INSERT INTO user_example_idiom(userExampleId, idiomId)
                VALUES ('example-user-1', 'i1'), ('example-user-1', 'i2'), ('example-user-2', 'i2')
                """)
            try db.execute(sql: """
                INSERT INTO mistake(id, userExampleId, imagePath, stem, optionA, optionB, optionC, optionD,
                                    state, createdAt, reviewCount)
                VALUES ('mistake-1', 'example-user-1', 'images/user.jpg', '错题题干',
                        '甲', '乙', '丙', '丁', '待复习', '2026-11-20T00:00:00Z', 1),
                       ('mistake-2', NULL, NULL, '历史错题', '甲', '乙', '丙', '丁',
                        '已掌握', '2026-11-19T00:00:00Z', 0)
                """)
            try db.execute(sql: """
                INSERT INTO mistake_idiom(mistakeId, idiomId)
                VALUES ('mistake-1', 'i1'), ('mistake-1', 'i2'), ('mistake-2', 'i2')
                """)
            try db.execute(sql: """
                INSERT INTO review_log(id, mistakeId, result, at)
                VALUES ('review-1', 'mistake-1', '错', '2026-11-21T00:00:00Z'),
                       ('review-2', 'mistake-2', '对', '2026-11-21T00:00:00Z')
                """)
            try db.execute(sql: """
                INSERT INTO idiom_state(idiomId, status, favorite) VALUES ('i1', '学过', 1), ('i2', '未学', 0)
                """)
            try db.execute(sql: """
                INSERT INTO settings(key, value) VALUES ('theme', 'dark'), ('keep', 'yes')
                """)
        }
        for table in clearedTables { XCTAssertGreaterThan(try count(table), 0, table) }
    }

    private func assertNoOrphans() throws {
        let queries = [
            "SELECT COUNT(*) FROM review_log r LEFT JOIN mistake m ON m.id = r.mistakeId WHERE m.id IS NULL",
            "SELECT COUNT(*) FROM mistake_idiom r LEFT JOIN mistake m ON m.id = r.mistakeId WHERE m.id IS NULL",
            "SELECT COUNT(*) FROM mistake_idiom r LEFT JOIN idiom i ON i.id = r.idiomId WHERE i.id IS NULL",
            """
            SELECT COUNT(*) FROM user_example_idiom r
            LEFT JOIN user_example e ON e.id = r.userExampleId WHERE e.id IS NULL
            """,
            "SELECT COUNT(*) FROM user_example_idiom r LEFT JOIN idiom i ON i.id = r.idiomId WHERE i.id IS NULL",
            """
            SELECT COUNT(*) FROM mistake m
            LEFT JOIN user_example e ON e.id = m.userExampleId
            WHERE m.userExampleId IS NOT NULL AND e.id IS NULL
            """,
            "SELECT COUNT(*) FROM idiom_state s LEFT JOIN idiom i ON i.id = s.idiomId WHERE i.id IS NULL"
        ]
        try queue.read { db in
            for query in queries {
                XCTAssertEqual(try Int.fetchOne(db, sql: query), 0, query)
            }
        }
    }
}
