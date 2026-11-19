import CangyuData
import GRDB
import XCTest

final class ExamScopeStoreTests: XCTestCase {
    private var queue: DatabaseQueue!
    private var store: ExamScopeStore!

    override func setUpWithError() throws {
        queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        store = ExamScopeStore(queue: queue)
    }

    override func tearDownWithError() throws {
        store = nil
        queue = nil
    }

    func testFreshDefaultsHaveThreeChecksAndNoProvince() throws {
        let preferences = try store.load()
        XCTAssertFalse(preferences.complete)
        XCTAssertTrue(preferences.scope.national)
        XCTAssertTrue(preferences.scope.provincial)
        XCTAssertTrue(preferences.scope.institution)
        XCTAssertTrue(preferences.scope.provinces.isEmpty)
        XCTAssertEqual(preferences.scope.description, "国考 · 省考 · 事业编")
    }

    func testEnterOrSkipPersistsCurrentSelectionIncludingAllCancelled() throws {
        let selection = ExamScopeSelection(national: false, provincial: false, institution: false)
        try store.save(scope: selection, complete: true)
        XCTAssertEqual(try store.load(), LaunchPreferences(complete: true, scope: selection))
        XCTAssertTrue(try store.load().scope.isEmpty)
        try store.save(scope: ExamScopeSelection(), complete: false)
        XCTAssertTrue(try store.load().complete)
    }

    func testScopeEditDoesNotCompleteFirstLaunch() throws {
        let selection = ExamScopeSelection(national: false, provinces: ["广东", "浙江"])
        try store.save(scope: selection)
        XCTAssertEqual(try store.load(), LaunchPreferences(complete: false, scope: selection))
    }

    func testUUIDReuseDisableWithoutDeletionAndProvinceSelectionRetention() throws {
        let selected = ExamScopeSelection(provinces: ["广东", "浙江"])
        try store.save(scope: selected, complete: true)
        let originalIDs = try scopeIDs()
        var disabled = selected
        disabled.national = false
        disabled.provincial = false
        try store.save(scope: disabled)
        XCTAssertEqual(try scopeIDs(), originalIDs)
        XCTAssertEqual(try store.load().scope, disabled)
        XCTAssertEqual(try enabledProvinceCount(), 0)
        try store.save(scope: selected)
        XCTAssertEqual(try scopeIDs(), originalIDs)
        XCTAssertEqual(try enabledProvinceCount(), 2)
        try store.save(scope: ExamScopeSelection(provinces: ["广东"]))
        XCTAssertEqual(try scopeIDs(), originalIDs)
        XCTAssertEqual(try store.load().scope.provinces, ["广东"])
        XCTAssertEqual(try enabledProvinceCount(), 1)
    }

    func testReusesExistingUUIDAndForcesTeacherDisabled() throws {
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO exam_scope (id, type, province, enabled) VALUES
                ('national-existing', '国考', NULL, 1), ('teacher-existing', '教师编', NULL, 1)
                """)
            try db.execute(sql: "INSERT INTO idiom_scope (idiomId, scopeId) VALUES ('fixture', 'national-existing')")
        }
        try store.save(scope: ExamScopeSelection())
        let rows = try queue.read { db in try Row.fetchAll(db, sql: "SELECT * FROM exam_scope WHERE type = '教师编'") }
        XCTAssertTrue(rows.allSatisfy { row in
            let enabled: Bool = row["enabled"]
            return !enabled
        })
        XCTAssertTrue(try scopeIDs().contains("national-existing"))
        XCTAssertTrue(try scopeIDs().contains("teacher-existing"))
        XCTAssertEqual(try queue.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM idiom_scope") }, 1)
    }

    func testFailureRollsBackScopesProvinceSettingAndCompletionThenCanRetry() throws {
        let initial = ExamScopeSelection(provinces: ["浙江"])
        try store.save(scope: initial)
        let originalIDs = try scopeIDs()
        try queue.write { db in
            try db.execute(sql: """
                CREATE TRIGGER reject_completion BEFORE INSERT ON settings
                WHEN NEW.key = 'firstLaunch.complete'
                BEGIN SELECT RAISE(ABORT, 'test failure'); END
                """)
        }
        let changed = ExamScopeSelection(national: false, provinces: ["广东"])
        XCTAssertThrowsError(try store.save(scope: changed, complete: true))
        XCTAssertEqual(try store.load(), LaunchPreferences(complete: false, scope: initial))
        XCTAssertEqual(try scopeIDs(), originalIDs)
        try queue.write { db in try db.execute(sql: "DROP TRIGGER reject_completion") }
        try store.save(scope: changed, complete: true)
        XCTAssertEqual(try store.load(), LaunchPreferences(complete: true, scope: changed))
    }

    func testInvalidProvinceDoesNotWriteAnything() throws {
        XCTAssertThrowsError(try store.save(scope: ExamScopeSelection(provinces: [" "]), complete: true))
        XCTAssertTrue(try scopeIDs().isEmpty)
        XCTAssertFalse(try store.load().complete)
    }

    func testSaveDoesNotTouchFTSContentOrUnrelatedUserData() throws {
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO idiom_fts (idiomid, word, pinyin, initials, sense)
                VALUES ('fixture', '按部就班', 'anbujiuban', 'abjb', '依次')
                """)
            try db.execute(sql: "INSERT INTO settings (key, value) VALUES ('unrelated', 'retained')")
            try db.execute(sql: "INSERT INTO idiom_state (idiomId, status, favorite) VALUES ('fixture', 'known', 1)")
        }
        try store.save(scope: ExamScopeSelection(national: false), complete: true)
        try queue.read { db in
            XCTAssertEqual(try String.fetchOne(db, sql: "SELECT word FROM idiom_fts WHERE idiomid = 'fixture'"), "按部就班")
            XCTAssertEqual(try Int.fetchOne(
                db, sql: "SELECT COUNT(*) FROM idiom_fts WHERE idiom_fts MATCH 'abjb'"
            ), 1)
            XCTAssertEqual(try String.fetchOne(
                db, sql: "SELECT value FROM settings WHERE key = 'unrelated'"
            ), "retained")
            XCTAssertEqual(try Bool.fetchOne(
                db, sql: "SELECT favorite FROM idiom_state WHERE idiomId = 'fixture'"
            ), true)
        }
    }

    func testNewStoreReadsPersistedSelection() throws {
        let selection = ExamScopeSelection(institution: false, provinces: ["四川"])
        try store.save(scope: selection, complete: true)
        let reopenedStore = ExamScopeStore(queue: queue)
        XCTAssertEqual(try reopenedStore.load(), LaunchPreferences(complete: true, scope: selection))
    }

    private func scopeIDs() throws -> Set<String> {
        try queue.read { db in Set(try String.fetchAll(db, sql: "SELECT id FROM exam_scope")) }
    }

    private func enabledProvinceCount() throws -> Int {
        try queue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM exam_scope WHERE type = '省考' AND enabled = 1") ?? 0
        }
    }
}
