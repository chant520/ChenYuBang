import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。
// 注入浅/深 colorScheme 与 accessibilityReduceMotion=true，保证动画构件帧确定性。

/// 列表行快照（规范 5.13）：默认 / 选中 / 禁用 / 带徽标 × 浅/深。
/// 行底默认透明（surface 由 CList 容器承担），此处单行入画；带徽标走 meta 重载。
@MainActor
final class CListRowSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func row(selected: Bool, disabled: Bool, withMeta: Bool) -> some View {
        Group {
            if withMeta {
                CListRow(name: "成语", pinyin: "chéng yǔ", selected: selected, disabled: disabled) {
                    CBadge(text: "标", kind: .info)
                }
            } else {
                CListRow(name: "成语", pinyin: "chéng yǔ", selected: selected, disabled: disabled)
            }
        }
    }

    private func assertListRow(
        selected: Bool = false,
        disabled: Bool = false,
        withMeta: Bool = false,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = row(selected: selected, disabled: disabled, withMeta: withMeta)
            .environment(\.colorScheme, dark ? .dark : .light)
            .environment(\.accessibilityReduceMotion, true)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: 80)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testListRowDefaultLight() {
        assertListRow(dark: false)
    }

    func testListRowDefaultDark() {
        assertListRow(dark: true)
    }

    func testListRowSelectedLight() {
        assertListRow(selected: true, dark: false)
    }

    func testListRowSelectedDark() {
        assertListRow(selected: true, dark: true)
    }

    func testListRowDisabledLight() {
        assertListRow(disabled: true, dark: false)
    }

    func testListRowDisabledDark() {
        assertListRow(disabled: true, dark: true)
    }

    func testListRowMetaLight() {
        assertListRow(withMeta: true, dark: false)
    }

    func testListRowMetaDark() {
        assertListRow(withMeta: true, dark: true)
    }
}
