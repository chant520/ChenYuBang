import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem

/// 构件黄金快照（规范 §11.1 / §13）。基线由 CI 设 SNAPSHOT_RECORD=1 录制入库，
/// 默认 verify 比对。浅/深通过 environment(\.colorScheme) 控制，与 CButton 取色一致。
@MainActor
final class CButtonSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertButton(
        variant: CButtonVariant,
        enabled: Bool,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = CButton(text: "按钮", variant: variant, enabled: enabled) {}
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: 60)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testPrimaryDefaultLight() {
        assertButton(variant: .primary, enabled: true, dark: false)
    }

    func testPrimaryDefaultDark() {
        assertButton(variant: .primary, enabled: true, dark: true)
    }

    func testPrimaryDisabledLight() {
        assertButton(variant: .primary, enabled: false, dark: false)
    }

    func testPrimaryDisabledDark() {
        assertButton(variant: .primary, enabled: false, dark: true)
    }

    func testGhostDefaultLight() {
        assertButton(variant: .ghost, enabled: true, dark: false)
    }

    func testGhostDefaultDark() {
        assertButton(variant: .ghost, enabled: true, dark: true)
    }

    func testGhostDisabledLight() {
        assertButton(variant: .ghost, enabled: false, dark: false)
    }

    func testGhostDisabledDark() {
        assertButton(variant: .ghost, enabled: false, dark: true)
    }
}
