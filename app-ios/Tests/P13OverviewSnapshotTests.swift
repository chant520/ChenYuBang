import CangyuTokens
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ChenYuBang

/// P13 E2 概览快照（规范 §11.1）：4+1 数字格 + 收藏入口 × 浅深 + 全空 × 浅深。
/// 只钉住本包新增的 P13OverviewContent，不依赖数据库。
@MainActor
final class P13OverviewSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertP13(_ stats: P13OverviewStats, dark: Bool,
                           file: StaticString = #filePath, testName: String = #function,
                           line: UInt = #line) {
        let colors = dark ? Tokens.dark : Tokens.light
        let view = P13OverviewContent(stats: stats, colors: colors, onOpenFavorites: {})
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(of: view, as: .image(layout: .fixed(width: 390, height: 500)),
                       file: file, testName: testName, line: line)
    }

    private func filledStats() -> P13OverviewStats {
        P13OverviewStats(learned: 18, favorites: 4, mistakes: 12, pending: 7, proficient: 3)
    }

    func testFilledLight() { assertP13(filledStats(), dark: false) }
    func testFilledDark() { assertP13(filledStats(), dark: true) }
    func testAllZeroLight() { assertP13(P13OverviewStats(), dark: false) }
    func testAllZeroDark() { assertP13(P13OverviewStats(), dark: true) }
}
