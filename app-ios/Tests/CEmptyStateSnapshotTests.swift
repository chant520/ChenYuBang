import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 空态快照（D 稿 44 屏同一结构）：无动作 / 带幽灵按钮 × 浅/深。
@MainActor
final class CEmptyStateSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertEmptyState(
        withAction: Bool,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let scheme: ColorScheme = dark ? .dark : .light
        let view = CEmptyState(
            title: "还没有错题",
            description: "拍照录入后，标错的题会出现在这里。",
            actionTitle: withAction ? "去录入" : nil,
            onAction: withAction ? {} : nil
        )
        .frame(width: 375)
        .background(CangyuTheme.colors(for: scheme).page)
        .environment(\.colorScheme, scheme)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 375, height: withAction ? 200 : 120)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testEmptyStatePlainLight() {
        assertEmptyState(withAction: false, dark: false)
    }

    func testEmptyStatePlainDark() {
        assertEmptyState(withAction: false, dark: true)
    }

    func testEmptyStateActionLight() {
        assertEmptyState(withAction: true, dark: false)
    }

    func testEmptyStateActionDark() {
        assertEmptyState(withAction: true, dark: true)
    }
}
