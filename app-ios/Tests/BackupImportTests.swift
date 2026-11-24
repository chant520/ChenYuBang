@testable import CangyuData
import Foundation
import GRDB
import XCTest

final class BackupImportTests: PackDatabaseTestCase {
    private struct BackupFiles {
        var manifest: [String: Any]
        var data: Data

        func archive() throws -> Data {
            let manifestData = try JSONSerialization.data(withJSONObject: manifest)
            return PackZIPFixture.make([("data.json", data), ("manifest.json", manifestData)])
        }
    }

    func testValidGeneratedBackupRoundTripsAllCounts() throws {
        try seedReferencedIdioms(count: 2)
        let files = try generatedFiles()
        let validated = try validate(files)
        XCTAssertEqual(validated.counts, BackupCounts(mistakes: 1, userExamples: 2, reviews: 1,
                                                       idiomStates: 1, scopes: 1))
        XCTAssertEqual(validated.sha256, files.manifest["sha256"] as? String)
        XCTAssertEqual(validated.exportedAt, "2026-11-24T05:06:07Z")
        XCTAssertEqual(validated.payload.userExamples?.count, 2)
        XCTAssertTrue(validated.payload.userExamples?.contains { !$0.isMistake } == true)
        XCTAssertEqual(validated.payload.mistakes.first?.options.optionA, "选项甲")
    }

    func testOlderSharedBackupWithoutUserExamplesRemainsValid() throws {
        try seedReferencedIdioms(count: 3)
        let directory = try sharedBackupDirectory()
        let data = try Data(contentsOf: directory.appendingPathComponent("data.json"))
        let manifestData = try Data(contentsOf: directory.appendingPathComponent("manifest.json"))
        let manifest = try XCTUnwrap(JSONSerialization.jsonObject(with: manifestData) as? [String: Any])
        let files = BackupFiles(manifest: manifest, data: data)
        let validated = try validate(files)

        XCTAssertEqual(validated.sha256, "5311377e4d3bf4fa585eef5fc9e2ed8967de1863850a24708e7e90accd3c63d9")
        XCTAssertEqual(validated.counts, BackupCounts(mistakes: 2, userExamples: 0, reviews: 2,
                                                       idiomStates: 3, scopes: 2))
        XCTAssertNil(validated.payload.userExamples)
        XCTAssertNil((manifest["counts"] as? [String: Any])?["userExamples"])
        let payload = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertNil(payload["userExamples"])
    }

    func testValidationRejectsLocalCentralHeaderMismatch() throws {
        try seedReferencedIdioms(count: 2)
        var archive = try generatedFiles().archive()
        archive[30] ^= 1
        XCTAssertThrowsError(try queue.read { db in
            try BackupImport.validate(archive, db: db)
        }) { error in
            XCTAssertTrue(String(describing: error).contains("备份包结构不合法"))
        }
    }

    func testValidationRejectsHigherSchemaAndDigestMismatch() throws {
        try seedReferencedIdioms(count: 2)
        let files = try generatedFiles()
        var higher = files
        higher.manifest["schemaVersion"] = Backup.exchangeSchemaVersion + 1
        assertRejected(higher, contains: "版本过高")

        var badDigest = files
        badDigest.manifest["sha256"] = String(repeating: "0", count: 64)
        assertRejected(badDigest, contains: "摘要不符")
    }

    func testValidationRejectsCountMismatchButDefersMissingReferencedIdiom() throws {
        try seedReferencedIdioms(count: 2)
        let files = try generatedFiles()
        var badCounts = files
        var counts = try XCTUnwrap(badCounts.manifest["counts"] as? [String: Any])
        counts["mistakes"] = (counts["mistakes"] as? Int ?? 0) + 1
        badCounts.manifest["counts"] = counts
        assertRejected(badCounts, contains: "条数")

        var missingIdiom = files
        let payload = try dataDictionary(missingIdiom)
        var mistakes = try XCTUnwrap(payload["mistakes"] as? [[String: Any]])
        var mistake = try XCTUnwrap(mistakes.first)
        mistake["idiomIds"] = ["99999999-9999-4999-8999-999999999999"]
        mistakes[0] = mistake
        var changedPayload = payload
        changedPayload["mistakes"] = mistakes
        missingIdiom = try rehashed(missingIdiom, payload: changedPayload)
        let validated = try validate(missingIdiom)
        XCTAssertEqual(validated.payload.mistakes.first?.idiomIds,
                       ["99999999-9999-4999-8999-999999999999"])
    }

