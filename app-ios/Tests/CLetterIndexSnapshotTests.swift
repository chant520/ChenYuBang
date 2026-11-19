import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 字母索引条快照（全页稿 v2 P03）：选中 / 未选中 × 浅/深。
/// 选中为 accent/700 + tintbg，未选为 text3（10pt、行高 20pt、宽 24pt）。
@MainActor
final class CLetterIndexSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertLetterIndex(
        selected: String?,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let scheme: ColorScheme = dark ? .dark : .light
        let view = CLetterIndex(letters: ["A", "B", "C", "D"], selected: selected, onSelect: { _ in })
            .padding(8)
            .background(CangyuTheme.colors(for: scheme).page)
            .environment(\.colorScheme, scheme)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 40, height: 96)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testLetterIndexUnselectedLight() {
        assertLetterIndex(selected: nil, dark: false)
    }

    func testLetterIndexUnselectedDark() {
        assertLetterIndex(selected: nil, dark: true)
    }

    func testLetterIndexSelectedLight() {
        assertLetterIndex(selected: "B", dark: false)
    }

    func testLetterIndexSelectedDark() {
        assertLetterIndex(selected: "B", dark: true)
    }
}
