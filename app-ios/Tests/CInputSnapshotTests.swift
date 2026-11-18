import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 输入框快照（规范 5.3）：默认 / 聚焦 / 校验错误 / 禁用 × 浅/深。
/// 聚焦态 iOS 可参数化（state: .focus）；错误提示仅 .error 态渲染于框下。
@MainActor
final class CInputSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertInput(
        state: CInputState,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = CInput(
            text: .constant("输入"),
            state: state,
            placeholder: "占位",
            errorHint: state == .error ? "错误提示" : nil
        )
        .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: 80)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testInputDefaultLight() {
        assertInput(state: .default, dark: false)
    }

    func testInputDefaultDark() {
        assertInput(state: .default, dark: true)
    }

    func testInputFocusLight() {
        assertInput(state: .focus, dark: false)
    }

    func testInputFocusDark() {
        assertInput(state: .focus, dark: true)
    }

    func testInputErrorLight() {
        assertInput(state: .error, dark: false)
    }

    func testInputErrorDark() {
        assertInput(state: .error, dark: true)
    }

    func testInputDisabledLight() {
        assertInput(state: .disabled, dark: false)
    }

    func testInputDisabledDark() {
        assertInput(state: .disabled, dark: true)
    }
}
