import XCTest
import SnapshotTesting
import SwiftUI
import CangyuDesignSystem
import CangyuTokens

// 构件黄金快照（规范 §11.1 / §7）：CI 设 SNAPSHOT_RECORD=1 录制，默认 verify 比对。
// 基线目录为 __Snapshots__/<本文件名去扩展名>/，每个构件独立存放。

/// 卡片快照（全页稿 v2 `.card` / `.card.soft`）：常规 / 软卡 × 浅/深。
@MainActor
final class CCardSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertCard(
        soft: Bool,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let scheme: ColorScheme = dark ? .dark : .light
        let view = CCard(soft: soft) {
            CLabel("生效范围")
            Text("国考 + 省考（全部省份）")
                .font(.system(size: Tokens.Font.n13))
                .foregroundStyle(CangyuTheme.colors(for: scheme).text2)
        }
        .padding(Tokens.Space.pageX)
        .frame(width: 320, height: 120)
        .background(CangyuTheme.colors(for: scheme).page)
        .environment(\.colorScheme, scheme)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 320, height: 120)),
            file: file,
            testName: testName,
            line: line
        )
    }

    func testCardDefaultLight() {
        assertCard(soft: false, dark: false)
    }

    func testCardDefaultDark() {
        assertCard(soft: false, dark: true)
    }

    func testCardSoftLight() {
        assertCard(soft: true, dark: false)
    }

    func testCardSoftDark() {
        assertCard(soft: true, dark: true)
    }
}
