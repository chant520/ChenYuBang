import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 悬浮拍照按钮快照（规范 5.8）：默认 / 禁用 / 带徽标 × 浅/深。按下态不可注入，iOS 按下态截图尚未覆盖（规范 §11.2）。
/// 画布留足投影外扩（浅色 blur 18 + y 8、深色 blur 24 + spread 1）与徽标组合宽度，避免裁切。
@MainActor
final class CFabSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertFab(
        enabled: Bool,
        badgeText: String? = nil,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = CFab(enabled: enabled, badgeText: badgeText) {}
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: badgeText == nil ? 120 : 160, height: 120)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testFabDefaultLight() {
        assertFab(enabled: true, dark: false)
    }

    func testFabDefaultDark() {
        assertFab(enabled: true, dark: true)
    }

    func testFabDisabledLight() {
        assertFab(enabled: false, dark: false)
    }

    func testFabDisabledDark() {
        assertFab(enabled: false, dark: true)
    }

    func testFabBadgeLight() {
        assertFab(enabled: true, badgeText: "3", dark: false)
    }

    func testFabBadgeDark() {
        assertFab(enabled: true, badgeText: "3", dark: true)
    }
}
