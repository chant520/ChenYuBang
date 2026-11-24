@testable import CangyuData
import Foundation
import GRDB
import XCTest

final class BackupStoreTests: PackDatabaseTestCase {
    private let contentTables = ["idiom", "example", "exam_scope", "idiom_scope", "example_idiom", "idiom_fts"]
    private let exportDate = Date(timeIntervalSince1970: 1_795_496_767)

    func testExportContainsOnlyCanonicalManifestAndSixUserDataCategories() throws {
        try seedExportData()
        let contentBefore = try snapshot(contentTables)
        let directory = try makeDirectory()
        let result = try queue.read { db in
            try Backup.export(db: db, to: directory, date: exportDate,
                              identity: BackupIdentity(appVersion: "1.2.3-beta.4", build: 42))
        }
        let archiveData = try Data(contentsOf: result.url)
        let files = try BackupArchive.read(archiveData)
        XCTAssertEqual(Set(files.keys), ["manifest.json", "data.json"])
        let dataJSON = try PackFixture.json(XCTUnwrap(files["data.json"]))
        let manifestJSON = try PackFixture.json(XCTUnwrap(files["manifest.json"]))
        let payload = object(dataJSON)
        let manifest = object(manifestJSON)

        XCTAssertEqual(result.url.lastPathComponent, Backup.fileName(exportDate))
        XCTAssertEqual(text(manifest["exportedAt"]), Backup.stamp(exportDate))
        XCTAssertEqual(text(manifest["appVersion"]), "1.2.3")
        XCTAssertEqual(integer(manifest["build"]), 42)
        XCTAssertEqual(integer(manifest["schemaVersion"]), 3)
        XCTAssertEqual(Set(payload.keys), ["mistakes", "userExamples", "reviews", "idiomStates", "scopes", "tags"])
        XCTAssertEqual(String(data: files["data.json"] ?? Data(), encoding: .utf8), PackCanonical.text(dataJSON))
        XCTAssertEqual(result.sha256, Backup.digest(of: PackCanonical.text(dataJSON)))
        XCTAssertEqual(text(manifest["sha256"]), result.sha256)
        XCTAssertEqual(result.counts, BackupCounts(mistakes: 2, userExamples: 1, reviews: 1,
                                                   idiomStates: 2, scopes: 2))
        let validated = try queue.read { db in try BackupImport.validate(archiveData, db: db) }
        XCTAssertEqual(validated.counts, result.counts)

        let mistake = try XCTUnwrap(array(payload["mistakes"] ?? .null).first)
        let mistakeFields = object(mistake)
        XCTAssertEqual(text(mistakeFields["id"]), "20000000-0000-4000-8000-000000000001")
        XCTAssertEqual(array(mistakeFields["idiomIds"] ?? .null).compactMap(text),
                       ["11111111-1111-4111-8111-111111111111"])
        XCTAssertEqual(Set(object(mistakeFields["options"] ?? .null).keys), ["A", "B", "C", "D"])
        XCTAssertEqual(["A", "B", "C", "D"].map { text(object(mistakeFields["options"] ?? .null)[$0]) },
                       ["选项甲", "选项乙", "选项丙", "选项丁"])
        XCTAssertEqual(text(mistakeFields["imagePath"]), "images/user.jpg")
        let examples = array(payload["userExamples"] ?? .null)
        XCTAssertEqual(examples.count, 1)
        XCTAssertEqual(text(object(try XCTUnwrap(examples.first))["imagePath"]), "images/user.jpg")
        XCTAssertEqual(array(payload["reviews"] ?? .null).count, 1)
        let review = try XCTUnwrap(array(payload["reviews"] ?? .null).first)
        XCTAssertEqual(text(object(review)["id"]), "30000000-0000-4000-8000-000000000001")
        XCTAssertEqual(text(object(review)["mistakeId"]), "20000000-0000-4000-8000-000000000001")
        XCTAssertEqual(array(payload["idiomStates"] ?? .null).count, 2)

        let scopes = array(payload["scopes"] ?? .null).map(object)
        XCTAssertEqual(scopes.count, 2)
        XCTAssertTrue(scopes.allSatisfy { Set($0.keys) == ["type", "province", "enabled"] })
        XCTAssertTrue(scopes.contains { text($0["type"]) == "国考" && $0["province"] == .null })
        let tags = array(payload["tags"] ?? .null).map(object)
        XCTAssertEqual(tags.map { text($0["name"]) }, ["望文生义", "搭配不当", "褒贬误用"])
        XCTAssertTrue(tags.allSatisfy { Set($0.keys) == ["name"] })
        XCTAssertFalse(payload.keys.contains("idiom"))
        XCTAssertFalse(payload.keys.contains("example"))
        XCTAssertFalse(payload.keys.contains("images"))
        XCTAssertEqual(try snapshot(contentTables), contentBefore, "export must be read-only")
    }

    func testExportRejectsUserExampleWithoutIdiom() throws {
        try seedExportData()
        try queue.write { db in try db.execute(sql: "DELETE FROM user_example_idiom") }
        let directory = try makeDirectory()
        XCTAssertThrowsError(try queue.read { db in
            try Backup.export(db: db, to: directory, date: exportDate)
        }) { error in
            guard case let BackupError.format(message) = error else {
                return XCTFail("Expected backup format error, got \(error)")
            }
            XCTAssertTrue(message.contains("idiomIds"))
        }
    }

