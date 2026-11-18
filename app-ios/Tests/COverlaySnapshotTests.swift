import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。
// 注入浅/深 colorScheme 与 accessibilityReduceMotion=true，保证动画构件帧确定性。

/// 弹层快照（规范 5.12）：sheet / dialog × 浅/深。
/// COverlay 为自绘 ZStack（非 .sheet），直接整幅快照；蒙层 + 面板 + 取消按钮一并入画。
@MainActor
final class COverlaySnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertOverlay(
        style: COverlayStyle,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = COverlay(style: style, title: "标题", onDismiss: {}) {
            Text("内容")
        }
        .environment(\.colorScheme, dark ? .dark : .light)
        .environment(\.accessibilityReduceMotion, true)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: 400)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testOverlaySheetLight() {
        assertOverlay(style: .sheet, dark: false)
    }

    func testOverlaySheetDark() {
        assertOverlay(style: .sheet, dark: true)
    }

    func testOverlayDialogLight() {
        assertOverlay(style: .dialog, dark: false)
    }

    func testOverlayDialogDark() {
        assertOverlay(style: .dialog, dark: true)
    }
}
