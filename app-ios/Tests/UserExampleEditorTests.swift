import CangyuData
import Foundation
import GRDB
import XCTest

/// P10 编辑与删除（工作包 2.8，规范 §8.1）：同一事务内写正文并刷新 `mistake` 快照，
/// 归属整批重写、复习字段不受扰动；删除分两支——标错的只移出错题本并保留录入题与原图，
/// 未标错的删整题连同本机原图。校验一律先于写库。
final class UserExampleEditorTests: XCTestCase {
    func testSaveEditRewritesBodyAndIdiomsAndRefreshesMistakeSnapshot() throws {
        let env = try makeEnv(isMistake: true)
        let reader = MistakeLibraryReader(queue: env.queue)
        try env.queue.write { db in
            try db.execute(
                sql: "UPDATE mistake SET state = '已掌握', reviewCount = 3 WHERE userExampleId = ?",
                arguments: [env.userExampleID]
            )
            try db.execute(sql: """
                INSERT INTO review_log (id, mistakeId, result, at)
                SELECT 'rl', id, '对', '2024-01-01T00:00:00Z' FROM mistake WHERE userExampleId = ?
                """, arguments: [env.userExampleID])
        }
        let before = try snapshot(env.queue, exampleID: env.userExampleID)

        try UserExampleEditor(queue: env.queue, dataDirectory: env.dataDirectory).saveEdit(
            makeInput(
                id: env.userExampleID,
                stem: " 改过的题干 ",
                options: ["甲", "乙", nil, "丁"],
                answer: "C",
                examType: "省考",
                year: 2024,
                reason: "混淆近义",
                tags: ["高频", "易混词"],
                idioms: [LinkedIdiom(id: "i3", word: "三日打鱼"), LinkedIdiom(id: "i1", word: "按部就班")]
            )
        )

        let detail = try XCTUnwrap(reader.userExample(id: env.userExampleID))
        XCTAssertEqual(detail.stem, "改过的题干")
        XCTAssertEqual(detail.options, ["甲", "乙", nil, "丁"])
        XCTAssertEqual(detail.myAnswer, "C")
        XCTAssertEqual(detail.examType, "省考")
        XCTAssertEqual(detail.year, 2024)
        XCTAssertEqual(detail.isMistake, true)
        XCTAssertEqual(detail.reason, "混淆近义")
        XCTAssertEqual(detail.tagNames, ["高频", "易混词"])
        XCTAssertEqual(detail.idioms.map(\.id), ["i3", "i1"])
        try env.queue.read { db in
            let mistakeID = try XCTUnwrap(
                try String.fetchOne(db, sql: "SELECT id FROM mistake WHERE userExampleId = ?",
                                    arguments: [env.userExampleID])
            )
            XCTAssertEqual(
                try String.fetchAll(
                    db,
                    sql: "SELECT idiomId FROM mistake_idiom WHERE mistakeId = ? ORDER BY rowid",
                    arguments: [mistakeID]
                ),
                ["i3", "i1"]
            )
            // 空选项在 user_example 是 NULL、在 mistake 是空串（快照列 NOT NULL）。
            XCTAssertEqual(
                try String.fetchOne(db, sql: "SELECT optionC FROM mistake WHERE id = ?", arguments: [mistakeID]),
                ""
            )
            XCTAssertEqual(
                try String.fetchOne(db, sql: "SELECT stem FROM mistake WHERE id = ?", arguments: [mistakeID]),
                "改过的题干"
            )
            XCTAssertEqual(
                try String.fetchOne(db, sql: "SELECT myAnswer FROM mistake WHERE id = ?", arguments: [mistakeID]),
                "C"
            )
            XCTAssertEqual(
                try String.fetchOne(db, sql: "SELECT examType FROM mistake WHERE id = ?", arguments: [mistakeID]),
                "省考"
            )
            XCTAssertEqual(
                try Int.fetchOne(db, sql: "SELECT year FROM mistake WHERE id = ?", arguments: [mistakeID]),
                2024
            )
            XCTAssertEqual(
                try String.fetchOne(db, sql: "SELECT reason FROM mistake WHERE id = ?", arguments: [mistakeID]),
                "混淆近义"
            )
            // 掌握状态与复习进度不属于编辑范围（规范 §8.1）：编辑只动正文快照。
            let after = try snapshot(db, exampleID: env.userExampleID)
            XCTAssertEqual(after.mistakeState, "已掌握")
            XCTAssertEqual(after.mistakeReviewCount, 3)
            XCTAssertEqual(after.mistakeState, before.mistakeState)
            XCTAssertEqual(after.mistakeReviewCount, before.mistakeReviewCount)
            XCTAssertEqual(after.mistakeCreatedAt, before.mistakeCreatedAt)
            XCTAssertEqual(after.mistakeID, before.mistakeID)
            XCTAssertEqual(after.exampleCreatedAt, before.exampleCreatedAt)
            XCTAssertEqual(after.exampleImagePath, before.exampleImagePath)
            XCTAssertEqual(after.exampleOcrText, before.exampleOcrText)
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM review_log"), 1)
        }
    }

