@testable import CangyuData
import Foundation
import GRDB
import XCTest

final class PackImporterTests: PackDatabaseTestCase {
    private let contentTables = ["idiom", "example", "idiom_scope", "example_idiom", "idiom_fts"]
    private let userTables = [
        "user_example", "user_example_idiom", "mistake", "mistake_idiom",
        "review_log", "idiom_state", "settings", "exam_scope",
    ]

    func testPublishedCountsDigestSearchAndIdempotency() throws {
        let pack = try importer.validate(PackFixture.published())
        XCTAssertEqual(pack.version, "v1.0.0")
        XCTAssertEqual(pack.counts.idioms, 59)
        XCTAssertEqual(pack.counts.examples, 11)
        XCTAssertEqual(pack.sha256, PackFixture.digest)
        XCTAssertEqual(try count("idiom"), 0, "validate must not touch the destination")
        let result = try importer.importPack(pack)
        XCTAssertEqual(result.version, pack.version)
        XCTAssertEqual(result.counts, pack.counts)
        XCTAssertEqual(try count("idiom"), 59)
        XCTAssertEqual(try count("example"), 11)
        XCTAssertEqual(try count("idiom_fts"), 59)
        let original = try snapshot(contentTables)
        _ = try importer.importPack(pack)
        XCTAssertEqual(try snapshot(contentTables), original)
        for query in ["步步为营", "步", "bu bu wei ying", "bbwy"] {
            let results = try queue.read { db in try IdiomSearch.searchIdioms(db, query: query) }
            XCTAssertTrue(results.contains { $0.word == "步步为营" }, query)
        }
    }

    func testAllEightUserTablesAreFieldForFieldUntouched() throws {
        let pack = try importer.validate(PackFixture.published())
        _ = try importer.importPack(pack)
        let fixture = try PackFixture()
        let stateID = try XCTUnwrap(fixture.idioms[0]["id"] as? String)
        let mistakeID = try XCTUnwrap(fixture.idioms[1]["id"] as? String)
        try seedUserTables(stateID: stateID, mistakeID: mistakeID)
        let before = try snapshot(userTables)
        XCTAssertTrue(before.values.allSatisfy { !$0.isEmpty })
        _ = try importer.importPack(pack)
        try importer.refreshScopeLinks(pack)
        XCTAssertEqual(try snapshot(userTables), before)
    }

