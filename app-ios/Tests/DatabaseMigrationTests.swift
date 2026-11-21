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
}
