import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem

/// 构件黄金快照（规范 §11.1 / §13）。基线由 CI 设 SNAPSHOT_RECORD=1 录制入库，
/// 默认 verify 比对。浅/深通过 environment(\.colorScheme) 控制，与 CButton 取色一致。
/// 全矩阵：5 变体 × 默认/禁用/加载 × 浅/深（规范 5.1）。
/// 按下态不可注入（ButtonStyleConfiguration.isPressed 无法在视图快照中模拟），
/// iOS 按下态截图尚未覆盖（规范 §11.2）；Android 快照不能替代 iOS 验证。
/// 事务禁用动画仅用于静态外观快照，不覆盖系统 reduce-motion。
@MainActor
final class CButtonSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertButton(
        variant: CButtonVariant,
        enabled: Bool,
        loading: Bool = false,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = CButton(text: "按钮", variant: variant, enabled: enabled, loading: loading) {}
            .environment(\.colorScheme, dark ? .dark : .light)
            .transaction { transaction in
                transaction.animation = nil
                transaction.disablesAnimations = true
            }
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: 60)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testPrimaryDefaultLight() {
        assertButton(variant: .primary, enabled: true, dark: false)
    }

    func testPrimaryDefaultDark() {
        assertButton(variant: .primary, enabled: true, dark: true)
    }

    func testPrimaryDisabledLight() {
        assertButton(variant: .primary, enabled: false, dark: false)
    }

    func testPrimaryDisabledDark() {
        assertButton(variant: .primary, enabled: false, dark: true)
    }

    func testGhostDefaultLight() {
        assertButton(variant: .ghost, enabled: true, dark: false)
    }

    func testGhostDefaultDark() {
        assertButton(variant: .ghost, enabled: true, dark: true)
    }

    func testGhostDisabledLight() {
        assertButton(variant: .ghost, enabled: false, dark: false)
    }

    func testGhostDisabledDark() {
        assertButton(variant: .ghost, enabled: false, dark: true)
    }

    // MARK: - 加载态（规范 5.0 / 5.11：默认样式 + spinner）

    func testPrimaryLoadingLight() {
        assertButton(variant: .primary, enabled: true, loading: true, dark: false)
    }

    func testPrimaryLoadingDark() {
        assertButton(variant: .primary, enabled: true, loading: true, dark: true)
    }

    func testGhostLoadingLight() {
        assertButton(variant: .ghost, enabled: true, loading: true, dark: false)
    }

    func testGhostLoadingDark() {
        assertButton(variant: .ghost, enabled: true, loading: true, dark: true)
    }

    // MARK: - 危险按钮·填充（规范 5.1）

    func testDangerFilledDefaultLight() {
        assertButton(variant: .dangerFilled, enabled: true, dark: false)
    }

    func testDangerFilledDefaultDark() {
        assertButton(variant: .dangerFilled, enabled: true, dark: true)
    }

    func testDangerFilledDisabledLight() {
        assertButton(variant: .dangerFilled, enabled: false, dark: false)
    }

    func testDangerFilledDisabledDark() {
        assertButton(variant: .dangerFilled, enabled: false, dark: true)
    }

    func testDangerFilledLoadingLight() {
        assertButton(variant: .dangerFilled, enabled: true, loading: true, dark: false)
    }

    func testDangerFilledLoadingDark() {
        assertButton(variant: .dangerFilled, enabled: true, loading: true, dark: true)
    }

    // MARK: - 危险按钮·描边（规范 5.1）

    func testDangerOutlineDefaultLight() {
        assertButton(variant: .dangerOutline, enabled: true, dark: false)
    }

    func testDangerOutlineDefaultDark() {
        assertButton(variant: .dangerOutline, enabled: true, dark: true)
    }

    func testDangerOutlineDisabledLight() {
        assertButton(variant: .dangerOutline, enabled: false, dark: false)
    }

    func testDangerOutlineDisabledDark() {
        assertButton(variant: .dangerOutline, enabled: false, dark: true)
    }

    func testDangerOutlineLoadingLight() {
        assertButton(variant: .dangerOutline, enabled: true, loading: true, dark: false)
    }

    func testDangerOutlineLoadingDark() {
        assertButton(variant: .dangerOutline, enabled: true, loading: true, dark: true)
    }

    // MARK: - 顶部按钮（规范 5.1：内容自适应宽 + 14pt 图标）

    func testTopDefaultLight() {
        assertButton(variant: .top, enabled: true, dark: false)
    }

    func testTopDefaultDark() {
        assertButton(variant: .top, enabled: true, dark: true)
    }

    func testTopDisabledLight() {
        assertButton(variant: .top, enabled: false, dark: false)
    }

    func testTopDisabledDark() {
        assertButton(variant: .top, enabled: false, dark: true)
    }

    func testTopLoadingLight() {
        assertButton(variant: .top, enabled: true, loading: true, dark: false)
    }

    func testTopLoadingDark() {
        assertButton(variant: .top, enabled: true, loading: true, dark: true)
    }
}
