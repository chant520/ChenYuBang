import CangyuData
import Foundation
import GRDB
import XCTest

/// P20 保存事务（规范 §4.5 / §8.1）：未标错 / 标错两分支、多归属全部写入、
/// 图片相对路径与 DB 失败后的图片清理、入口校验先于复制。
final class UserExampleSaverTests: XCTestCase {
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
