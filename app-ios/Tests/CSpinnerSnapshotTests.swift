import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。
// 注入浅/深 colorScheme 与 accessibilityReduceMotion=true，保证动画构件帧确定性。

/// 加载转圈快照（规范 5.0 / 5.11 / §6）：浅/深。
/// reduceMotion 下角度归零静止；颜色继承 foregroundStyle，取 token 文字色保证深色可见。
@MainActor
final class CSpinnerSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertSpinner(
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let scheme: ColorScheme = dark ? .dark : .light
        let view = CSpinner()
            .foregroundStyle(CangyuTheme.colors(for: scheme).text)
            .environment(\.colorScheme, scheme)
            .environment(\.accessibilityReduceMotion, true)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 40, height: 40)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testSpinnerLight() {
        assertSpinner(dark: false)
    }

    func testSpinnerDark() {
        assertSpinner(dark: true)
    }
}
