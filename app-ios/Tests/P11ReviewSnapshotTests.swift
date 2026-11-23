import CangyuData
import CangyuTokens
import SnapshotTesting
import SwiftUI
import XCTest

@testable import ChenYuBang

/// P11 复习队列快照（开发规范第 11.1 节）：判分四种口径 + 落库失败 + 两种空态，各 × 浅深。
///
/// 基线目录 `__Snapshots__/P11ReviewSnapshotTests/`。判分区只演「与首次作答比对」，
/// 页面不出现正确答案与解析（第 8.2 节）；两种空态文案按信息架构 v1.4 第 5.4 节 P11 两行，
/// 单成语空态是直接进入后的兜底帧，文档已明确要建，故一并录。
/// 退出二次确认弹层由 `P11ReviewScreen` 挂在页面外层、不在纯渲染层内，像素由 COverlay 构件快照覆盖。
@MainActor
final class P11ReviewSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertP11(
        _ state: P11ReviewState,
        scope: ReviewScope = .global(),
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P11ReviewContent(
            state: state,
            scope: scope,
            actions: actions(),
            colors: dark ? Tokens.dark : Tokens.light
        ).environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 390, height: 844)),
            file: file,
            testName: testName,
            line: line
        )
    }

    private func item(_ id: String, first: String?, options: [String?] = ["甲", "乙", "丙", "丁"]) -> ReviewQueueItem {
        ReviewQueueItem(
            id: id,
            stem: "填入横线处最恰当的一项：改革不能______，要敢于突破既有做法。",
            options: options,
            myAnswer: first,
            createdAt: "2026-11-14T08:30:00Z"
        )
    }

    /// 本轮三题：首次答 A、首次答 C、首次未答（不可比对）。
    private func roundItems() -> [ReviewQueueItem] {
        [item("m1", first: "A"), item("m2", first: "C"), item("m3", first: nil)]
    }

    private func actions() -> P11ReviewActions {
        P11ReviewActions(
            onPick: { _ in },
            onNext: {},
            onAskExit: {},
            onLeave: {},
            onRetryLoad: {},
            onRetryRecord: {}
        )
    }

    func testAwaitingPickLight() {
        assertP11(P11ReviewState(loading: false, items: roundItems()), dark: false)
    }

    func testAwaitingPickDark() {
        assertP11(P11ReviewState(loading: false, items: roundItems()), dark: true)
    }

    /// 与首次作答相同＝答错（第 8.2 节）。
    func testJudgedSameAsFirstLight() {
        assertP11(
            P11ReviewState(
                loading: false, items: roundItems(), index: 0, picked: "A",
                judged: true, recorded: true, answeredCount: 1, wrongCount: 1
            ),
            dark: false
        )
    }

    func testJudgedSameAsFirstDark() {
        assertP11(
            P11ReviewState(
                loading: false, items: roundItems(), index: 0, picked: "A",
                judged: true, recorded: true, answeredCount: 1, wrongCount: 1
            ),
            dark: true
        )
    }

    /// 与首次作答不同＝答对。
    func testJudgedDifferentFromFirstLight() {
        assertP11(
            P11ReviewState(
                loading: false, items: roundItems(), index: 1, picked: "B",
                judged: true, recorded: true, answeredCount: 1, correctCount: 1
            ),
            dark: false
        )
    }

    func testJudgedDifferentFromFirstDark() {
        assertP11(
            P11ReviewState(
                loading: false, items: roundItems(), index: 1, picked: "B",
                judged: true, recorded: true, answeredCount: 1, correctCount: 1
            ),
            dark: true
        )
    }

    /// 首次「我的答案」没记录：如实说明无法比对，不伪造判分、不计入判分题数。
    func testJudgedUncomparableLight() {
        assertP11(
            P11ReviewState(
                loading: false, items: roundItems(), index: 2, picked: "C",
                judged: true, recorded: true, answeredCount: 1, correctCount: 1
            ),
            dark: false
        )
    }

    func testJudgedUncomparableDark() {
        assertP11(
            P11ReviewState(
                loading: false, items: roundItems(), index: 2, picked: "C",
                judged: true, recorded: true, answeredCount: 1, correctCount: 1
            ),
            dark: true
        )
    }

    /// 一个选项都没录的题：不可作答，页内说明后仍可推进，只计题量。
    func testQuestionWithoutOptionsLight() {
        assertP11(
            P11ReviewState(
                loading: false, items: [item("m4", first: "A", options: [nil, nil, nil, nil])],
                judged: true, recorded: true
            ),
            dark: false
        )
    }

    func testQuestionWithoutOptionsDark() {
        assertP11(
            P11ReviewState(
                loading: false, items: [item("m4", first: "A", options: [nil, nil, nil, nil])],
                judged: true, recorded: true
            ),
            dark: true
        )
    }

    /// 成绩没落库前不给「下一题」，红条可重试（不丢成绩）。
    func testRecordFailedLight() {
        assertP11(
            P11ReviewState(
                loading: false, items: roundItems(), index: 0, picked: "A",
                judged: true, recorded: false, recordFailed: true, wrongCount: 1
            ),
            dark: false
        )
    }

    func testRecordFailedDark() {
        assertP11(
            P11ReviewState(
                loading: false, items: roundItems(), index: 0, picked: "A",
                judged: true, recorded: false, recordFailed: true, wrongCount: 1
            ),
            dark: true
        )
    }

    func testGlobalEmptyLight() {
        assertP11(P11ReviewState(loading: false), dark: false)
    }

    func testGlobalEmptyDark() {
        assertP11(P11ReviewState(loading: false), dark: true)
    }

    func testIdiomEmptyLight() {
        assertP11(P11ReviewState(loading: false), scope: .idiom(id: "i1", word: "按部就班"), dark: false)
    }

    func testIdiomEmptyDark() {
        assertP11(P11ReviewState(loading: false), scope: .idiom(id: "i1", word: "按部就班"), dark: true)
    }
}
