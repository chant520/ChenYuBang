@testable import CangyuData
import Foundation
import GRDB
import XCTest
@testable import ChenYuBang

final class RouterBackupTests: XCTestCase {
    func testBackupImportRequiresValidatedFileAndBackupPage() throws {
        let router = RouterState()
        let backup = try validatedBackup()
        XCTAssertFalse(router.beginBackupImport())
        XCTAssertFalse(router.prepareBackupImport(backup))
        router.selectTab(.profile)
        XCTAssertTrue(router.push(.backup))
        XCTAssertTrue(router.prepareBackupImport(backup))
        XCTAssertEqual(router.overlay?.route, .importMode)
        XCTAssertEqual(router.pendingBackup?.counts, backup.counts)
        router.closeOverlay()
        XCTAssertNil(router.overlay)
        XCTAssertNil(router.pendingBackup)
    }

    func testBackupImportProgressBlocksCancelAndSuccessRefreshesContent() throws {
        let router = RouterState()
        let backup = try validatedBackup()
        router.selectTab(.profile)
        XCTAssertTrue(router.push(.backup))
        XCTAssertTrue(router.prepareBackupImport(backup))
        let revision = router.userContentRevision
        XCTAssertTrue(router.beginBackupImport())
        XCTAssertFalse(router.beginBackupImport())
        router.closeOverlay()
        XCTAssertEqual(router.overlay?.route, .importMode)
        router.finishBackupImport(result: .failure("导入失败"))
        XCTAssertFalse(router.backupImportInProgress)
        XCTAssertEqual(router.backupImportError, "导入失败")
        XCTAssertNotNil(router.pendingBackup)
        XCTAssertTrue(router.beginBackupImport())
        router.finishBackupImport(result: .success("导入完成"))
        XCTAssertNil(router.overlay)
        XCTAssertNil(router.pendingBackup)
        XCTAssertEqual(router.backupImportResult, "导入完成")
        XCTAssertNotEqual(router.userContentRevision, revision)
    }

    func testClearRequiresProfileAndProgressBlocksCancel() {
        let router = RouterState()
        XCTAssertFalse(router.beginClearData())
        router.selectTab(.profile)
        let revision = router.userContentRevision
        XCTAssertTrue(router.openOverlay(.clear))
        XCTAssertTrue(router.beginClearData())
        XCTAssertFalse(router.beginClearData())
        router.closeOverlay()
        XCTAssertEqual(router.overlay?.route, .clear)
        router.finishClearData(result: .failure("清空失败"))
        XCTAssertEqual(router.clearError, "清空失败")
        XCTAssertEqual(router.overlay?.route, .clear)
        XCTAssertTrue(router.beginClearData())
        router.finishClearData(result: .success("已清空"))
        XCTAssertNil(router.overlay)
        XCTAssertEqual(router.clearResult, "已清空")
        XCTAssertNotEqual(router.userContentRevision, revision)
    }

    private func validatedBackup() throws -> ValidatedBackup {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("router-backup-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        let service = BackupService(queue: queue)
        let exported = try service.export(to: directory)
        return try service.validate(Data(contentsOf: exported.url))
    }
}
