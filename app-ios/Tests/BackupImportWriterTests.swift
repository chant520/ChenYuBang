@testable import CangyuData
import Foundation
import GRDB
import XCTest

private enum BackupWriterFixture {
    static let idiomA = "11111111-1111-4111-8111-111111111111"
    static let idiomB = "22222222-2222-4222-8222-222222222222"
    static let sharedExample = "10000000-0000-4000-8000-000000000001"
    static let extraExample = "10000000-0000-4000-8000-000000000002"
    static let sharedMistake = "20000000-0000-4000-8000-000000000001"
    static let extraMistake = "20000000-0000-4000-8000-000000000002"
    static let sharedReview = "30000000-0000-4000-8000-000000000001"
    static let extraReview = "30000000-0000-4000-8000-000000000002"
    static let localExtraExample = "10000000-0000-4000-8000-000000000003"
    static let localExtraMistake = "20000000-0000-4000-8000-000000000003"
    static let localExtraReview = "30000000-0000-4000-8000-000000000003"
    static let contentTables = ["idiom", "example", "example_idiom", "idiom_fts"]
    static let userTables = ["mistake", "mistake_idiom", "review_log", "idiom_state", "exam_scope",
                             "user_example", "user_example_idiom", "idiom_scope"]
}

final class BackupImportWriterTests: PackDatabaseTestCase {
    func testMergeKeepsLocalConflictsAndInsertsNewRowsWithoutImages() throws {
        try BackupWriterFixture.seedDestination(queue)
        let contentBefore = try snapshot(BackupWriterFixture.contentTables)
        let settingsBefore = try snapshot(["settings"])
        let fixture = try makeImport()
        let service = BackupService(queue: queue)
        let summary = try service.importBackup(fixture, mode: .merge)

        XCTAssertEqual(summary.mode, .merge)
        XCTAssertEqual(summary.mistakes, fixture.counts.mistakes)
        XCTAssertEqual(summary.userExamples, fixture.counts.userExamples)
        XCTAssertNil(summary.automaticBackup)
        try assertMergedState()
        let merged = try snapshot(BackupWriterFixture.userTables)
        _ = try service.importBackup(fixture, mode: .merge)
        XCTAssertEqual(try snapshot(BackupWriterFixture.userTables), merged, "merge must be idempotent")
        XCTAssertEqual(try snapshot(BackupWriterFixture.contentTables), contentBefore)
        let settingsAfter = try snapshot(["settings"])
        XCTAssertEqual(settingsAfter.filter { $0.key != ExamScopeStore.provincesKey },
                       settingsBefore.filter { $0.key != ExamScopeStore.provincesKey })
        let provinceJSON = try XCTUnwrap(settingsAfter[ExamScopeStore.provincesKey])
        XCTAssertEqual(try JSONDecoder().decode([String].self, from: Data(provinceJSON.utf8)), ["广东", "湖北"])
    }

