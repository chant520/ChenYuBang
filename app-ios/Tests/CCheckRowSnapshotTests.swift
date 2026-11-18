import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 勾选行快照（规范 5.15）：勾选 / 未勾选 / 禁用 / 带警示 × 浅/深。
@MainActor
final class CCheckRowSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertCheckRow(
        isOn: Bool,
        enabled: Bool = true,
        warningText: String? = nil,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = CCheckRow(
            label: "选项",
            isOn: .constant(isOn),
            enabled: enabled,
            warningText: warningText
        )
        .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: 60)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testCheckRowOnLight() {
        assertCheckRow(isOn: true, dark: false)
    }

    func testCheckRowOnDark() {
        assertCheckRow(isOn: true, dark: true)
    }

    func testCheckRowOffLight() {
        assertCheckRow(isOn: false, dark: false)
    }

    func testCheckRowOffDark() {
        assertCheckRow(isOn: false, dark: true)
    }

    func testCheckRowDisabledLight() {
        assertCheckRow(isOn: true, enabled: false, dark: false)
    }

    func testCheckRowDisabledDark() {
        assertCheckRow(isOn: true, enabled: false, dark: true)
    }

    func testCheckRowWarningLight() {
        assertCheckRow(isOn: true, warningText: "警示", dark: false)
    }

    func testCheckRowWarningDark() {
        assertCheckRow(isOn: true, warningText: "警示", dark: true)
    }
}
