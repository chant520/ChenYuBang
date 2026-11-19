import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 小标签文字快照（全页稿 v2 `.lbl`，规范 §5 卡片小标题）：浅/深。
/// 非交互构件，激活 / 禁用 / 加载 / 错误态不适用。
@MainActor
final class CLabelSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertLabel(
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let scheme: ColorScheme = dark ? .dark : .light
        let view = CLabel("生效范围")
            .frame(width: 200, height: 40)
            .background(CangyuTheme.colors(for: scheme).page)
            .environment(\.colorScheme, scheme)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 200, height: 40)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testLabelLight() {
        assertLabel(dark: false)
    }

    func testLabelDark() {
        assertLabel(dark: true)
    }
}
