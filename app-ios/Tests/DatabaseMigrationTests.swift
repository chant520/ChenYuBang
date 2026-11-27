import CangyuData
import Foundation
import GRDB
import XCTest

/// 迁移中断模拟错误，文件内两个测试类共用。
fileprivate enum Interruption: Error {
    case simulated
}

/// v1→v2 / v2→v3 迁移与 P20 保存事务（规范 §4.5 / §8.1 / §11.1）。
/// 迁移覆盖正常路径、防御性回填与中断回滚；保存覆盖未标错 / 标错两分支、
/// 多归属全部写入、图片相对路径与 DB 失败后的图片清理。
final class DatabaseMigrationTests: XCTestCase {

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
}

/// v3→v4 未标错录入题归属纯回填（规范 §4.5）：多命中、无命中、悬空与幂等。
final class UserExampleBackfillMigrationTests: XCTestCase {
    func testV3ToV4BackfillsUnlinkedUnmarkedExamples() throws {
        let queue = try DatabaseQueue()
        let migrator = ChenYuBangDatabase.makeMigrator()
        try migrator.migrate(queue, upTo: "v3")
        try queue.write { db in
            try insertIdiom(db, id: "i1", word: "一字千金")
            try insertIdiom(db, id: "i2", word: "画蛇添足")
            try insertIdiom(db, id: "i3", word: "守株待兔")
            try insertExample(db, id: "ex1", stem: exactStem)
            try db.execute(sql: "INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i1')")
            // u1 题干带首尾空白，规则一去空白后全等命中 → i1
            try insertUserExample(db, id: "u1", stem: "  \(exactStem)  ")
            // u2 / u3 分别由题干、myAnswer 完整包含成语词命中规则二
            try insertUserExample(db, id: "u2", stem: "这道题说画蛇添足不对")
            try insertUserExample(db, id: "u3", stem: "无关题干", myAnswer: "答案应为守株待兔")
            // u4 已有归属不动；u5 已标错、u6 均不中，跳过
            try insertUserExample(db, id: "u4", stem: "画蛇添足")
            try db.execute(sql: "INSERT INTO user_example_idiom (userExampleId, idiomId) VALUES ('u4', 'i2')")
            try insertUserExample(db, id: "u5", stem: "画蛇添足", isMistake: true)
            try insertUserExample(db, id: "u6", stem: "完全没有匹配", myAnswer: "无")
        }

        try migrator.migrate(queue, upTo: "v4")

        try queue.read { db in
            XCTAssertTrue(try migrator.appliedIdentifiers(db).contains("v4"))
            XCTAssertEqual(try links(db), ["u1:i1", "u2:i2", "u3:i3", "u4:i2"])
            XCTAssertEqual(try danglingCount(db), 0)
        }
    }

    func testInterruptedV4RollsBackBackfilledAssociations() throws {
        let queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue, upTo: "v3")
        try queue.write { db in
            try insertIdiom(db, id: "i1", word: "一字千金")
            try insertExample(db, id: "ex1", stem: exactStem)
            try db.execute(sql: "INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i1')")
            try insertUserExample(db, id: "u1", stem: "  \(exactStem)  ")
        }

        var failing = DatabaseMigrator()
        failing.registerMigration("v1") { _ in }
        failing.registerMigration("v2") { _ in }
        failing.registerMigration("v3") { _ in }
        failing.registerMigration("v4-interrupted") { db in
            try db.execute(sql: backfillSQL)
            throw Interruption.simulated
        }
        XCTAssertThrowsError(try failing.migrate(queue))