    func testCanonicalHelpersUseUTCAndNormalizeIdentity() {
        let date = Date(timeIntervalSince1970: 1_795_496_767)
        XCTAssertEqual(Backup.stamp(date), "2026-11-24T05:06:07Z")
        XCTAssertEqual(Backup.fileName(date), "backup-20261124-050607.zip")
        XCTAssertEqual(Backup.digest(of: "{}"), "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a")
        XCTAssertEqual(Backup.normalized(appVersion: "2.10.0.7"), "2.10.0")
        XCTAssertEqual(Backup.normalized(appVersion: "beta"), "0.0.0")
    }

    private func seedExportData() throws {
        try queue.write { db in
            for (id, word) in [
                ("11111111-1111-4111-8111-111111111111", "按部就班"),
                ("22222222-2222-4222-8222-222222222222", "墨守成规")
            ] {
                try db.execute(sql: """
                    INSERT INTO idiom(id, word, pinyin, initials, sense, color, frequency)
                    VALUES (?, ?, 'pin yin', 'abjb', '释义', '褒义', 5)
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
                INSERT INTO exam_scope(id, type, province, enabled)
                VALUES ('scope-national', '国考', NULL, 1), ('scope-province', '省考', '湖北', 0)
                """)
            try db.execute(sql: """
                INSERT INTO idiom_scope(idiomId, scopeId)
                VALUES ('11111111-1111-4111-8111-111111111111', 'scope-national')
                """)
            try db.execute(sql: """
                INSERT INTO user_example(id, imagePath, ocrText, stem, optionA, optionB, optionC, optionD,
                                         myAnswer, isMistake, examType, year, region, createdAt)
                VALUES ('10000000-0000-4000-8000-000000000001', 'images/user.jpg', 'OCR 文本',
                        '录入题题干', '甲', '乙', '丙', '丁', 'B', 1, '国考', 2026, '中央',
                        '2026-11-20T00:00:00Z')
                """)
            try db.execute(sql: """
                INSERT INTO user_example_idiom(userExampleId, idiomId)
                VALUES ('10000000-0000-4000-8000-000000000001',
                        '11111111-1111-4111-8111-111111111111')
                """)
            try db.execute(sql: """
                INSERT INTO mistake(id, userExampleId, imagePath, ocrText, stem,
                                    optionA, optionB, optionC, optionD, myAnswer, reason, tagNames,
                                    examType, year, region, state, createdAt, lastReviewedAt, reviewCount)
                VALUES ('20000000-0000-4000-8000-000000000001'.uppercased(),
                        '10000000-0000-4000-8000-000000000001', 'images/user.jpg', 'OCR 文本',
                        '错题题干', '选项甲', '选项乙', '选项丙', '选项丁', 'B', '搭配不当',
                        '["褒贬误用","望文生义"]', '国考', 2026, '中央', '待复习',
                        '2026-11-20T00:00:00Z', '2026-11-21T00:00:00Z', 1)
                """)
            try db.execute(sql: """
                INSERT INTO mistake(id, imagePath, stem, optionA, optionB, optionC, optionD, tagNames,
                                    state, createdAt, reviewCount)
                VALUES ('20000000-0000-4000-8000-000000000002', NULL, '第二条错题',
                        '甲', '乙', '丙', '丁', '["褒贬误用","搭配不当"]', '已掌握',
                        '2026-11-22T00:00:00Z', 0)
                """)
            try db.execute(sql: """
                INSERT INTO mistake_idiom(mistakeId, idiomId)
                VALUES ('20000000-0000-4000-8000-000000000001'.uppercased(),
                        '11111111-1111-4111-8111-111111111111'),
                       ('20000000-0000-4000-8000-000000000002',
                        '22222222-2222-4222-8222-222222222222')
                """)
            try db.execute(sql: """
                INSERT INTO review_log(id, mistakeId, result, at)
                VALUES ('30000000-0000-4000-8000-000000000001'.uppercased(),
                        '20000000-0000-4000-8000-000000000001', '错', '2026-11-21T00:00:00Z')
                """)
            try db.execute(sql: """
                INSERT INTO idiom_state(idiomId, status, favorite) VALUES
                ('11111111-1111-4111-8111-111111111111', '学过', 1),
                ('22222222-2222-4222-8222-222222222222', '未学', 0)
                """)
            try db.execute(sql: "INSERT INTO settings(key, value) VALUES ('theme', 'dark')")
        }
    }

    private func makeDirectory() throws -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("backup-store-test-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return directory
    }

    private func object(_ json: PackJSON) -> [String: PackJSON] {
        guard case let .object(value) = json else {
            XCTFail("Expected JSON object")
            return [:]
        }
        return value
    }

    private func array(_ json: PackJSON) -> [PackJSON] {
        guard case let .array(value) = json else {
            XCTFail("Expected JSON array")
            return []
        }
        return value
    }

    private func text(_ json: PackJSON?) -> String? {
        guard case let .string(value)? = json else { return nil }
        return value
    }

    private func integer(_ json: PackJSON?) -> Int? {
        guard case let .integer(value)? = json else { return nil }
        return Int(value)
    }
}