    func testDeletedReferencedWordsRetainExactRowsWithoutContentLinks() throws {
        let published = try importer.validate(PackFixture.published())
        _ = try importer.importPack(published)
        var fixture = try PackFixture()
        let removed = try fixture.idioms.prefix(3).map { try XCTUnwrap($0["id"] as? String) }
        try seedUserTables(stateID: removed[0], mistakeID: removed[1])
        try importer.refreshScopeLinks(published)
        let users = try snapshot(userTables)
        let retained = try queue.read { db in
            try Row.fetchAll(db, sql: "SELECT * FROM idiom WHERE id IN (?, ?) ORDER BY id",
                             arguments: [removed[0], removed[1]])
        }
        fixture.idioms.removeFirst(3)
        let removedSet = Set(removed)
        fixture.examples = try fixture.examples.compactMap { example in
            var updated = example
            let ids = try XCTUnwrap(example["idiomIds"] as? [String]).filter { !removedSet.contains($0) }
            updated["idiomIds"] = ids
            return ids.isEmpty ? nil : updated
        }
        fixture.manifest["packVersion"] = "v1.0.1"
        let newPack = try importer.validate(fixture.zip())
        _ = try importer.importPack(newPack)
        try importer.refreshScopeLinks(newPack)
        XCTAssertEqual(newPack.counts.idioms, 56)
        XCTAssertEqual(try count("idiom"), 58)
        XCTAssertEqual(try count("idiom_fts"), 56)
        XCTAssertEqual(try snapshot(userTables), users)
        try queue.read { db in
            let after = try Row.fetchAll(db, sql: "SELECT * FROM idiom WHERE id IN (?, ?) ORDER BY id",
                                        arguments: [removed[0], removed[1]])
            XCTAssertEqual(after, retained)
            for row in retained {
                let id: String = row["id"]
                let word: String = row["word"]
                for query in [word, String(word.prefix(1))] {
                    let results = try IdiomSearch.searchIdioms(db, query: query)
                    XCTAssertFalse(results.contains { $0.id == id })
                }
            }
            for id in removed {
                XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM idiom_scope WHERE idiomId = ?",
                                                arguments: [id]), 0)
                XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM idiom_fts WHERE idiomid = ?",
                                                arguments: [id]), 0)
            }
            XCTAssertNil(try String.fetchOne(db, sql: "SELECT id FROM idiom WHERE id = ?", arguments: [removed[2]]))
        }
        XCTAssertThrowsError(try importer.refreshScopeLinks(published))
    }

    func testDestinationTriggerFailureRollsBackContentFTSAndUsers() throws {
        let pack = try importer.validate(PackFixture.published())
        _ = try importer.importPack(pack)
        let fixture = try PackFixture()
        try seedUserTables(stateID: XCTUnwrap(fixture.idioms[0]["id"] as? String),
                           mistakeID: XCTUnwrap(fixture.idioms[1]["id"] as? String))
        try importer.refreshScopeLinks(pack)
        let before = try snapshot(contentTables + userTables)
        var update = fixture
        update.idioms[0]["sense"] = try XCTUnwrap(update.idioms[0]["sense"] as? String) + "\n"
        update.manifest["packVersion"] = "v1.0.1"
        let validated = try importer.validate(update.zip())
        try queue.write { db in
            try db.execute(sql: """
                CREATE TRIGGER pack_test_abort BEFORE INSERT ON idiom_scope
                BEGIN SELECT RAISE(ABORT, 'pack rollback test'); END
                """)
        }
        XCTAssertThrowsError(try importer.importPack(validated))
        XCTAssertEqual(try snapshot(contentTables + userTables), before)
        try queue.write { db in try db.execute(sql: "DROP TRIGGER pack_test_abort") }
        _ = try importer.importPack(validated)
        XCTAssertEqual(try snapshot(userTables), before.filter { userTables.contains($0.key) })
    }

    func testMistakeReferencedWordWithNewUUIDRollsBackAllTables() throws {
        try assertReferencedWordUUIDReplacementRejected(at: 1)
    }

    func testStateReferencedWordWithNewUUIDRollsBackAllTables() throws {
        try assertReferencedWordUUIDReplacementRejected(at: 0)
    }

    private func assertReferencedWordUUIDReplacementRejected(at index: Int) throws {
        let published = try importer.validate(PackFixture.published())
        _ = try importer.importPack(published)
        var fixture = try PackFixture()
        try seedUserTables(stateID: XCTUnwrap(fixture.idioms[0]["id"] as? String),
                           mistakeID: XCTUnwrap(fixture.idioms[1]["id"] as? String))
        try importer.refreshScopeLinks(published)
        let before = try snapshot(contentTables + userTables)
        let originalID = try XCTUnwrap(fixture.idioms[index]["id"] as? String)
        let originalWord = try XCTUnwrap(fixture.idioms[index]["word"] as? String)
        let replacementID = UUID().uuidString.lowercased()
        XCTAssertFalse(fixture.idioms.contains { $0["id"] as? String == replacementID })
        fixture.idioms[index]["id"] = replacementID
        for exampleIndex in fixture.examples.indices {
            let ids = try XCTUnwrap(fixture.examples[exampleIndex]["idiomIds"] as? [String])
            fixture.examples[exampleIndex]["idiomIds"] = ids.map { $0 == originalID ? replacementID : $0 }
        }
        XCTAssertEqual(fixture.idioms[index]["word"] as? String, originalWord)
        let updated = try importer.validate(fixture.zip())
        XCTAssertEqual(updated.counts, published.counts)
        XCTAssertNotEqual(updated.sha256, published.sha256)
        XCTAssertEqual(try snapshot(contentTables + userTables), before)
        XCTAssertThrowsError(try importer.importPack(updated))
        XCTAssertEqual(try snapshot(contentTables + userTables), before)
        try importer.refreshScopeLinks(published)
        XCTAssertEqual(try snapshot(contentTables + userTables), before)
    }

    private func seedUserTables(stateID: String, mistakeID: String) throws {
        try queue.write { db in
            // Question text/options come straight from imported real example, not a fabricated question.
            try db.execute(sql: """
                INSERT INTO mistake(id, imagePath, ocrText, stem, optionA, optionB, optionC, optionD,
                    myAnswer, reason, tagNames, examType, year, region, state, createdAt, lastReviewedAt, reviewCount)
                SELECT id, NULL, NULL, stem, optionA, optionB, optionC, optionD,
                    NULL, '望文生义', '["言语理解"]', examType, year, region, '待复习',
                    '2026-11-18T00:00:00Z', '2026-11-18T01:00:00Z', 1 FROM example ORDER BY id LIMIT 1
                """)
            try db.execute(sql: """
                INSERT INTO user_example(id, imagePath, ocrText, stem, optionA, optionB, optionC, optionD,
                    myAnswer, isMistake, examType, year, region, createdAt)
                SELECT 'captured-user-example', NULL, NULL, stem, optionA, optionB, optionC, optionD,
                    'A', 1, examType, year, region, '2026-11-18T00:00:00Z' FROM example ORDER BY id LIMIT 1
                """)
            try db.execute(sql: "UPDATE mistake SET userExampleId = 'captured-user-example'")
            try db.execute(sql: "INSERT INTO user_example_idiom VALUES ('captured-user-example', ?)",
                           arguments: [mistakeID])
            try db.execute(sql: "INSERT INTO mistake_idiom SELECT id, ? FROM mistake", arguments: [mistakeID])
            try db.execute(sql: "INSERT INTO review_log SELECT id, id, '忘记', '2026-11-18T01:00:00Z' FROM mistake")
            try db.execute(sql: "INSERT INTO idiom_state(idiomId, status, favorite) VALUES (?, '学过', 1)",
                           arguments: [stateID])
            try db.execute(sql: "INSERT INTO settings(key, value) VALUES ('packVersion', 'v1.0.0'), ('theme', 'dark')")
            try db.execute(sql: """
                INSERT INTO exam_scope(id, type, province, enabled) VALUES ('national', '国考', NULL, 1)
                """)
        }
    }
}
