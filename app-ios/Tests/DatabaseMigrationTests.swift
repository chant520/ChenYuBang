import CangyuData
import GRDB
import XCTest

/// v1→v2 迁移（规范 §4.5 v2 方案 / §11.1）：user_example 建表 + mistake.userExampleId 增列，
/// 覆盖正常路径（旧行与索引保留、外键可解析）与中断回滚（迁移事务未提交则整体撤销）。
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

        try migrator.migrate(queue)

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
            try db.create(table: "rollback_probe")
            throw Interruption.simulated
        }
        XCTAssertThrowsError(try failing.migrate(queue))

        try queue.read { db in
            XCTAssertFalse(try failing.appliedIdentifiers(db).contains("v2-interrupted"))
            XCTAssertFalse(try db.tableExists("rollback_probe"))
            XCTAssertFalse(try db.tableExists("user_example"))
        }
    }
}