    /// 重复归属按页面顺序去重后落库：`mistake_idiom` 是复合主键，不去重会直接撞约束。
    func testSaveEditDeduplicatesRepeatedIdioms() throws {
        let env = try makeEnv(isMistake: true)
        let one = LinkedIdiom(id: "i2", word: "墨守成规")

        try UserExampleEditor(queue: env.queue, dataDirectory: env.dataDirectory)
            .saveEdit(makeInput(id: env.userExampleID, idioms: [one, one]))

        let detail = try XCTUnwrap(MistakeLibraryReader(queue: env.queue).userExample(id: env.userExampleID))
        XCTAssertEqual(detail.idioms.map(\.id), ["i2"])
        try env.queue.read { db in
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM user_example_idiom"), 1)
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake_idiom"), 1)
        }
    }

    func testSaveEditOnUnmarkedExampleWritesNoMistakeRows() throws {
        let env = try makeEnv(isMistake: false)
        let reader = MistakeLibraryReader(queue: env.queue)

        try UserExampleEditor(queue: env.queue, dataDirectory: env.dataDirectory).saveEdit(
            makeInput(id: env.userExampleID, stem: "只改正文", reason: "不该落库的错因")
        )

        let detail = try XCTUnwrap(reader.userExample(id: env.userExampleID))
        XCTAssertEqual(detail.stem, "只改正文")
        XCTAssertEqual(detail.isMistake, false)
        XCTAssertNil(detail.mistakeState)
        try env.queue.read { db in
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake"), 0)
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake_idiom"), 0)
            XCTAssertEqual(
                try String.fetchOne(db, sql: "SELECT stem FROM user_example WHERE id = ?",
                                    arguments: [env.userExampleID]),
                "只改正文"
            )
        }
    }

    func testSaveEditRejectsInvalidInputBeforeTouchingDatabase() throws {
        let env = try makeEnv(isMistake: true)
        let editor = UserExampleEditor(queue: env.queue, dataDirectory: env.dataDirectory)

        XCTAssertThrowsError(try editor.saveEdit(makeInput(id: env.userExampleID, stem: "   "))) {
            XCTAssertEqual($0 as? UserExampleEditError, .emptyStem)
        }
        XCTAssertThrowsError(try editor.saveEdit(makeInput(id: env.userExampleID, idioms: []))) {
            XCTAssertEqual($0 as? UserExampleEditError, .noIdioms)
        }
        XCTAssertThrowsError(try editor.saveEdit(makeInput(id: env.userExampleID, answer: "E"))) {
            XCTAssertEqual($0 as? UserExampleEditError, .invalidAnswer)
        }
        XCTAssertThrowsError(try editor.saveEdit(makeInput(id: env.userExampleID, tags: ["超过八个字的标签名"]))) {
            XCTAssertEqual($0 as? UserExampleEditError, .invalidTag)
        }
        XCTAssertThrowsError(try editor.saveEdit(makeInput(id: "missing-id", stem: "不存在的题"))) {
            XCTAssertEqual($0 as? UserExampleEditError, .notFound)
        }
        try env.queue.read { db in
            XCTAssertEqual(
                try String.fetchOne(db, sql: "SELECT stem FROM user_example WHERE id = ?",
                                    arguments: [env.userExampleID]),
                "下列各句中，加点的成语使用不恰当的一项是……"
            )
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake"), 1)
        }
    }

