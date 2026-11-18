import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 标签快照（规范 5.2）：4 样式 × 浅/深。
@MainActor
final class CTagSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertTag(
        style: CTagStyle,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = CTag(text: "标签", style: style)
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 120, height: 40)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testTagSolidLight() {
        assertTag(style: .solid, dark: false)
    }

    func testTagSolidDark() {
        assertTag(style: .solid, dark: true)
    }

    func testTagGhostLight() {
        assertTag(style: .ghost, dark: false)
    }

    func testTagGhostDark() {
        assertTag(style: .ghost, dark: true)
    }

    func testTagSelectedLight() {
        assertTag(style: .selected, dark: false)
    }

    func testTagSelectedDark() {
        assertTag(style: .selected, dark: true)
    }

    func testTagDisabledLight() {
        assertTag(style: .disabled, dark: false)
    }

    func testTagDisabledDark() {
        assertTag(style: .disabled, dark: true)
    }
}