    func testMergeSkipsReviewIDCollisionAndKeepsCountsConsistent() throws {
        try BackupWriterFixture.seedDestination(queue)
        let fixture = try makeImport(collidingReviewID: BackupWriterFixture.localExtraReview)
        let service = BackupService(queue: queue)
        _ = try service.importBackup(fixture, mode: .merge)
        try queue.read { db in
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT reviewCount FROM mistake WHERE id = ?",
                                            arguments: [BackupWriterFixture.extraMistake]), 0)
            XCTAssertEqual(try count(db, "review_log"), 2)
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT mistakeId FROM review_log WHERE id = ?",
                                               arguments: [BackupWriterFixture.localExtraReview]),
                           BackupWriterFixture.localExtraMistake)
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT result FROM review_log WHERE id = ?",
                                               arguments: [BackupWriterFixture.localExtraReview]), "错")
            XCTAssertNil(try String.fetchOne(db, sql: "SELECT lastReviewedAt FROM mistake WHERE id = ?",
                                             arguments: [BackupWriterFixture.extraMistake]))
        }
        let merged = try snapshot(BackupWriterFixture.userTables)
        _ = try service.importBackup(fixture, mode: .merge)
        XCTAssertEqual(try snapshot(BackupWriterFixture.userTables), merged)
    }

    func testReplaceStagesAtomicallyAndCreatesPreImportCacheBackup() throws {
        try BackupWriterFixture.seedDestination(queue)
        let contentBefore = try snapshot(BackupWriterFixture.contentTables)
        let settingsBefore = try snapshot(["settings"])
        let fixture = try makeImport()
        let (caches, cacheBefore) = try cacheSnapshot()
        defer { try? removeNewCacheFiles(caches, cacheBefore) }

        let summary = try BackupService(queue: queue).importBackup(fixture, mode: .replace)

        XCTAssertEqual(summary.mode, .replace)
        XCTAssertEqual(summary.mistakes, fixture.counts.mistakes)
        XCTAssertEqual(summary.userExamples, fixture.counts.userExamples)
        let automaticBackup = try XCTUnwrap(summary.automaticBackup)
        XCTAssertEqual(automaticBackup.deletingLastPathComponent().standardizedFileURL,
                       caches.standardizedFileURL)
        XCTAssertTrue(automaticBackup.lastPathComponent.hasPrefix("backup-before-import-"))
        XCTAssertNotNil(automaticBackup.lastPathComponent.range(
            of: #"^backup-before-import-\d{8}-\d{6}\.zip$"#, options: .regularExpression
        ))
        try assertReplacedState()
        XCTAssertEqual(try snapshot(BackupWriterFixture.contentTables), contentBefore)
        try assertReplacedSettings()
        let cacheAfter = try cacheNames(caches)
        XCTAssertEqual(cacheAfter.filter { $0.hasPrefix("backup-import-") && $0.hasSuffix(".sqlite") },
                       cacheBefore.filter { $0.hasPrefix("backup-import-") && $0.hasSuffix(".sqlite") })

        let preImport = try queue.read { db in
            try BackupImport.validate(Data(contentsOf: automaticBackup), db: db)
        }
        XCTAssertEqual(preImport.counts.mistakes, 2)
        XCTAssertEqual(preImport.payload.mistakes.first?.stem, "本机共享错题")
        XCTAssertTrue(preImport.payload.mistakes.contains { $0.id == BackupWriterFixture.localExtraMistake })
        XCTAssertFalse(preImport.payload.mistakes.contains { $0.id == BackupWriterFixture.extraMistake })
    }

    func testReplaceRebuildsScopeLinksAgainstImportedScopeIDs() throws {
        let pack = try importer.validate(PackFixture.published())
        try importer.importPack(pack)
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO exam_scope(id, type, province, enabled)
                VALUES ('old-scope', '国考', NULL, 1)
                """)
            try db.execute(sql: "INSERT INTO settings(key, value) VALUES ('examScope.provinces', '[\"湖北\"]')")
        }
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("backup-scope-test-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        let service = BackupService(queue: queue)
        let exported = try service.export(to: directory)
        let backup = try service.validate(Data(contentsOf: exported.url))
        let (caches, cacheBefore) = try cacheSnapshot()
        defer { try? removeNewCacheFiles(caches, cacheBefore) }

        _ = try service.importBackup(backup, mode: .replace)
        XCTAssertEqual(try count("idiom_scope"), 0)
        XCTAssertNil(try queue.read { db in
            try String.fetchOne(db, sql: "SELECT value FROM settings WHERE key = ?",
                                arguments: [ExamScopeStore.provincesKey])
        })
        try importer.refreshScopeLinks(pack)
        let scopeID = try XCTUnwrap(queue.read { db in
            try String.fetchOne(db, sql: "SELECT id FROM exam_scope WHERE type = '国考'")
        })
        XCTAssertNotEqual(scopeID, "old-scope")
        XCTAssertGreaterThan(try queue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM idiom_scope WHERE scopeId = ?", arguments: [scopeID])
        } ?? 0, 0)
    }

    func testWriterFailureRollsBackUserRowsAndCanRetry() throws {
        try BackupWriterFixture.seedDestination(queue)
        let before = try snapshot(BackupWriterFixture.contentTables + BackupWriterFixture.userTables + ["settings"])
        let fixture = try makeImport()
        let (caches, cacheBefore) = try cacheSnapshot()
        defer { try? removeNewCacheFiles(caches, cacheBefore) }
        try queue.write { db in
            try db.execute(sql: """
                CREATE TRIGGER reject_backup_link BEFORE INSERT ON mistake_idiom
                BEGIN SELECT RAISE(ABORT, 'replace rollback test'); END
                """)
        }
        addTeardownBlock { try? self.queue.write { db in try db.execute(sql: "DROP TRIGGER reject_backup_link") } }

        XCTAssertThrowsError(try BackupService(queue: queue).importBackup(fixture, mode: .replace)) { error in
            XCTAssertTrue(String(describing: error).contains("replace rollback test"))
        }
        XCTAssertEqual(try snapshot(BackupWriterFixture.contentTables + BackupWriterFixture.userTables + ["settings"]),
                       before)
        let cacheAfter = try cacheNames(caches)
        XCTAssertEqual(cacheAfter.filter { $0.hasPrefix("backup-import-") && $0.hasSuffix(".sqlite") },
                       cacheBefore.filter { $0.hasPrefix("backup-import-") && $0.hasSuffix(".sqlite") })
        let backups = cacheAfter.subtracting(cacheBefore).filter { $0.hasPrefix("backup-") && $0.hasSuffix(".zip") }
        XCTAssertEqual(backups.count, 1)
        if let backup = backups.first {
            let saved = try queue.read { db in
                try BackupImport.validate(Data(contentsOf: caches.appendingPathComponent(backup)), db: db)
            }
            XCTAssertEqual(saved.payload.mistakes.first?.stem, "本机共享错题")
        }

        try queue.write { db in try db.execute(sql: "DROP TRIGGER reject_backup_link") }
        _ = try BackupService(queue: queue).importBackup(fixture, mode: .replace)
        try assertReplacedState()
        XCTAssertEqual(try snapshot(BackupWriterFixture.contentTables),
                       before.filter { BackupWriterFixture.contentTables.contains($0.key) })
        try assertReplacedSettings()
    }

}

private extension BackupImportWriterTests {
    private func makeImport(collidingReviewID: String? = nil) throws -> ValidatedBackup {
        let source = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(source)
        try source.write { db in
            try BackupWriterFixture.seedUserRows(db, local: false)
            if let collidingReviewID {
                try db.execute(sql: "UPDATE review_log SET id = ? WHERE id = ?",
                               arguments: [collidingReviewID, BackupWriterFixture.extraReview])
            }
        }
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("backup-writer-test-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        let exported = try source.read { db in
            try Backup.export(db: db, to: directory, date: Date(timeIntervalSince1970: 1_795_496_767),
                              identity: BackupIdentity(appVersion: "1.0.0", build: 1))
        }
        return try queue.read { db in
            try BackupImport.validate(Data(contentsOf: exported.url), db: db)
        }
    }

    private func assertMergedState() throws {
        try queue.read { db in
            XCTAssertEqual(try count(db, "mistake"), 3)
            XCTAssertEqual(try count(db, "user_example"), 3)
            XCTAssertEqual(try count(db, "review_log"), 3)
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT stem FROM mistake WHERE id = ?",
                                               arguments: [BackupWriterFixture.sharedMistake]), "本机共享错题")
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT stem FROM mistake WHERE id = ?",
                                               arguments: [BackupWriterFixture.extraMistake]), "备份新增错题")
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT imagePath FROM mistake WHERE id = ?",
                                               arguments: [BackupWriterFixture.extraMistake]), nil)
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT imagePath FROM user_example WHERE id = ?",
                                               arguments: [BackupWriterFixture.extraExample]), nil)
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT result FROM review_log WHERE id = ?",
                                               arguments: [BackupWriterFixture.sharedReview]), "错")
            XCTAssertEqual(try links(db, "mistake_idiom", "mistakeId", BackupWriterFixture.sharedMistake),
                           [BackupWriterFixture.idiomA])
            XCTAssertEqual(try links(db, "user_example_idiom", "userExampleId", BackupWriterFixture.sharedExample),
                           [BackupWriterFixture.idiomA])
            XCTAssertEqual(try links(db, "user_example_idiom", "userExampleId", BackupWriterFixture.extraExample),
                           [BackupWriterFixture.idiomA, BackupWriterFixture.idiomB])
            let provinces = try String.fetchAll(
                db, sql: "SELECT province FROM exam_scope WHERE province IS NOT NULL ORDER BY 1"
            )
            XCTAssertEqual(provinces, ["广东", "湖北"])
        }
    }

    private func assertReplacedState() throws {
        try queue.read { db in
            XCTAssertEqual(try count(db, "mistake"), 2)
            XCTAssertEqual(try count(db, "user_example"), 2)
            XCTAssertEqual(try count(db, "review_log"), 2)
            XCTAssertEqual(try count(db, "idiom_state"), 2)
            XCTAssertEqual(try count(db, "exam_scope"), 2)
            XCTAssertEqual(try count(db, "idiom_scope"), 0)
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT stem FROM mistake WHERE id = ?",
                                               arguments: [BackupWriterFixture.sharedMistake]), "备份共享错题")
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT stem FROM mistake WHERE id = ?",
                                               arguments: [BackupWriterFixture.extraMistake]), "备份新增错题")
            XCTAssertNil(try String.fetchOne(db, sql: "SELECT id FROM mistake WHERE id = ?",
                                             arguments: [BackupWriterFixture.localExtraMistake]))
            XCTAssertEqual(try Int.fetchOne(db, sql: """
                SELECT COUNT(*) FROM mistake WHERE id = ? AND imagePath IS NOT NULL
                """, arguments: [BackupWriterFixture.extraMistake]), 0)
            XCTAssertEqual(try Int.fetchOne(db, sql: """
                SELECT COUNT(*) FROM user_example WHERE id = ? AND imagePath IS NOT NULL
                """, arguments: [BackupWriterFixture.extraExample]), 0)
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT result FROM review_log WHERE id = ?",
                                               arguments: [BackupWriterFixture.sharedReview]), "对")
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT status FROM idiom_state WHERE idiomId = ?",
                                               arguments: [BackupWriterFixture.idiomA]), "已掌握")
            XCTAssertEqual(try String.fetchAll(db, sql: "SELECT province FROM exam_scope WHERE province IS NOT NULL"),
                           ["广东"])
            XCTAssertEqual(try Bool.fetchOne(db, sql: "SELECT enabled FROM exam_scope WHERE type = '国考'"), false)
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM exam_scope WHERE id = 'scope-national'"), 0)
            XCTAssertEqual(try links(db, "mistake_idiom", "mistakeId", BackupWriterFixture.sharedMistake),
                           [BackupWriterFixture.idiomB])
        }
    }

    private func assertReplacedSettings() throws {
        let values = try queue.read { db in
            try Dictionary(uniqueKeysWithValues: Row.fetchAll(db, sql: "SELECT key, value FROM settings").map {
                ($0["key"] as String, $0["value"] as String)
            })
        }
        XCTAssertEqual(values["theme"], "dark")
        XCTAssertEqual(values["keep"], "yes")
        XCTAssertNil(values[ExamScopeStore.provincesKey])
    }

    private func count(_ db: Database, _ table: String) throws -> Int {
        try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") ?? -1
    }

    private func links(_ db: Database, _ table: String, _ column: String, _ id: String) throws -> [String] {
        try String.fetchAll(db, sql: "SELECT idiomId FROM \(table) WHERE \(column) = ? ORDER BY idiomId",
                            arguments: [id])
    }

    private func cacheSnapshot() throws -> (URL, Set<String>) {
        let caches = try FileManager.default.url(for: .cachesDirectory, in: .userDomainMask,
                                                 appropriateFor: nil, create: true)
        return (caches, try cacheNames(caches))
    }

    private func cacheNames(_ directory: URL) throws -> Set<String> {
        Set(try FileManager.default.contentsOfDirectory(atPath: directory.path))
    }

    private func removeNewCacheFiles(_ directory: URL, _ previous: Set<String>) throws {
        for name in try cacheNames(directory).subtracting(previous) where name.hasPrefix("backup-") {
            try? FileManager.default.removeItem(at: directory.appendingPathComponent(name))
        }
    }
}

private extension BackupWriterFixture {
    static func seedDestination(_ queue: DatabaseQueue) throws {
        try queue.write { db in
            for (id, word) in [(idiomA, "按部就班"), (idiomB, "墨守成规")] {
                try db.execute(sql: """
                    INSERT INTO idiom(id, word, pinyin, initials, sense, color, frequency)
                    VALUES (?, ?, 'pin yin', 'initials', '释义', '褒义', 1)
                    """, arguments: [id, word])
            }
            try db.execute(sql: """
                INSERT INTO example(id, stem, optionA, optionB, optionC, optionD, answer, explain,
                                    examType, year, region, questionType, imagePath)
                VALUES ('content-example', '内容例题', '甲', '乙', '丙', '丁', 'A', '解析',
                        '国考', 2026, '中央', '片段阅读', 'images/content.jpg')
                """)
            try db.execute(sql: """
                INSERT INTO example_idiom(exampleId, idiomId)
                VALUES ('content-example', '11111111-1111-4111-8111-111111111111')
                """)
            try db.execute(sql: """
                INSERT INTO idiom_fts(idiomid, word, pinyin, initials, sense)
                SELECT id, word, pinyin, initials, sense FROM idiom
                """)
            try db.execute(sql: """
                INSERT INTO settings(key, value) VALUES
                ('theme', 'dark'), ('keep', 'yes'), ('examScope.provinces', '["湖北"]')
                """)
        }
        try queue.write { db in try seedUserRows(db, local: true) }
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO idiom_scope(idiomId, scopeId)
                VALUES ('11111111-1111-4111-8111-111111111111', 'scope-national')
                """)
        }
    }

    static func seedUserRows(_ db: Database, local: Bool) throws {
        let prefix = local ? "本机" : "备份"
        let image = local ? "images/local.jpg" : "images/imported.jpg"
        let rows = [
            (sharedExample, sharedMistake, "\(prefix)共享"),
            (local ? localExtraExample : extraExample, local ? localExtraMistake : extraMistake,
             local ? "本机独有" : "备份新增")
        ]
        for (exampleID, mistakeID, label) in rows {
            try db.execute(sql: """
                INSERT INTO user_example(id, imagePath, ocrText, stem, optionA, optionB, optionC, optionD,
                                         myAnswer, isMistake, examType, year, region, createdAt)
                VALUES (?, ?, 'OCR', ?, '甲', '乙', '丙', '丁', 'B', 1, '国考', 2026, '中央',
                        '2026-11-20T00:00:00Z')
                """, arguments: [exampleID, image, "\(label)录入题"])
            try db.execute(sql: """
                INSERT INTO mistake(id, userExampleId, imagePath, ocrText, stem, optionA, optionB, optionC,
                                    optionD, myAnswer, reason, tagNames, examType, year, region, state,
                                    createdAt, lastReviewedAt, reviewCount)
                VALUES (?, ?, ?, 'OCR', ?, '甲', '乙', '丙', '丁', 'B', '望文生义', '["望文生义"]',
                        '国考', 2026, '中央', '待复习', '2026-11-20T00:00:00Z',
                        '2026-11-21T00:00:00Z', 1)
                """, arguments: [mistakeID, exampleID, image, "\(label)错题"])
            let idiomID = exampleID == sharedExample ? (local ? idiomA : idiomB) : idiomA
            try db.execute(sql: "INSERT INTO user_example_idiom(userExampleId, idiomId) VALUES (?, ?)",
                           arguments: [exampleID, idiomID])
            try db.execute(sql: "INSERT INTO mistake_idiom(mistakeId, idiomId) VALUES (?, ?)",
                           arguments: [mistakeID, idiomID])
            if exampleID == extraExample && !local {
                try db.execute(sql: """
                    INSERT INTO user_example_idiom(userExampleId, idiomId) VALUES (?, ?)
                    """, arguments: [exampleID, idiomB])
            }
        }
        let extraReviewID = local ? localExtraReview : extraReview
        let extraMistakeID = local ? localExtraMistake : extraMistake
        for (reviewID, mistakeID) in [(sharedReview, sharedMistake), (extraReviewID, extraMistakeID)] {
            try db.execute(sql: "INSERT INTO review_log(id, mistakeId, result, at) VALUES (?, ?, ?, ?)",
                           arguments: [reviewID, mistakeID, local ? "错" : "对", "2026-11-21T00:00:00Z"])
        }
        try db.execute(sql: "INSERT INTO idiom_state(idiomId, status, favorite) VALUES (?, ?, ?)",
                       arguments: [idiomA, local ? "学过" : "已掌握", local ? 0 : 1])
        try db.execute(sql: "INSERT INTO idiom_state(idiomId, status, favorite) VALUES (?, '学过', 1)",
                       arguments: [idiomB])
        try db.execute(sql: """
            INSERT INTO exam_scope(id, type, province, enabled) VALUES (?, '国考', NULL, ?)
            """, arguments: [local ? "scope-national" : "scope-source-national", local ? 1 : 0])
        try db.execute(sql: """
            INSERT INTO exam_scope(id, type, province, enabled) VALUES (?, '省考', ?, 1)
            """, arguments: [local ? "scope-local-province" : "scope-source-province",
                            local ? "湖北" : "广东"])
    }
}