    func testValidationAcceptsEmptyIdiomIdsForImportState() throws {
        try seedReferencedIdioms(count: 2)
        let files = try generatedFiles()
        var payload = try dataDictionary(files)
        var mistakes = try XCTUnwrap(payload["mistakes"] as? [[String: Any]])
        var mistake = try XCTUnwrap(mistakes.first)
        mistake["idiomIds"] = []
        mistakes[0] = mistake
        var examples = try XCTUnwrap(payload["userExamples"] as? [[String: Any]])
        var example = try XCTUnwrap(examples.first)
        example["idiomIds"] = []
        examples[0] = example
        payload["mistakes"] = mistakes
        payload["userExamples"] = examples

        let validated = try validate(try rehashed(files, payload: payload))

        XCTAssertEqual(validated.payload.mistakes.first?.idiomIds, [])
        XCTAssertEqual(validated.payload.userExamples?.first?.idiomIds, [])
    }

    func testValidationRejectsDuplicateAndNFCCollidingJSONKeys() throws {
        try seedReferencedIdioms(count: 2)
        let files = try generatedFiles()
        let original = try XCTUnwrap(String(data: files.data, encoding: .utf8))
        let duplicate = try XCTUnwrap(original.replacingOccurrences(
            of: "\"options\":{",
            with: "\"options\":{\"\\u0041\":\"重复\",\"A\":\"重复\","
        ))
        var duplicateFiles = files
        duplicateFiles.data = Data(duplicate.utf8)
        assertRejected(duplicateFiles, contains: "Duplicate or NFC-colliding key")

        let colliding = String(original.dropLast()) + ",\"é\":null,\"e\u{0301}\":null}"
        var collidingFiles = files
        collidingFiles.data = Data(colliding.utf8)
        assertRejected(collidingFiles, contains: "Duplicate or NFC-colliding key")
    }

    func testValidationRejectsUnknownFieldsAndInvalidTimestamp() throws {
        try seedReferencedIdioms(count: 2)
        let files = try generatedFiles()
        var rootPayload = try dataDictionary(files)
        rootPayload["unexpected"] = true
        assertRejected(try rehashed(files, payload: rootPayload), contains: "共享 schema")

        let nested = try changingMistake(files) { mistake in
            var options = try XCTUnwrap(mistake["options"] as? [String: Any])
            options["E"] = "额外选项"
            mistake["options"] = options
        }
        assertRejected(nested, contains: "共享 schema")

        let timestamp = try changingMistake(files) { $0["createdAt"] = "2026-11-24" }
        assertRejected(timestamp, contains: "共享 schema")
    }

    func testValidationRejectsTagProjectionAndReviewCountMismatch() throws {
        try seedReferencedIdioms(count: 2)
        let files = try generatedFiles()
        let tags = try changingMistake(files) { $0["tagNames"] = ["不存在"] }
        assertRejected(tags, contains: "标签不在")
        let reviews = try changingMistake(files) { $0["reviewCount"] = 2 }
        assertRejected(reviews, contains: "复习条数")
    }

    func testMissingOptionalTagNamesRemainsValid() throws {
        try seedReferencedIdioms(count: 2)
        let files = try generatedFiles()
        let changed = try changingMistake(files) { $0.removeValue(forKey: "tagNames") }
        let validated = try validate(changed)
        XCTAssertNil(validated.payload.mistakes.first?.tagNames)
    }

}

private extension BackupImportTests {
    private func generatedFiles() throws -> BackupFiles {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("backup-import-test-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        let exported = try queue.read { db in
            try Backup.export(db: db, to: directory, date: Date(timeIntervalSince1970: 1_795_496_767),
                              identity: BackupIdentity(appVersion: "1.0.0", build: 1))
        }
        let files = try BackupArchive.read(Data(contentsOf: exported.url))
        return BackupFiles(manifest: try dictionary(files["manifest.json"]), data: try XCTUnwrap(files["data.json"]))
    }

    private func validate(_ files: BackupFiles) throws -> ValidatedBackup {
        try queue.read { db in try BackupImport.validate(files.archive(), db: db) }
    }

    private func assertRejected(_ files: BackupFiles, contains message: String, file: StaticString = #filePath,
                                line: UInt = #line) {
        XCTAssertThrowsError(try validate(files), file: file, line: line) { error in
            guard let backupError = error as? BackupError, case let .format(reason) = backupError else {
                return XCTFail("Expected BackupError.format, received \(error)", file: file, line: line)
            }
            XCTAssertTrue(reason.contains(message), "Expected '\(message)' in '\(reason)'", file: file, line: line)
        }
    }

