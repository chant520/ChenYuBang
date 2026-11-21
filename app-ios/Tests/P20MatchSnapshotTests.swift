import XCTest
import SnapshotTesting
import SwiftUI
import CangyuTokens
@testable import ChenYuBang

/// P20 黄金快照（规范 §11.1）：浅/深主态 + 浅/深空态 + 表单区，基线目录 __Snapshots__/P20MatchSnapshotTests/。
/// 2.5 版式变更后改用新测试名，2.4 的旧基线 PNG 保留不覆盖。
/// 非空候选卡补上「手动挑成语」入口（信息架构 v1.4 P20 ③）后，主态同名会撞到旧基线，
/// 故改走 testFormMainWithManual*；空态未变，testFormEmpty* 继续用原基线。
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

    private func assertForm(
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P20FormSection(form: filledForm(), actions: formActions())
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.block)
            .background(dark ? Tokens.dark.page : Tokens.light.page)
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 390, height: 640)),
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
        state.checked = [
            MatchCandidate(id: idAnbu, word: "按部就班", frequency: 5, tier: .best),
            MatchCandidate(id: idMoshou, word: "墨守成规", frequency: 4, tier: .sameQuestion)
        ]
        state.poolSize = 3
        state.loading = false
        state.form = filledForm()
        return state
    }

    private func emptyState() -> P20MatchState {
        var state = P20MatchState()
        state.reviewText = "今天天气不错，我们出去散步吧。"
        state.loading = false
        return state
    }

    private func filledForm() -> P20FormState {
        P20FormState(
            answerText: "B",
            reason: "望文生义",
            tags: ["易混词"],
            isMistake: true,
            examType: "国考",
            yearText: "2023"
        )
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
            form: formActions(),
            manualPicker: { _, _ in AnyView(EmptyView()) }
        )
    }

    private func formActions() -> P20FormActions {
        P20FormActions(
            onAnswer: { _ in },
            onReason: { _ in },
            onAddTag: { _ in false },
            onRemoveTag: { _ in },
            onToggleMistake: {},
            onExamType: { _ in },
            onYear: { _ in },
            onDismissSaveError: {}
        )
    }

    func testFormMainWithManualLight() {
        assertP20(mainState(), dark: false)
    }

    func testFormMainWithManualDark() {
        assertP20(mainState(), dark: true)
    }

    func testFormEmptyLight() {
        assertP20(emptyState(), dark: false)
    }

    func testFormEmptyDark() {
        assertP20(emptyState(), dark: true)
    }

    func testFormSectionLight() {
        assertForm(dark: false)
    }

    func testFormSectionDark() {
        assertForm(dark: true)
    }

    private let idAnbu = "11111111-1111-4111-8111-111111111111"
    private let idBiechu = "22222222-2222-4222-8222-222222222222"
    private let idMoshou = "33333333-3333-4333-8333-333333333333"
}