    func testFailedMistakeSnapshotUpdateRollsBackWholeTransaction() throws {
        let env = try makeEnv(isMistake: true)
        try env.queue.write { db in
            try db.execute(sql: """
                CREATE TRIGGER fail_mistake_update BEFORE UPDATE ON mistake
                BEGIN SELECT RAISE(ABORT, 'boom'); END;
                """)
        }
        let editor = UserExampleEditor(queue: env.queue, dataDirectory: env.dataDirectory)

        XCTAssertThrowsError(
            try editor.saveEdit(makeInput(
                id: env.userExampleID,
                stem: "改到一半失败",
                idioms: [LinkedIdiom(id: "i3", word: "三日打鱼")]
            ))
        )
        try env.queue.read { db in
            XCTAssertEqual(
                try String.fetchOne(db, sql: "SELECT stem FROM user_example WHERE id = ?",
                                    arguments: [env.userExampleID]),
                "下列各句中，加点的成语使用不恰当的一项是……"
            )
            XCTAssertEqual(
                try String.fetchAll(
                    db,
                    sql: "SELECT idiomId FROM user_example_idiom WHERE userExampleId = ? ORDER BY rowid",
                    arguments: [env.userExampleID]
                ),
                ["i1", "i2"]
            )
        }
    }

    func testDeleteMistakeKeepsExampleAndImageAndFlipsFlag() throws {
        let env = try makeEnv(isMistake: true)
        let reader = MistakeLibraryReader(queue: env.queue)
        try env.queue.write { db in
            try db.execute(sql: """
                INSERT INTO review_log (id, mistakeId, result, at)
                SELECT 'rl', id, '错', '2024-01-01T00:00:00Z' FROM mistake WHERE userExampleId = ?
                """, arguments: [env.userExampleID])
        }

        try UserExampleEditor(queue: env.queue, dataDirectory: env.dataDirectory)
            .deleteMistake(userExampleID: env.userExampleID)

        let detail = try XCTUnwrap(reader.userExample(id: env.userExampleID))
        XCTAssertFalse(detail.isMistake)
        XCTAssertNil(detail.mistakeState)
        try env.queue.read { db in
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake"), 0)
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake_idiom"), 0)
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM review_log"), 0)
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM user_example"), 1)
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM user_example_idiom"), 2)
            XCTAssertEqual(
                try Bool.fetchOne(db, sql: "SELECT isMistake FROM user_example WHERE id = ?",
                                  arguments: [env.userExampleID]),
                false
            )
        }
        XCTAssertTrue(FileManager.default.fileExists(atPath: env.imagePath.path))
    }

    func testDeleteMistakeRejectsUnknownExample() throws {
        let env = try makeEnv(isMistake: true)

        XCTAssertThrowsError(
            try UserExampleEditor(queue: env.queue, dataDirectory: env.dataDirectory)
                .deleteMistake(userExampleID: "missing-id")
        ) { XCTAssertEqual($0 as? UserExampleEditError, .notFound) }
        try env.queue.read { db in
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake"), 1)
        }
    }

    func testDeleteUserExampleRemovesRowAssociationAndLocalImage() throws {
        let env = try makeEnv(isMistake: false)
        let editor = UserExampleEditor(queue: env.queue, dataDirectory: env.dataDirectory)

        try editor.deleteUserExample(id: env.userExampleID)

        try env.queue.read { db in
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM user_example"), 0)
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM user_example_idiom"), 0)
        }
        XCTAssertFalse(FileManager.default.fileExists(atPath: env.imagePath.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: env.dataDirectory.path))
    }

    func testDeleteUserExampleToleratesMissingImageFileAndRejectsUnknownId() throws {
        let env = try makeEnv(isMistake: false)
        try FileManager.default.removeItem(at: env.imagePath)
        let editor = UserExampleEditor(queue: env.queue, dataDirectory: env.dataDirectory)

        XCTAssertNoThrow(try editor.deleteUserExample(id: env.userExampleID))
        try env.queue.read { db in
            XCTAssertEqual(try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM user_example"), 0)
        }
        XCTAssertThrowsError(try editor.deleteUserExample(id: env.userExampleID)) {
            XCTAssertEqual($0 as? UserExampleEditError, .notFound)
        }
        try env.queue.read { db in
            XCTAssertEqual(
                try String.fetchOne(db, sql: "SELECT stem FROM user_example"),
                "下列各句中，加点的成语使用不恰当的一项是……"
            )
        }
    }
}

/// 用例共用的建库与输入装配（放扩展里，与用例体分开）。
private extension UserExampleEditorTests {

