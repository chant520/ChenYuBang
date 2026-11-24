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
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    func testP15Light() throws { try assertBackup(dark: false) }
    func testP15Dark() throws { try assertBackup(dark: true) }
    func testP16Light() throws { try assertPack(dark: false) }
    func testP16Dark() throws { try assertPack(dark: true) }
    func testP22Light() throws { try assertImport(dark: false) }
    func testP22Dark() throws { try assertImport(dark: true) }
    func testP23Light() throws { try assertClear(dark: false) }
    func testP23Dark() throws { try assertClear(dark: true) }

    private func assertBackup(dark: Bool, file: StaticString = #filePath,
                              testName: String = #function, line: UInt = #line) throws {
        let fixture = try makeFixture()
        let view = BackupScreen(
            service: fixture.backup,
            importResult: nil,
            onValidated: { _ in true },
            onBack: {}
        )
        .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(of: view, as: .image(layout: .fixed(width: 390, height: 844)),
                       file: file, testName: testName, line: line)
    }

    private func assertPack(dark: Bool, file: StaticString = #filePath,
                            testName: String = #function, line: UInt = #line) throws {
        let fixture = try makeFixture()
        let view = PackVersionScreen(pack: fixture.pack, onBack: {})
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(of: view, as: .image(layout: .fixed(width: 390, height: 844)),
                       file: file, testName: testName, line: line)
    }

    private func assertImport(dark: Bool, file: StaticString = #filePath,
                              testName: String = #function, line: UInt = #line) throws {
        let fixture = try makeFixture()
        let content = ImportModeSheet(
            backup: fixture.backup,
            service: fixture.backup,
            inProgress: false,
            error: nil,
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
            backupPayload: backup
        )
    }

    private struct SnapshotFixture {
        let backup: BackupService
        let cleaner: UserDataCleaner
        let pack: ValidatedPack
        let backupPayload: ValidatedBackup
    }
}
