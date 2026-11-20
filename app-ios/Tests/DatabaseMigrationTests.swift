import CangyuData
import Foundation
import GRDB
import XCTest

/// v1→v2 / v2→v3 迁移与 P20 保存事务（规范 §4.5 / §8.1 / §11.1）。
/// 迁移覆盖正常路径、防御性回填与中断回滚；保存覆盖未标错 / 标错两分支、
/// 多归属全部写入、图片相对路径与 DB 失败后的图片清理。
final class DatabaseMigrationTests: XCTestCase {
    private enum Interruption: Error {
        case simulated
    }

    func testV1ToV2AddsUserExampleAndColumnPreservingRows() throws {
        let queue = try DatabaseQueue()
        let migrator = ChenYuBangDatabase.makeMigrator()
        try migrator.migrate(queue, upTo: "v1")
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO mistake
                    (id, stem, optionA, optionB, optionC, optionD, tagNames, state, createdAt, reviewCount)
                VALUES ('m1', '旧错题', 'A', 'B', 'C', 'D', '[]', '待复习', '2026-11-01T00:00:00Z', 0)
                """)
        }

        try migrator.migrate(queue, upTo: "v2")

        try queue.read { db in
            XCTAssertTrue(try migrator.appliedIdentifiers(db).contains("v2"))
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake"), 1)
            XCTAssertTrue(try db.columns(in: "mistake").map(\.name).contains("userExampleId"))
            XCTAssertNil(try String.fetchOne(db, sql: "SELECT userExampleId FROM mistake WHERE id = 'm1'"))
            XCTAssertEqual(try db.columns(in: "user_example").map(\.name).count, 14)
            let indexes = try String.fetchAll(db, sql: "SELECT name FROM pragma_index_list('mistake')")
            XCTAssertTrue(indexes.contains("idx_mistake_state"))
            XCTAssertTrue(indexes.contains("idx_mistake_created"))
        }

        // 外键可解析：录入题插入后错题行可 JOIN 回录入题；isMistake 默认 false
        try queue.write { db in
            try db.execute(
                sql: "INSERT INTO user_example (id, stem, createdAt) VALUES ('u1', '新录入题', '2026-11-20T00:00:00Z')"
            )
            try db.execute(sql: "UPDATE mistake SET userExampleId = 'u1' WHERE id = 'm1'")
        }
        try queue.read { db in
            XCTAssertEqual(
                try Int.fetchOne(
                    db,
                    sql: "SELECT COUNT(*) FROM mistake JOIN user_example ON user_example.id = mistake.userExampleId"
                ),
                1
            )
            XCTAssertEqual(try Bool.fetchOne(db, sql: "SELECT isMistake FROM user_example WHERE id = 'u1'"), false)
        }
    }

    func testInterruptedMigrationRollsBack() throws {
        let queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue, upTo: "v1")

        // v1 已登记，会被跳过；v2 在事务内建表后抛出，迁移整体必须回滚
        var failing = DatabaseMigrator()
        failing.registerMigration("v1") { _ in }
        failing.registerMigration("v2-interrupted") { db in
            try db.create(table: "rollback_probe") { t in
                t.column("id", .text)
            }
            throw Interruption.simulated
        }
        XCTAssertThrowsError(try failing.migrate(queue))

        try queue.read { db in
            XCTAssertFalse(try failing.appliedIdentifiers(db).contains("v2-interrupted"))
            XCTAssertFalse(try db.tableExists("rollback_probe"))
            XCTAssertFalse(try db.tableExists("user_example"))
        }
    }

    func testV2ToV3CreatesAssociationTableAndBackfillsMistakeLinks() throws {
        let queue = try DatabaseQueue()
        let migrator = ChenYuBangDatabase.makeMigrator()
        try migrator.migrate(queue, upTo: "v2")
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO user_example (id, stem, createdAt)
                VALUES ('u1', '录入题一', '2026-11-01T00:00:00Z'),
                       ('u2', '录入题二', '2026-11-02T00:00:00Z')
                """)
            try db.execute(sql: """
                INSERT INTO mistake
                    (id, userExampleId, stem, optionA, optionB, optionC, optionD,
                     tagNames, state, createdAt)
                VALUES ('m1', 'u1', '旧错题一', 'A', 'B', 'C', 'D', '[]', '待复习', '2026-11-01T00:00:00Z'),
                       ('m2', NULL, '无录入题', 'A', 'B', 'C', 'D', '[]', '待复习', '2026-11-01T00:00:00Z'),
                       ('m3', 'u2', '旧错题二', 'A', 'B', 'C', 'D', '[]', '待复习', '2026-11-02T00:00:00Z')
                """)
            try db.execute(sql: """
                INSERT INTO mistake_idiom (mistakeId, idiomId)
                VALUES ('m1', 'i1'), ('m1', 'i2'), ('m2', 'i3'), ('m3', 'i2')
                """)
        }

        try migrator.migrate(queue)

        try queue.read { db in
            XCTAssertTrue(try migrator.appliedIdentifiers(db).contains("v3"))
            XCTAssertTrue(try db.tableExists("user_example_idiom"))
            XCTAssertEqual(
                try String.fetchAll(
                    db,
                    sql: "SELECT name FROM pragma_table_info('user_example_idiom') WHERE pk > 0 ORDER BY pk"
                ),
                ["userExampleId", "idiomId"]
            )
            let indexes = try String.fetchAll(db, sql: "SELECT name FROM pragma_index_list('user_example_idiom')")
            XCTAssertTrue(indexes.contains("idx_user_example_idiom_example"))
            XCTAssertTrue(indexes.contains("idx_user_example_idiom_idiom"))
            XCTAssertEqual(
                try String.fetchAll(
                    db,
                    sql: "SELECT userExampleId || ':' || idiomId FROM user_example_idiom ORDER BY 1"
                ),
                ["u1:i1", "u1:i2", "u2:i2"]
            )
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake"), 3)
        }
    }

    func testInterruptedV3RollsBackAssociationTable() throws {
        let queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue, upTo: "v2")

        var failing = DatabaseMigrator()
        failing.registerMigration("v1") { _ in }
        failing.registerMigration("v2") { _ in }
        failing.registerMigration("v3-interrupted") { db in
            try db.create(table: "user_example_idiom") { t in
                t.column("userExampleId", .text)
                t.column("idiomId", .text)
            }
            throw Interruption.simulated
        }
        XCTAssertThrowsError(try failing.migrate(queue))

        try queue.read { db in
            XCTAssertFalse(try failing.appliedIdentifiers(db).contains("v3-interrupted"))
            XCTAssertFalse(try db.tableExists("user_example_idiom"))
        }
    }

    func testSaveWithoutMistakeWritesAllAssociationsAndRelativeImagePath() throws {
        let queue = try makeQueue()
        try seedIdioms(queue)
        let images = try makeImagesDirectory()
        let image = try makeImageFile(in: images)
        let saver = UserExampleSaver(queue: queue, imagesDirectory: images)
        let idioms = [SavedIdiom(id: "i1", word: "按部就班"), SavedIdiom(id: "i2", word: "墨守成规")]

        let saved = try saver.save(makeInput(image: image, idioms: idioms))

        XCTAssertNotNil(UUID(uuidString: saved.userExampleID))
        XCTAssertNil(saved.mistakeID)
        XCTAssertEqual(saved.idioms, idioms)
        let target = images.appendingPathComponent("images/\(saved.userExampleID).jpg")
        XCTAssertTrue(FileManager.default.fileExists(atPath: target.path))
        try queue.read { db in
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM user_example"), 1)
            XCTAssertEqual(
                try String.fetchOne(db, sql: "SELECT imagePath FROM user_example WHERE id = ?",
                                    arguments: [saved.userExampleID]),
                "images/\(saved.userExampleID).jpg"
            )
            XCTAssertEqual(try Bool.fetchOne(db, sql: "SELECT isMistake FROM user_example"), false)
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake"), 0)
            XCTAssertEqual(
                try String.fetchAll(
                    db,
                    sql: "SELECT idiomId FROM user_example_idiom WHERE userExampleId = ? ORDER BY idiomId",
                    arguments: [saved.userExampleID]
                ),
                ["i1", "i2"]
            )
            XCTAssertEqual(
                try String.fetchOne(db, sql: "SELECT createdAt FROM user_example"),
                "1970-01-01T00:00:00Z"
            )
        }
    }

    func testSaveWithMistakeWritesMistakeAndAllIdioms() throws {
        let queue = try makeQueue()
        try seedIdioms(queue)
        let images = try makeImagesDirectory()
        let image = try makeImageFile(in: images)
        let saver = UserExampleSaver(queue: queue, imagesDirectory: images)
        let idioms = [SavedIdiom(id: "i1", word: "按部就班"), SavedIdiom(id: "i2", word: "墨守成规")]

        let saved = try saver.save(
            makeInput(image: image, idioms: idioms, isMistake: true, tags: ["易混词", "高频"])
        )

        let mistakeID = try XCTUnwrap(saved.mistakeID)
        try queue.read { db in
            XCTAssertEqual(try Bool.fetchOne(db, sql: "SELECT isMistake FROM user_example"), true)
            let select = "SELECT %@ FROM mistake WHERE id = ?"
            func value(_ column: String) throws -> String? {
                try String.fetchOne(db, sql: String(format: select, column), arguments: [mistakeID])
            }
            XCTAssertEqual(try value("userExampleId"), saved.userExampleID)
            XCTAssertEqual(try value("state"), "待复习")
            XCTAssertEqual(try value("reason"), "望文生义")
            XCTAssertEqual(try value("myAnswer"), "B")
            XCTAssertEqual(try value("examType"), "国考")
            XCTAssertEqual(try value("imagePath"), "images/\(saved.userExampleID).jpg")
            XCTAssertEqual(
                try Int.fetchOne(db, sql: "SELECT year FROM mistake WHERE id = ?", arguments: [mistakeID]),
                2023
            )
            XCTAssertEqual(
                try Int.fetchOne(db, sql: "SELECT reviewCount FROM mistake WHERE id = ?", arguments: [mistakeID]),
                0
            )
            let tagsData = try XCTUnwrap(try value("tagNames")?.data(using: .utf8))
            XCTAssertEqual(try JSONDecoder().decode([String].self, from: tagsData), ["易混词", "高频"])
            XCTAssertEqual(
                try String.fetchAll(
                    db,
                    sql: "SELECT idiomId FROM mistake_idiom WHERE mistakeId = ? ORDER BY idiomId",
                    arguments: [mistakeID]
                ),
                ["i1", "i2"]
            )
        }
    }

    func testDatabaseFailureRollsBackRowsAndRemovesCopiedImage() throws {
        let queue = try makeQueue()
        try seedIdioms(queue)
        let images = try makeImagesDirectory()
        let image = try makeImageFile(in: images)
        try queue.write { db in
            try db.execute(sql: """
                CREATE TRIGGER fail_user_example_idiom BEFORE INSERT ON user_example_idiom
                BEGIN SELECT RAISE(ABORT, 'boom'); END;
                """)
        }
        let saver = UserExampleSaver(queue: queue, imagesDirectory: images)

        XCTAssertThrowsError(
            try saver.save(makeInput(image: image, idioms: [SavedIdiom(id: "i1", word: "按部就班")]))
        ) { error in
            XCTAssertEqual(error as? CaptureSaveError, .database)
        }
        try queue.read { db in
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM user_example"), 0)
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM user_example_idiom"), 0)
        }
        let imageDirectory = images.appendingPathComponent("images").path
        let savedImages = try FileManager.default.contentsOfDirectory(atPath: imageDirectory)
        XCTAssertTrue(savedImages.isEmpty)
    }

    func testSaveRejectsEmptyAssociationsEmptyStemAndOversizeTagBeforeCopy() throws {
        let queue = try makeQueue()
        let images = try makeImagesDirectory()
        let image = try makeImageFile(in: images)
        let saver = UserExampleSaver(queue: queue, imagesDirectory: images)

        XCTAssertThrowsError(try saver.save(makeInput(image: image, idioms: []))) { error in
            XCTAssertEqual(error as? CaptureSaveError, .noIdioms)
        }
        var emptyStem = makeInput(image: image, idioms: [SavedIdiom(id: "i1", word: "按部就班")])
        emptyStem = CaptureSaveInput(
            imageURL: emptyStem.imageURL, ocrText: "", stem: "  ", isMistake: false,
            idioms: emptyStem.idioms, createdAt: emptyStem.createdAt
        )
        XCTAssertThrowsError(try saver.save(emptyStem)) { error in
            XCTAssertEqual(error as? CaptureSaveError, .emptyStem)
        }
        let oversize = makeInput(
            image: image,
            idioms: [SavedIdiom(id: "i1", word: "按部就班")],
            tags: ["超过八个字的标签名"]
        )
        XCTAssertThrowsError(try saver.save(oversize)) { error in
            XCTAssertEqual(error as? CaptureSaveError, .invalidTag)
        }
        XCTAssertFalse(FileManager.default.fileExists(atPath: images.appendingPathComponent("images").path))
    }

    private func makeQueue() throws -> DatabaseQueue {
        let queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        return queue
    }

    private func seedIdioms(_ queue: DatabaseQueue) throws {
        try queue.write { db in
            for (id, word) in [("i1", "按部就班"), ("i2", "墨守成规")] {
                try db.execute(sql: """
                    INSERT INTO idiom (id, word, pinyin, initials, sense, color, frequency)
                    VALUES (?, ?, '', '', '', '', 1)
                    """, arguments: [id, word])
            }
        }
    }

    private func makeImagesDirectory() throws -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("chenyubang-images-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return directory
    }

    private func makeImageFile(in directory: URL) throws -> URL {
        let url = directory.appendingPathComponent("source-\(UUID().uuidString).jpg")
        try Data([0xFF, 0xD8, 0xFF, 0xD9]).write(to: url)
        return url
    }

    private func makeInput(
        image: URL,
        idioms: [SavedIdiom],
        isMistake: Bool = false,
        tags: [String] = []
    ) -> CaptureSaveInput {
        CaptureSaveInput(
            imageURL: image,
            ocrText: "下列各句中，加点的成语使用不恰当的一项是……\nA．新厂长按部就班\nB．他墨守成规",
            stem: "下列各句中，加点的成语使用不恰当的一项是……",
            optionA: "新厂长按部就班",
            optionB: "他墨守成规",
            myAnswer: "B",
            isMistake: isMistake,
            reason: isMistake ? "望文生义" : "",
            tagNames: tags,
            examType: "国考",
            year: 2023,
            idioms: idioms,
            createdAt: Date(timeIntervalSince1970: 0)
        )
    }
}