    /// 一台临时数据目录 + 一条已落库的录入题，形状与 App 注入完全一致：
    /// 保存器拿 `images` 子目录、编辑器拿数据根目录（相对路径 `images/<id>.jpg` 的解析基准）。
    struct Env {
        let queue: DatabaseQueue
        let dataDirectory: URL
        let userExampleID: String
        let imagePath: URL
    }

    struct MutableColumns: Equatable {
        let mistakeID: String?
        let mistakeState: String?
        let mistakeReviewCount: Int?
        let mistakeCreatedAt: String?
        let exampleCreatedAt: String
        let exampleImagePath: String?
        let exampleOcrText: String?
    }

    private func snapshot(_ db: Database, exampleID: String) throws -> MutableColumns {
        MutableColumns(
            mistakeID: try String.fetchOne(db, sql: "SELECT id FROM mistake WHERE userExampleId = ?",
                                           arguments: [exampleID]),
            mistakeState: try String.fetchOne(db, sql: "SELECT state FROM mistake WHERE userExampleId = ?",
                                              arguments: [exampleID]),
            mistakeReviewCount: try Int.fetchOne(db, sql: "SELECT reviewCount FROM mistake WHERE userExampleId = ?",
                                                 arguments: [exampleID]),
            mistakeCreatedAt: try String.fetchOne(db, sql: "SELECT createdAt FROM mistake WHERE userExampleId = ?",
                                                  arguments: [exampleID]),
            exampleCreatedAt: try String.fetchOne(
                db, sql: "SELECT createdAt FROM user_example WHERE id = ?", arguments: [exampleID]
            ) ?? "",
            exampleImagePath: try String.fetchOne(db, sql: "SELECT imagePath FROM user_example WHERE id = ?",
                                                  arguments: [exampleID]),
            exampleOcrText: try String.fetchOne(db, sql: "SELECT ocrText FROM user_example WHERE id = ?",
                                                arguments: [exampleID])
        )
    }

    private func makeEnv(isMistake: Bool) throws -> Env {
        let queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        try seedIdioms(queue)
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("chenyubang-editor-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: root) }
        let images = root.appendingPathComponent("images", isDirectory: true)
        let source = root.appendingPathComponent("source.jpg")
        try Data([0xFF, 0xD8, 0xFF, 0xD9]).write(to: source)
        let saved = try UserExampleSaver(queue: queue, imagesDirectory: images).save(
            CaptureSaveInput(
                imageURL: source,
                ocrText: "识别原文",
                stem: "下列各句中，加点的成语使用不恰当的一项是……",
                optionA: "新厂长按部就班",
                optionB: "他墨守成规",
                myAnswer: "B",
                isMistake: isMistake,
                reason: isMistake ? "望文生义" : "",
                tagNames: isMistake ? ["易混词"] : [],
                examType: "国考",
                year: 2023,
                idioms: [SavedIdiom(id: "i1", word: "按部就班"), SavedIdiom(id: "i2", word: "墨守成规")],
                createdAt: Date(timeIntervalSince1970: 0)
            )
        )
        return Env(
            queue: queue,
            dataDirectory: root,
            userExampleID: saved.userExampleID,
            imagePath: images.appendingPathComponent("\(saved.userExampleID).jpg")
        )
    }

    private func seedIdioms(_ queue: DatabaseQueue) throws {
        try queue.write { db in
            for (id, word) in [("i1", "按部就班"), ("i2", "墨守成规"), ("i3", "三日打鱼")] {
                try db.execute(sql: """
                    INSERT INTO idiom (id, word, pinyin, initials, sense, color, frequency)
                    VALUES (?, ?, '', '', '', '', 1)
                    """, arguments: [id, word])
            }
        }
    }

    private func makeInput(
        id: String,
        stem: String = "改过的题干",
        options: [String?] = ["甲", "乙", "丙", "丁"],
        answer: String? = "C",
        examType: String? = "国考",
        year: Int? = 2024,
        reason: String? = "望文生义",
        tags: [String] = ["高频"],
        idioms: [LinkedIdiom] = [LinkedIdiom(id: "i1", word: "按部就班")]
    ) -> UserExampleEditInput {
        UserExampleEditInput(
            id: id,
            stem: stem,
            options: options,
            myAnswer: answer,
            examType: examType,
            year: year,
            reason: reason,
            tagNames: tags,
            idioms: idioms
        )
    }
}
