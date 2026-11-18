import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。
// 注入浅/深 colorScheme 与 accessibilityReduceMotion=true，保证动画构件帧确定性。

/// 图片占位快照（规范 5.7 / 5.10）：中性 / 警示 / 骨架 / 截断 × 浅/深。
/// 骨架流光在 reduceMotion 下降级静态 skel 色块；截断态含下方标线，画布加高避免裁切。
@MainActor
final class CImagePlaceholderSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func placeholder(_ kind: CImagePlaceholder.Kind) -> some View {
        switch kind {
        case .neutral:
            CImagePlaceholder(kind: .neutral, title: "还没有照片")
        case .warn:
            CImagePlaceholder(kind: .warn, title: "图片丢失", subtitle: "请重新上传")
        case .skeleton:
            CImagePlaceholder(kind: .skeleton)
        case .truncated:
            CImagePlaceholder(kind: .truncated, cutText: "长页 · 已截断")
        }
    }

    private func assertPlaceholder(
        kind: CImagePlaceholder.Kind,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = placeholder(kind)
            .environment(\.colorScheme, dark ? .dark : .light)
            .environment(\.accessibilityReduceMotion, true)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: kind == .truncated ? 200 : 160)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testImagePlaceholderNeutralLight() {
        assertPlaceholder(kind: .neutral, dark: false)
    }

    func testImagePlaceholderNeutralDark() {
        assertPlaceholder(kind: .neutral, dark: true)
    }

    func testImagePlaceholderWarnLight() {
        assertPlaceholder(kind: .warn, dark: false)
    }

    func testImagePlaceholderWarnDark() {
        assertPlaceholder(kind: .warn, dark: true)
    }

    func testImagePlaceholderSkeletonLight() {
        assertPlaceholder(kind: .skeleton, dark: false)
    }

    func testImagePlaceholderSkeletonDark() {
        assertPlaceholder(kind: .skeleton, dark: true)
    }

    func testImagePlaceholderTruncatedLight() {
        assertPlaceholder(kind: .truncated, dark: false)
    }

    func testImagePlaceholderTruncatedDark() {
        assertPlaceholder(kind: .truncated, dark: true)
    }
}
