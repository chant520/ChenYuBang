@testable import CangyuData
import GRDB
import XCTest

final class PackScopeTests: PackDatabaseTestCase {
    func testProvinceEvidenceIsExactAndNullIsNeverAllProvinces() throws {
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO exam_scope(id, type, province, enabled) VALUES
                ('null', '省考', NULL, 1), ('empty', '省考', '', 1),
                ('henan', '省考', '河南', 1), ('guizhou', '省考', '贵州', 1),
                ('disabled', '省考', '河南', 0), ('other', '省考', '河南省', 1),
                ('national', '国考', NULL, 1), ('teacher', '教师编', NULL, 1)
                """)
        }
        let users = try snapshot(["exam_scope", "settings"])
        let pack = try importer.validate(PackFixture.published())
        _ = try importer.importPack(pack)
        let fixture = try PackFixture()
        let henan = try expected(fixture, type: "省考", province: "河南")
        let guizhou = try expected(fixture, type: "省考", province: "贵州")
        XCTAssertFalse(henan.isEmpty)
        XCTAssertFalse(guizhou.isEmpty)
        XCTAssertNotEqual(henan, guizhou)
        XCTAssertEqual(try linkedIDs(scope: "henan"), henan)
        XCTAssertEqual(try linkedIDs(scope: "guizhou"), guizhou)
        for scope in ["null", "empty", "disabled", "other"] { XCTAssertTrue(try linkedIDs(scope: scope).isEmpty) }
        XCTAssertEqual(try linkedIDs(scope: "national"), try expected(fixture, type: "国考", province: nil))
        XCTAssertEqual(try linkedIDs(scope: "teacher"), try expected(fixture, type: "教师编", province: nil))
        try importer.refreshScopeLinks(pack)
        XCTAssertEqual(try linkedIDs(scope: "henan"), henan)
        XCTAssertEqual(try snapshot(["exam_scope", "settings"]), users)
    }

    func testImportThenScopeSaveRefreshMatchesScopeSaveThenImport() throws {
        let pack = try importer.validate(PackFixture.published())
        _ = try importer.importPack(pack)
        XCTAssertEqual(try count("exam_scope"), 0)
        XCTAssertEqual(try count("idiom_scope"), 0)
        let selection = ExamScopeSelection(national: true, provincial: true, institution: true, provinces: ["河南", "贵州"])
        try ExamScopeStore(queue: queue).save(scope: selection, complete: true)
        let userTables = ["mistake", "mistake_idiom", "review_log", "idiom_state", "settings", "exam_scope"]
        let savedUsers = try snapshot(userTables)
        try importer.refreshScopeLinks(pack)
        let afterRefresh = try links(in: queue)
        XCTAssertFalse(afterRefresh.isEmpty)
        XCTAssertEqual(try snapshot(userTables), savedUsers)
        let other = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(other)
        try ExamScopeStore(queue: other).save(scope: selection, complete: true)
        _ = try PackImporter(queue: other).importPack(pack)
        XCTAssertEqual(try links(in: other), afterRefresh)
        // P14 changes province and disables national/institution: refresh only changes content links.
        try ExamScopeStore(queue: queue).save(scope: ExamScopeSelection(
            national: false, provincial: true, institution: false, provinces: ["贵州"]
        ))
        let savedAgain = try snapshot(userTables)
        try importer.refreshScopeLinks(pack)
        XCTAssertEqual(try snapshot(userTables), savedAgain)
        let changed = try links(in: queue)
        XCTAssertTrue(changed.allSatisfy { $0.hasPrefix("省考|贵州|") })
        XCTAssertEqual(changed.count, try expected(PackFixture(), type: "省考", province: "贵州").count)
    }

    func testScopeRefreshRollsBackOnDestinationTrigger() throws {
        let pack = try importer.validate(PackFixture.published())
        _ = try importer.importPack(pack)
        try ExamScopeStore(queue: queue).save(scope: ExamScopeSelection(provinces: ["河南"]))
        try importer.refreshScopeLinks(pack)
        let before = try snapshot(["idiom_scope", "exam_scope", "settings"])
        try queue.write { db in
            try db.execute(sql: """
                CREATE TRIGGER pack_scope_abort BEFORE INSERT ON idiom_scope
                BEGIN SELECT RAISE(ABORT, 'scope rollback test'); END
                """)
        }
        XCTAssertThrowsError(try importer.refreshScopeLinks(pack))
        XCTAssertEqual(try snapshot(["idiom_scope", "exam_scope", "settings"]), before)
    }

    func testRefreshRejectsPreviousPackWithSameIDsAndChangedScopes() throws {
        let original = try importer.validate(PackFixture.published())
        _ = try importer.importPack(original)
        try ExamScopeStore(queue: queue).save(scope: ExamScopeSelection())
        try importer.refreshScopeLinks(original)
        var fixture = try PackFixture()
        for index in fixture.idioms.indices { fixture.idioms[index]["scopes"] = ["事业编"] }
        fixture.manifest["packVersion"] = "v1.0.1"
        let updated = try importer.validate(fixture.zip())
        XCTAssertNotEqual(original.sha256, updated.sha256)
        _ = try importer.importPack(updated)
        let before = try snapshot(["idiom_scope", "exam_scope", "settings"])
        XCTAssertThrowsError(try importer.refreshScopeLinks(original))
        XCTAssertEqual(try snapshot(["idiom_scope", "exam_scope", "settings"]), before)
        try importer.refreshScopeLinks(updated)
        XCTAssertEqual(try snapshot(["idiom_scope", "exam_scope", "settings"]), before)
    }

    private func expected(_ fixture: PackFixture, type: String, province: String?) throws -> Set<String> {
        let scoped = try Set(fixture.idioms.filter {
            try XCTUnwrap($0["scopes"] as? [String]).contains(type)
        }.map { try XCTUnwrap($0["id"] as? String) })
        guard type == "省考" else { return scoped }
        let evidence = try fixture.examples.filter {
            $0["examType"] as? String == type && $0["region"] as? String == province
        }.flatMap { try XCTUnwrap($0["idiomIds"] as? [String]) }
        return scoped.intersection(evidence)
    }

    private func links(in queue: DatabaseQueue) throws -> [String] {
        try queue.read { db in
            try String.fetchAll(db, sql: """
                SELECT scope.type || '|' || COALESCE(scope.province, '') || '|' || link.idiomId
                FROM idiom_scope link JOIN exam_scope scope ON scope.id = link.scopeId ORDER BY 1
                """)
        }
    }
}
