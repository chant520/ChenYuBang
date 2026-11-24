@testable import CangyuData
import Foundation
import GRDB
import XCTest

final class BackupImportOrphanTests: PackDatabaseTestCase {
    func testMergeResolvesOrphansAndReportsSkippedRows() throws {
        try BackupWriterFixture.seedDestination(queue)
        let summary = try queue.write { db in
            try BackupImportWriter.merge(makeOrphanPayload(), db: db)
        }

        XCTAssertEqual(summary.mistakes, 1)
        XCTAssertEqual(summary.userExamples, 1)
        XCTAssertEqual(summary.reviews, 1)
        XCTAssertEqual(summary.orphans.skippedMistakes, 1)
        XCTAssertEqual(summary.orphans.missingMistakeIdioms, 1)
        XCTAssertEqual(summary.orphans.missingExampleIdioms, 1)
        XCTAssertEqual(summary.orphans.skippedReviews, 2)
        XCTAssertTrue(summary.message.contains("过滤缺失成语关联 2 处"))
        try queue.read { db in
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT stem FROM mistake WHERE id = ?",
                                               arguments: [BackupWriterFixture.sharedMistake]), "本机共享错题")
            XCTAssertNil(try String.fetchOne(db, sql: "SELECT id FROM mistake WHERE id = ?",
                                             arguments: [BackupWriterFixture.extraMistake]))
            XCTAssertEqual(try links(db, "user_example_idiom", "userExampleId", BackupWriterFixture.extraExample),
                           [BackupWriterFixture.idiomA, BackupWriterFixture.idiomB])
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT mistakeId FROM review_log WHERE id = ?",
                                               arguments: [BackupWriterFixture.localReviewFromBackup]),
                           BackupWriterFixture.localExtraMistake)
            XCTAssertNil(try String.fetchOne(db, sql: "SELECT id FROM review_log WHERE id = ?",
                                             arguments: [BackupWriterFixture.extraReview]))
            XCTAssertNil(try String.fetchOne(db, sql: "SELECT id FROM review_log WHERE id = ?",
                                             arguments: ["30000000-0000-4000-8000-000000000005"]))
        }
    }

    func testReplaceUsesOnlyBackupReferencesWhenResolvingOrphans() throws {
        try BackupWriterFixture.seedDestination(queue)
        let (caches, cacheBefore) = try cacheSnapshot()
        defer { try? removeNewCacheFiles(caches, cacheBefore) }

        let summary = try BackupImportWriter.replace(makeOrphanPayload(), queue: queue)

        XCTAssertEqual(summary.mistakes, 1)
        XCTAssertEqual(summary.userExamples, 2)
        XCTAssertEqual(summary.reviews, 1)
        XCTAssertEqual(summary.orphans.skippedMistakes, 1)
        XCTAssertEqual(summary.orphans.missingMistakeIdioms, 1)
        XCTAssertEqual(summary.orphans.missingExampleIdioms, 1)
        XCTAssertEqual(summary.orphans.skippedReviews, 3)
        try queue.read { db in
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT stem FROM mistake WHERE id = ?",
                                               arguments: [BackupWriterFixture.sharedMistake]), "备份共享错题")
            XCTAssertNil(try String.fetchOne(db, sql: "SELECT id FROM mistake WHERE id = ?",
                                             arguments: [BackupWriterFixture.extraMistake]))
            XCTAssertEqual(try links(db, "mistake_idiom", "mistakeId", BackupWriterFixture.sharedMistake),
                           [BackupWriterFixture.idiomB])
            XCTAssertEqual(try links(db, "user_example_idiom", "userExampleId", BackupWriterFixture.extraExample),
                           [BackupWriterFixture.idiomA, BackupWriterFixture.idiomB])
            XCTAssertNil(try String.fetchOne(db, sql: "SELECT id FROM review_log WHERE id = ?",
                                             arguments: [BackupWriterFixture.localReviewFromBackup]))
            XCTAssertNil(try String.fetchOne(db, sql: "SELECT id FROM review_log WHERE id = ?",
                                             arguments: ["30000000-0000-4000-8000-000000000005"]))
        }
    }
}