    private func changingMistake(
        _ files: BackupFiles, _ change: (inout [String: Any]) throws -> Void
    ) throws -> BackupFiles {
        var payload = try dataDictionary(files)
        var mistakes = try XCTUnwrap(payload["mistakes"] as? [[String: Any]])
        var mistake = try XCTUnwrap(mistakes.first)
        try change(&mistake)
        mistakes[0] = mistake
        payload["mistakes"] = mistakes
        return try rehashed(files, payload: payload)
    }

    private func rehashed(_ files: BackupFiles, payload: [String: Any]) throws -> BackupFiles {
        let data = try JSONSerialization.data(withJSONObject: payload)
        let json = try PackFixture.json(data)
        var manifest = files.manifest
        manifest["sha256"] = Backup.digest(of: PackCanonical.text(json))
        return BackupFiles(manifest: manifest, data: data)
    }

    private func dataDictionary(_ files: BackupFiles) throws -> [String: Any] {
        try dictionary(files.data)
    }

    private func dictionary(_ data: Data?) throws -> [String: Any] {
        let data = try XCTUnwrap(data)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    private func sharedBackupDirectory() throws -> URL {
        var directory = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        for _ in 0..<5 {
            let candidate = directory.appendingPathComponent("shared/fixtures/backup", isDirectory: true)
            if FileManager.default.fileExists(atPath: candidate.appendingPathComponent("data.json").path),
               FileManager.default.fileExists(atPath: candidate.appendingPathComponent("manifest.json").path) {
                return candidate
            }
            directory = directory.deletingLastPathComponent()
        }
        throw CocoaError(.fileNoSuchFile)
    }

    private func seedReferencedIdioms(count: Int) throws {
        let idioms = [
            ("11111111-1111-4111-8111-111111111111", "按部就班"),
            ("22222222-2222-4222-8222-222222222222", "墨守成规"),
            ("33333333-3333-4333-8333-333333333333", "别出心裁")
        ]
        try queue.write { db in
            for (id, word) in idioms.prefix(count) {
                try db.execute(sql: """
                    INSERT INTO idiom(id, word, pinyin, initials, sense, color, frequency)
                    VALUES (?, ?, 'pin yin', 'initials', '释义', '褒义', 1)
                    """, arguments: [id, word])
            }
            try db.execute(sql: """
                INSERT INTO user_example(id, imagePath, stem, isMistake, createdAt)
                VALUES ('10000000-0000-4000-8000-000000000001', 'images/user.jpg', '录入题题干', 1,
                        '2026-11-20T00:00:00Z')
                """)
            try db.execute(sql: """
                INSERT INTO user_example(id, imagePath, stem, isMistake, createdAt)
                VALUES ('10000000-0000-4000-8000-000000000002', NULL, '未标错录入题', 0,
                        '2026-11-20T00:00:00Z')
                """)
            try db.execute(sql: """
                INSERT INTO user_example_idiom(userExampleId, idiomId)
                VALUES ('10000000-0000-4000-8000-000000000001',
                        '11111111-1111-4111-8111-111111111111'),
                       ('10000000-0000-4000-8000-000000000002',
                        '11111111-1111-4111-8111-111111111111')
                """)
            try db.execute(sql: """
                INSERT INTO mistake(id, userExampleId, imagePath, stem, optionA, optionB, optionC, optionD,
                                    tagNames, state, createdAt, reviewCount)
                VALUES ('20000000-0000-4000-8000-000000000001',
                        '10000000-0000-4000-8000-000000000001', 'images/user.jpg', '错题题干',
                        '选项甲', '选项乙', '选项丙', '选项丁', '[]', '待复习',
                        '2026-11-20T00:00:00Z', 1)
                """)
            try db.execute(sql: """
                INSERT INTO mistake_idiom(mistakeId, idiomId)
                VALUES ('20000000-0000-4000-8000-000000000001',
                        '11111111-1111-4111-8111-111111111111')
                """)
            try db.execute(sql: """
                INSERT INTO review_log(id, mistakeId, result, at)
                VALUES ('30000000-0000-4000-8000-000000000001',
                        '20000000-0000-4000-8000-000000000001', '对', '2026-11-21T00:00:00Z')
                """)
            try db.execute(sql: """
                INSERT INTO idiom_state(idiomId, status, favorite)
                VALUES ('11111111-1111-4111-8111-111111111111', '学过', 0)
                """)
            try db.execute(sql: """
                INSERT INTO exam_scope(id, type, province, enabled)
                VALUES ('scope-import', '国考', NULL, 1)
                """)
        }
    }
}
