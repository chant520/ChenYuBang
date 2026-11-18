import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 徽标快照（规范 5.6）：4 类别 × 浅/深。
@MainActor
final class CBadgeSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertBadge(
        kind: CBadgeKind,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = CBadge(text: "标", kind: kind)
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 120, height: 40)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testBadgeWarnLight() {
        assertBadge(kind: .warn, dark: false)
    }

    func testBadgeWarnDark() {
        assertBadge(kind: .warn, dark: true)
    }

    func testBadgeErrorLight() {
        assertBadge(kind: .error, dark: false)
    }

    func testBadgeErrorDark() {
        assertBadge(kind: .error, dark: true)
    }

    func testBadgeInfoLight() {
        assertBadge(kind: .info, dark: false)
    }

    func testBadgeInfoDark() {
        assertBadge(kind: .info, dark: true)
    }

    func testBadgeNeutralLight() {
        assertBadge(kind: .neutral, dark: false)
    }

    func testBadgeNeutralDark() {
        assertBadge(kind: .neutral, dark: true)
    }
}