        try queue.read { db in
            XCTAssertFalse(try failing.appliedIdentifiers(db).contains("v4-interrupted"))
            XCTAssertTrue(try links(db).isEmpty)
        }
    }

    func testV4MultipleMatchesWriteAllAssociations() throws {
        let queue = try DatabaseQueue()
        let migrator = ChenYuBangDatabase.makeMigrator()
        try migrator.migrate(queue, upTo: "v3")
        try queue.write { db in
            try insertIdiom(db, id: "i1", word: "一字千金")
            try insertIdiom(db, id: "i2", word: "画蛇添足")
            try insertIdiom(db, id: "i3", word: "守株待兔")
            try insertExample(db, id: "ex1", stem: multiStem)
            try db.execute(sql: "INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i1')")
            try db.execute(sql: "INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i2')")
            // 规则一命中 i1/i2，规则二命中 i2/i3；i2 重复命中只写一行
            try insertUserExample(db, id: "u1", stem: "  \(multiStem)  ")
        }
        try migrator.migrate(queue, upTo: "v4")
        try queue.read { db in
            XCTAssertEqual(try links(db), ["u1:i1", "u1:i2", "u1:i3"])
        }
    }

    func testV4NoMatchLeavesNoAssociation() throws {
        let queue = try DatabaseQueue()
        let migrator = ChenYuBangDatabase.makeMigrator()
        try migrator.migrate(queue, upTo: "v3")
        try queue.write { db in
            try insertIdiom(db, id: "i1", word: "一字千金")
            try insertUserExample(db, id: "u1", stem: "完全没有匹配", myAnswer: "无")
        }
        try migrator.migrate(queue, upTo: "v4")
        try queue.read { db in
            XCTAssertTrue(try links(db).isEmpty)
        }
    }

    func testV4DanglingExampleIdiomDoesNotWriteMissingIdiom() throws {
        let queue = try DatabaseQueue()
        let migrator = ChenYuBangDatabase.makeMigrator()
        try migrator.migrate(queue, upTo: "v3")
        try queue.write { db in
            try insertIdiom(db, id: "i1", word: "一字千金")
            try insertExample(db, id: "ex1", stem: exactStem)
            try db.execute(sql: "INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i1')")
            try db.execute(sql: "INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'ghost')")
            try insertUserExample(db, id: "u1", stem: exactStem)
        }
        try migrator.migrate(queue, upTo: "v4")
        try queue.read { db in
            XCTAssertEqual(try links(db), ["u1:i1"])
            XCTAssertEqual(try danglingCount(db), 0)
        }
    }

    func testV4SecondRunDoesNotAddRows() throws {
        let queue = try DatabaseQueue()
        let migrator = ChenYuBangDatabase.makeMigrator()
        try migrator.migrate(queue, upTo: "v3")
        try queue.write { db in
            try insertIdiom(db, id: "i1", word: "一字千金")
            try insertExample(db, id: "ex1", stem: exactStem)
            try db.execute(sql: "INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex1', 'i1')")
            try insertUserExample(db, id: "u1", stem: "  \(exactStem)  ")
        }
        try migrator.migrate(queue, upTo: "v4")
        let before = try queue.read { try links($0) }
        try queue.write { db in try db.execute(sql: backfillSQL) }
        let after = try queue.read { try links($0) }
        XCTAssertEqual(after, before)
        XCTAssertEqual(after, ["u1:i1"])
    }

}

// 迁移测试共享辅助（文件内可见，两个测试类复用）。
fileprivate extension XCTestCase {

    func insertIdiom(_ db: Database, id: String, word: String) throws {
        try db.execute(
            sql: "INSERT INTO idiom (id, word, pinyin, initials, sense, color, " +
                "pitfalls, synonyms, antonyms, frequency) " +
                "VALUES (?, ?, 'py', 'py', '义', '中', '[]', '[]', '[]', 1)",
            arguments: [id, word]
        )
    }

    func insertExample(_ db: Database, id: String, stem: String) throws {
        try db.execute(
            sql: "INSERT INTO example (id, stem, optionA, optionB, optionC, optionD, answer, explain, " +
                "examType, year, region, questionType) " +
                "VALUES (?, ?, 'A', 'B', 'C', 'D', 'A', '释', '国考', 2026, '京', '单选')",
            arguments: [id, stem]
        )
    }

    func insertUserExample(
        _ db: Database,
        id: String,
        stem: String,
        myAnswer: String? = nil,
        isMistake: Bool = false
    ) throws {
        try db.execute(
            sql: "INSERT INTO user_example (id, stem, myAnswer, isMistake, createdAt) VALUES (?, ?, ?, ?, ?)",
            arguments: [id, stem, myAnswer, isMistake, "2026-11-01T00:00:00Z"]
        )
    }

    func links(_ db: Database) throws -> [String] {
        try String.fetchAll(db, sql: "SELECT userExampleId || ':' || idiomId FROM user_example_idiom ORDER BY 1")
    }

    func danglingCount(_ db: Database) throws -> Int {
        (try Int.fetchOne(
            db,
            sql: "SELECT COUNT(*) FROM user_example_idiom AS link WHERE " +
                "link.userExampleId NOT IN (SELECT id FROM user_example) OR " +
                "link.idiomId NOT IN (SELECT id FROM idiom)"
        )) ?? 0
    }

    var exactStem: String { "请补全：一行白鹭上青天" }
    var multiStem: String { "画蛇添足与守株待兔" }

    var backfillSQL: String {
        """
        INSERT OR IGNORE INTO user_example_idiom(userExampleId, idiomId)
        SELECT ue.id, ei.idiomId
        FROM user_example AS ue
        JOIN example AS ex ON trim(ue.stem, char(9,10,13,32,12288)) = ex.stem
        JOIN example_idiom AS ei ON ei.exampleId = ex.id
        JOIN idiom AS i ON i.id = ei.idiomId
        WHERE ue.isMistake = 0
          AND NOT EXISTS (SELECT 1 FROM user_example_idiom AS link WHERE link.userExampleId = ue.id)
        UNION
        SELECT ue.id, i.id
        FROM user_example AS ue
        JOIN idiom AS i
          ON i.word <> ''
         AND (instr(ue.stem, i.word) > 0 OR instr(coalesce(ue.myAnswer, ''), i.word) > 0)
        WHERE ue.isMistake = 0
          AND NOT EXISTS (SELECT 1 FROM user_example_idiom AS link WHERE link.userExampleId = ue.id)
        """
    }
}
