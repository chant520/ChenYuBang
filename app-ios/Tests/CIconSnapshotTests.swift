import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。
// 注入浅/深 colorScheme 与 accessibilityReduceMotion=true，保证动画构件帧确定性。

/// 内联矢量图标快照（规范 §6）：5 语义 × 浅/深。
/// 18pt 档描边 1.8（尺寸-描边表）；色取 token 文字色（图标与文字同色，不另取第二种强调色）。
@MainActor
final class CIconSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertIcon(
        kind: CIcons.Kind,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let scheme: ColorScheme = dark ? .dark : .light
        let view = CIcon(
            kind,
            size: Tokens.Icon.n18,
            strokeWidth: 1.8,
            color: CangyuTheme.colors(for: scheme).text
        )
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

    func testIconPlusLight() {
        assertIcon(kind: .plus, dark: false)
    }

    func testIconPlusDark() {
        assertIcon(kind: .plus, dark: true)
    }

    func testIconCheckLight() {
        assertIcon(kind: .check, dark: false)
    }

    func testIconCheckDark() {
        assertIcon(kind: .check, dark: true)
    }

    func testIconWarnLight() {
        assertIcon(kind: .warn, dark: false)
    }

    func testIconWarnDark() {
        assertIcon(kind: .warn, dark: true)
    }

    func testIconErrLight() {
        assertIcon(kind: .err, dark: false)
    }

    func testIconErrDark() {
        assertIcon(kind: .err, dark: true)
    }

    func testIconInfoLight() {
        assertIcon(kind: .info, dark: false)
    }

    func testIconInfoDark() {
        assertIcon(kind: .info, dark: true)
    }
}
