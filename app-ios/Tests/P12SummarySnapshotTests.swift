import CangyuData
import CangyuTokens
import SnapshotTesting
import SwiftUI
import XCTest

@testable import ChenYuBang

/// P12 复习小结快照（开发规范第 11.1 节）：全局 / 单成语两种来源的收尾 × 浅深 + 成语读取失败帧 + 0 题兜底态。
///
/// 基线目录 `__Snapshots__/P12SummarySnapshotTests/`。成绩数字取本轮内存小结（`review_log`
/// 无轮次概念，不按库重算，第 8.2 节）；夹具给的是「4 题里 1 题无法比对」的真实形态：
/// 题量 4、答对 2、答错 1，让「无法比对的题只进题量」这条口径在像素上也能看出来。
/// 「本轮错题没有可跳转的成语」一行在本版不可达（标错至少一个归属，见 2.5），不录基线。
@MainActor
final class P12SummarySnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertP12(
        _ state: P12SummaryState,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P12SummaryContent(
            state: state,
            actions: P12SummaryActions(
                onOpenIdiom: { _ in },
                onHome: {},
                onRetryRound: {},
                onRetryLoad: {}
            ),
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

    /// 本轮碰到的成语：库内去重后按词面升序（第 8.2 节），夹具给三条。
    private func roundIdioms() -> [LinkedIdiom] {
        [
            LinkedIdiom(id: "i1", word: "按部就班"),
            LinkedIdiom(id: "i3", word: "别出心裁"),
            LinkedIdiom(id: "i2", word: "墨守成规")
        ]
    }

    private func globalState() -> P12SummaryState {
        P12SummaryState(loading: false, total: 4, correct: 2, wrong: 1, idioms: roundIdioms())
    }

    private func idiomState() -> P12SummaryState {
        P12SummaryState(
            loading: false,
            scope: .idiom(id: "i1", word: "按部就班"),
            total: 4,
            correct: 2,
            wrong: 1,
            idioms: roundIdioms()
        )
    }

    func testGlobalRoundLight() {
        assertP12(globalState(), dark: false)
    }

    func testGlobalRoundDark() {
        assertP12(globalState(), dark: true)
    }

    func testIdiomRoundLight() {
        assertP12(idiomState(), dark: false)
    }

    func testIdiomRoundDark() {
        assertP12(idiomState(), dark: true)
    }

    /// 涉及成语读取失败：本轮数字照常保留，只在该卡内给一行说明与重试。
    func testGlobalIdiomsLoadFailedLight() {
        var state = globalState()
        state.failed = true
        state.idioms = []
        assertP12(state, dark: false)
    }

    func testGlobalIdiomsLoadFailedDark() {
        var state = globalState()
        state.failed = true
        state.idioms = []
        assertP12(state, dark: true)
    }

    /// 0 题兜底态（信息架构 v1.4 第 5.4 节 P12 行 / 高保真稿 D P12「0 题」帧）：题量卡照常给 0/0/0，
    /// 收尾只留「回上一页」。正常路径不可达（空队列进不了 P11），与 Android 同样按异常态钉住像素。
    func testZeroRoundLight() {
        assertP12(P12SummaryState(loading: false, total: 0), dark: false)
    }

    func testZeroRoundDark() {
        assertP12(P12SummaryState(loading: false, total: 0), dark: true)
    }

    /// C9：本轮完成后队列被清空，再复习重查为空时保留成绩卡并给中性提示。
    func testRetryQueueEmptyLight() {
        var state = globalState()
        state.retryIssue = "当前范围已没有待复习的错题"
        assertP12(state, dark: false)
    }
}
