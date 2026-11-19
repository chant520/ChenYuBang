import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 文字链接快照（全页稿 v2 `.linkbtn`，规范 §5 页内跳转）：浅/深。
@MainActor
final class CLinkSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertLink(
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let scheme: ColorScheme = dark ? .dark : .light
        let view = CLink("调整省份", action: {})
            .frame(width: 160, height: 44)
            .background(CangyuTheme.colors(for: scheme).page)
            .environment(\.colorScheme, scheme)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 160, height: 44)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testLinkLight() {
        assertLink(dark: false)
    }

    func testLinkDark() {
        assertLink(dark: true)
    }
}
