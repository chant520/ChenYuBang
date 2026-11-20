import XCTest
import SnapshotTesting
import SwiftUI
import CangyuTokens
@testable import ChenYuBang

/// P20 黄金快照（规范 §11.1）：浅/深主态 + 浅/深空态，四张新基线，不覆盖基线 C/D 图。
/// 基线目录 __Snapshots__/P20MatchSnapshotTests/，新命名独立存放；CI 先 SNAPSHOT_RECORD=1 录制再默认 verify。
@MainActor
final class P20MatchSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertP20(
        _ state: P20MatchState,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P20MatchContent(
            state: state,
            actions: actions(),
            colors: dark ? Tokens.dark : Tokens.light
        )
        .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 390, height: 844)),
            file: file,
            testName: testName,
            line: line
        )
    }

    private func mainState() -> P20MatchState {
        var state = P20MatchState()
        state.reviewText = "下列各句中，加点的成语使用不恰当的一项是……\n"
            + "A．新厂长按部就班，提出了一整套创新方案"
        state.candidates = [
            MatchCandidate(id: idAnbu, word: "按部就班", frequency: 5, tier: .best),
            MatchCandidate(id: idMoshou, word: "墨守成规", frequency: 4, tier: .sameQuestion),
            MatchCandidate(id: idBiechu, word: "别出心裁", frequency: 4, tier: .semantic)
        ]
        state.checkedId = idAnbu
        state.poolSize = 3
        state.loading = false
        return state
    }

    private func emptyState() -> P20MatchState {
        var state = P20MatchState()
        state.reviewText = "今天天气不错，我们出去散步吧。"
        state.loading = false
        return state
    }

    private func actions() -> P20MatchActions {
        P20MatchActions(
            onEdit: {},
            onSelect: { _ in },
            onCycle: {},
            onAdd: {},
            onToggleDelete: {},
            onDelete: { _ in },
            onOpenManual: {},
            onDismissManual: {},
            onManualPick: { _ in },
            onSave: {},
            onDismissInfo: {},
            manualPicker: { _, _ in AnyView(EmptyView()) }
        )
    }

    func testMainLight() {
        assertP20(mainState(), dark: false)
    }

    func testMainDark() {
        assertP20(mainState(), dark: true)
    }

    func testEmptyLight() {
        assertP20(emptyState(), dark: false)
    }

    func testEmptyDark() {
        assertP20(emptyState(), dark: true)
    }

    private let idAnbu = "11111111-1111-4111-8111-111111111111"
    private let idBiechu = "22222222-2222-4222-8222-222222222222"
    private let idMoshou = "33333333-3333-4333-8333-333333333333"
}
