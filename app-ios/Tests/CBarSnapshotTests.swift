import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 提示条快照（规范 5.4 / 5.5）：3 类别 × 浅/深，另含行内警示 / 行内错误 × 浅/深。
@MainActor
final class CBarSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertBar(
        kind: CBarKind,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = CBar(kind: kind, title: "提示", subtitle: "说明", actionTitle: "操作", onAction: {})
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: 90)),
            file: file,
            testName: testName,
            line: line
        )
    }

    private func assertInline(
        isError: Bool,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = Group {
            if isError {
                CInlineError(text: "错误")
            } else {
                CInlineWarning(text: "警示")
            }
        }
        .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: 40)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testBarInfoLight() {
        assertBar(kind: .info, dark: false)
    }

    func testBarInfoDark() {
        assertBar(kind: .info, dark: true)
    }

    func testBarWarnLight() {
        assertBar(kind: .warn, dark: false)
    }

    func testBarWarnDark() {
        assertBar(kind: .warn, dark: true)
    }

    func testBarErrorLight() {
        assertBar(kind: .error, dark: false)
    }

    func testBarErrorDark() {
        assertBar(kind: .error, dark: true)
    }

    func testBarInlineWarningLight() {
        assertInline(isError: false, dark: false)
    }

    func testBarInlineWarningDark() {
        assertInline(isError: false, dark: true)
    }

    func testBarInlineErrorLight() {
        assertInline(isError: true, dark: false)
    }

    func testBarInlineErrorDark() {
        assertInline(isError: true, dark: true)
    }
}
