@testable import CangyuData
import CangyuDesignSystem
import Foundation
import GRDB
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ChenYuBang

@MainActor
final class Phase4SnapshotTests: XCTestCase {
    private enum BackupCase {
        case main
        case exportSuccess
        case exportFailure
        case importSuccess
        case importUnreadable
    }

    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    func testP15Light() throws { try assertBackup(dark: false) }
    func testP15Dark() throws { try assertBackup(dark: true) }
    func testP15ExportSuccessLight() throws { try assertBackup(dark: false, snapshot: .exportSuccess) }
    func testP15ExportSuccessDark() throws { try assertBackup(dark: true, snapshot: .exportSuccess) }
    func testP15ExportFailureLight() throws { try assertBackup(dark: false, snapshot: .exportFailure) }
    func testP15ExportFailureDark() throws { try assertBackup(dark: true, snapshot: .exportFailure) }
    func testP15ImportSuccessLight() throws { try assertBackup(dark: false, snapshot: .importSuccess) }
    func testP15ImportSuccessDark() throws { try assertBackup(dark: true, snapshot: .importSuccess) }
    func testP15ImportUnreadableLight() throws { try assertBackup(dark: false, snapshot: .importUnreadable) }
    func testP15ImportUnreadableDark() throws { try assertBackup(dark: true, snapshot: .importUnreadable) }
    func testP16Light() throws { try assertPack(dark: false, status: .builtin) }
    func testP16Dark() throws { try assertPack(dark: true, status: .builtin) }
    func testP16CheckingLight() throws { try assertPack(dark: false, status: .checking) }
    func testP16CheckingDark() throws { try assertPack(dark: true, status: .checking) }
    func testP16DownloadingLight() throws { try assertPack(dark: false, status: .downloading) }
    func testP16DownloadingDark() throws { try assertPack(dark: true, status: .downloading) }
    func testP16UpdatedLight() throws { try assertPack(dark: false, status: .updated) }
    func testP16UpdatedDark() throws { try assertPack(dark: true, status: .updated) }
    func testP16RecoverableFailureLight() throws { try assertPack(dark: false, status: .recoverableFailure) }
    func testP16RecoverableFailureDark() throws { try assertPack(dark: true, status: .recoverableFailure) }
    func testP16MockLight() throws { try assertPack(dark: false, status: .mock) }
    func testP16MockDark() throws { try assertPack(dark: true, status: .mock) }
    func testP22Light() throws { try assertImport(dark: false) }
    func testP22Dark() throws { try assertImport(dark: true) }
    func testP22BusyLight() throws { try assertImport(dark: false, inProgress: true) }
    func testP22BusyDark() throws { try assertImport(dark: true, inProgress: true) }
    func testP22FailureLight() throws { try assertImport(dark: false, error: Phase4Copy.importFailure) }
    func testP22FailureDark() throws { try assertImport(dark: true, error: Phase4Copy.importFailure) }
    func testP23Light() throws { try assertClear(dark: false) }
    func testP23Dark() throws { try assertClear(dark: true) }

    private func assertBackup(dark: Bool, snapshot: BackupCase = .main,
                              file: StaticString = #filePath, testName: String = #function,
                              line: UInt = #line) throws {
        let fixture = try makeFixture()
        var importResult: String?
        var initialStatus: String?
        var initialFailed = false
        switch snapshot {
        case .main:
            break
        case .exportSuccess:
            initialStatus = BackupScreen.exportMessage(fixture.exported, share: false)
        case .exportFailure:
            initialStatus = Phase4Copy.exportFailure
            initialFailed = true
        case .importSuccess:
            importResult = "合并导入完成：写入 0 条错题、1 条录入题、0 条复习记录、0 条学习状态、0 条考试范围。"
        case .importUnreadable:
            initialStatus = Phase4Copy.backupUnreadable
            initialFailed = true
        }
        let view = BackupScreen(
            service: fixture.backup,
            importResult: importResult,
            onValidated: { _ in true },
            onBack: {},
            initialStatus: initialStatus,
            initialFailed: initialFailed
        )
        .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(of: view, as: .image(layout: .fixed(width: 390, height: 844)),
                       file: file, testName: testName, line: line)
    }

    private func assertPack(dark: Bool, status: PackVersionStatus,
                            file: StaticString = #filePath, testName: String = #function,
                            line: UInt = #line) throws {
        let fixture = try makeFixture()
        let presentation = PackVersionPresentation(
            status: status,
            version: status == .mock ? "TEST" : fixture.pack.version,
            idiomCount: status == .mock ? 3 : fixture.pack.counts.idioms,
            exampleCount: status == .mock ? 2 : fixture.pack.counts.examples,
            progress: 0.42,
            reason: status == .mock ? "题库未获取成功" : nil
        )
        let view = PackVersionScreen(presentation: presentation, onBack: {})
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(of: view, as: .image(layout: .fixed(width: 390, height: 844)),
                       file: file, testName: testName, line: line)
    }

    private func assertImport(dark: Bool, inProgress: Bool = false, error: String? = nil,
                              file: StaticString = #filePath, testName: String = #function,
                              line: UInt = #line) throws {
        let fixture = try makeFixture()
        let content = ImportModeSheet(
            backup: fixture.backupPayload,
            service: fixture.backup,
            inProgress: inProgress,
            error: error,
            onStart: { true },
            onFinish: { _ in }
        )
        let view = COverlay(style: .sheet, title: "读入这份备份，用哪种方式",
                            description: "两种方式结果不同，选之前看清楚。",
                            onDismiss: {}, content: {
            content
        })
        .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(of: view, as: .image(layout: .fixed(width: 390, height: 844)),
                       file: file, testName: testName, line: line)
    }

    private func assertClear(dark: Bool, file: StaticString = #filePath,
                             testName: String = #function, line: UInt = #line) throws {
        let fixture = try makeFixture()
        let content = ClearDataSheet(
            service: fixture.cleaner,
            inProgress: false,
            error: nil,
            counts: try fixture.cleaner.counts(),
            onStart: { true },
            onFinish: { _ in }
        )
        let view = COverlay(style: .dialog, title: "清空全部学习数据",
                            description: "这一步不可撤销。清掉的是你录的东西，不是题库本身。",
                            onDismiss: {}, content: {
            content
        })
        .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(of: view, as: .image(layout: .fixed(width: 390, height: 844)),
                       file: file, testName: testName, line: line)
    }

    private func makeFixture() throws -> SnapshotFixture {
        let queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("phase4-snapshot-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        let backupService = BackupService(queue: queue)
        let exported = try backupService.export(to: directory)
        let backup = try backupService.validate(Data(contentsOf: exported.url))
        let pack = try PackImporter(queue: queue).validate(PackFixture.published())
        return SnapshotFixture(
            backup: backupService,
            cleaner: UserDataCleaner(queue: queue),
            pack: pack,
            exported: exported,
            backupPayload: backup
        )
    }

    private struct SnapshotFixture {
        let backup: BackupService
        let cleaner: UserDataCleaner
        let pack: ValidatedPack
        let exported: BackupExport
        let backupPayload: ValidatedBackup
    }
}
