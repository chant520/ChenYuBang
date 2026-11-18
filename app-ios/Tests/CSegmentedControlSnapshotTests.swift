import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 分段控件快照（规范 5.14）：首段选中 / 换段选中 / 含禁用项 / 两段 × 浅/深。
@MainActor
final class CSegmentedControlSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertSegmented(
        items: [String] = ["甲", "乙", "丙"],
        selection: Int,
        disabledIndices: Set<Int> = [],
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = CSegmentedControl(
            items: items,
            selection: .constant(selection),
            disabledIndices: disabledIndices
        )
        .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: 50)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testSegmentedFirstLight() {
        assertSegmented(selection: 0, dark: false)
    }

    func testSegmentedFirstDark() {
        assertSegmented(selection: 0, dark: true)
    }

    func testSegmentedSecondLight() {
        assertSegmented(selection: 1, dark: false)
    }

    func testSegmentedSecondDark() {
        assertSegmented(selection: 1, dark: true)
    }

    func testSegmentedDisabledLight() {
        assertSegmented(selection: 0, disabledIndices: [2], dark: false)
    }

    func testSegmentedDisabledDark() {
        assertSegmented(selection: 0, disabledIndices: [2], dark: true)
    }

    func testSegmentedTwoItemsLight() {
        assertSegmented(items: ["甲", "乙"], selection: 0, dark: false)
    }

    func testSegmentedTwoItemsDark() {
        assertSegmented(items: ["甲", "乙"], selection: 0, dark: true)
    }
}
