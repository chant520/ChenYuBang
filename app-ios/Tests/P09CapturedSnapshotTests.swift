import CangyuTokens
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ChenYuBang

/// P09 某成语下录入题快照（规范 §11.1）：列表 + 空态 × 浅深，基线目录 `__Snapshots__/P09CapturedSnapshotTests/`。
///
/// 只录本版可达状态：`已掌握` 标签要等 3.1 复习判分改 `mistake.state` 才会出现，本版不录；
/// 成语已从题库消失（missing）与读取失败（橙条）属边界，由构件矩阵覆盖同款 `CEmptyState` / `CBar`。
@MainActor
final class P09CapturedSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertP09(
        _ state: IdiomCapturedState,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P09CapturedContent(state: state, actions: actions(), colors: dark ? Tokens.dark : Tokens.light)
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 390, height: 844)),
            file: file,
            testName: testName,
            line: line
        )
    }

    /// 释义取 `shared/fixtures/idioms.sample.json` 中「按部就班」的 `sense` 原文（不拼色彩、不截句）。
    private let sense = "按：依照；部、班：门类与次序。原指安排事务依照一定条理，后多指照老规矩办事，缺乏变通。"

    private func listState() -> IdiomCapturedState {
        IdiomCapturedState(
            loading: false,
            word: "按部就班",
            senseLine: sense,
            examples: [
                CapturedExampleView(
                    id: "e1",
                    stem: "下列各句中，加点的成语使用不恰当的一项是",
                    dateText: "2026-11-14 录入",
                    isMistake: true,
                    state: "待复习",
                    reason: "望文生义"
                ),
                CapturedExampleView(
                    id: "e2",
                    stem: "填入横线处最恰当的一项：改革不能______，要敢于突破既有框架",
                    dateText: "2026-11-09 录入",
                    isMistake: true,
                    state: "待复习",
                    reason: nil
                ),
                CapturedExampleView(
                    id: "e3",
                    stem: "这段文字中「按部就班」能否换成「墨守成规」，为什么",
                    dateText: "2026-10-28 录入",
                    isMistake: false,
                    state: nil,
                    reason: nil
                )
            ]
        )
    }

    private func emptyState() -> IdiomCapturedState {
        IdiomCapturedState(loading: false, word: "按部就班", senseLine: sense)
    }

    private func actions() -> IdiomCapturedActions {
        IdiomCapturedActions(onBack: {}, onOpenIdiom: {}, onOpenExample: { _ in }, onBackToBook: {}, onRetry: {})
    }

    func testListLight() {
        assertP09(listState(), dark: false)
    }

    func testListDark() {
        assertP09(listState(), dark: true)
    }

    func testEmptyLight() {
        assertP09(emptyState(), dark: false)
    }

    func testEmptyDark() {
        assertP09(emptyState(), dark: true)
    }
}
