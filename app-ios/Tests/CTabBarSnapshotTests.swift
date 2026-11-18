import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 四标签 Chrome 快照（规范 5.9）：4 选中项 × 浅/深。
/// 内容高约 66pt（tabTop 11 + 图标 24 + gap 5 + 文字 + tabBottom 13），画布取 80 避免裁切。
@MainActor
final class CTabBarSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertTabBar(
        selected: CTabBarItem,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = CTabBar(selected: selected) { _ in }
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: 80)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testTabBarIdiomLight() {
        assertTabBar(selected: .idiom, dark: false)
    }

    func testTabBarIdiomDark() {
        assertTabBar(selected: .idiom, dark: true)
    }

    func testTabBarExampleLight() {
        assertTabBar(selected: .example, dark: false)
    }

    func testTabBarExampleDark() {
        assertTabBar(selected: .example, dark: true)
    }

    func testTabBarMistakeLight() {
        assertTabBar(selected: .mistake, dark: false)
    }

    func testTabBarMistakeDark() {
        assertTabBar(selected: .mistake, dark: true)
    }

    func testTabBarProfileLight() {
        assertTabBar(selected: .profile, dark: false)
    }

    func testTabBarProfileDark() {
        assertTabBar(selected: .profile, dark: true)
    }
}