private extension BackupImportOrphanTests {
    private func makeOrphanPayload() throws -> BackupDataPayload {
        let source = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(source)
        try source.write { db in try BackupWriterFixture.seedUserRows(db, local: false) }
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("backup-orphan-test-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        let exported = try source.read { db in
            try Backup.export(db: db, to: directory, date: Date(timeIntervalSince1970: 1_795_496_767),
                              identity: BackupIdentity(appVersion: "1.0.0", build: 1))
        }
        let fixture = try queue.read { db in
            try BackupImport.validate(Data(contentsOf: exported.url), db: db)
        }
        let payload = fixture.payload
        let missingID = "99999999-9999-4999-8999-999999999999"
        let mistakes = payload.mistakes.map { mistake -> BackupMistakePayload in
            let orphaned = mistake.id == BackupWriterFixture.extraMistake
            return BackupMistakePayload(
                id: mistake.id, userExampleId: orphaned ? missingID : mistake.userExampleId,
                stem: mistake.stem, options: mistake.options, imagePath: mistake.imagePath,
                ocrText: mistake.ocrText, myAnswer: mistake.myAnswer, reason: mistake.reason,
                tagNames: mistake.tagNames, examType: mistake.examType, year: mistake.year,
                region: mistake.region, state: mistake.state, createdAt: mistake.createdAt,
                lastReviewedAt: mistake.lastReviewedAt, reviewCount: mistake.reviewCount,
                idiomIds: orphaned ? mistake.idiomIds : mistake.idiomIds + [missingID]
            )
        }
        let examples = try XCTUnwrap(payload.userExamples).map { example -> BackupUserExamplePayload in
            let ids = example.id == BackupWriterFixture.extraExample ? example.idiomIds + [missingID] : example.idiomIds
            return BackupUserExamplePayload(
                id: example.id, imagePath: example.imagePath, ocrText: example.ocrText, stem: example.stem,
                optionA: example.optionA, optionB: example.optionB, optionC: example.optionC,
                optionD: example.optionD, myAnswer: example.myAnswer, isMistake: example.isMistake,
                examType: example.examType, year: example.year, region: example.region,
                createdAt: example.createdAt, idiomIds: ids
            )
        }
        let orphanReview = try XCTUnwrap(payload.reviews.first { $0.id == BackupWriterFixture.extraReview })
        let skippedMistakeReviewID = "30000000-0000-4000-8000-000000000005"
        let reviews = payload.reviews.map {
            if $0.id == orphanReview.id {
                return BackupReviewPayload(id: $0.id, mistakeId: missingID,
                                           result: $0.result, reviewedAt: $0.reviewedAt)
            }
            return $0
        } + [BackupReviewPayload(id: skippedMistakeReviewID,
                                 mistakeId: BackupWriterFixture.extraMistake,
                                 result: "错", reviewedAt: "2026-11-24T00:00:00Z"),
             BackupReviewPayload(id: BackupWriterFixture.localReviewFromBackup,
                                 mistakeId: BackupWriterFixture.localExtraMistake,
                                 result: "对", reviewedAt: "2026-11-24T00:00:00Z")]
        return BackupDataPayload(mistakes: mistakes, userExamples: examples, reviews: reviews,
                                 idiomStates: payload.idiomStates, scopes: payload.scopes, tags: payload.tags)
    }

    private func links(_ db: Database, _ table: String, _ column: String, _ id: String) throws -> [String] {
        try String.fetchAll(db, sql: "SELECT idiomId FROM \(table) WHERE \(column) = ? ORDER BY idiomId",
                            arguments: [id])
    }

    private func cacheSnapshot() throws -> (URL, Set<String>) {
        let caches = try FileManager.default.url(for: .cachesDirectory, in: .userDomainMask,
                                                 appropriateFor: nil, create: true)
        return (caches, Set(try FileManager.default.contentsOfDirectory(atPath: caches.path)))
    }

    private func removeNewCacheFiles(_ directory: URL, _ previous: Set<String>) throws {
        for name in try FileManager.default.contentsOfDirectory(atPath: directory.path).subtracting(previous)
            where name.hasPrefix("backup-") {
            try? FileManager.default.removeItem(at: directory.appendingPathComponent(name))
        }
    }
}
