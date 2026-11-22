import CangyuTokens
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ChenYuBang

/// P08 错题本快照（规范 §11.1）：三视图中的分组与平铺 × 浅深 + 空态 × 浅深。
///
/// 基线目录 `__Snapshots__/P08MistakeSnapshotTests/`。只录本版可达状态：
/// 「待复习已清空」空态要等 3.x 复习判分改 `mistake.state` 才会出现，本版不录（不把死分支钉成基线）。
@MainActor
final class P08MistakeSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertP08(
        _ state: MistakeBookState,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P08MistakeContent(state: state, actions: actions(), colors: dark ? Tokens.dark : Tokens.light)
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 390, height: 844)),
            file: file,
            testName: testName,
            line: line
        )
    }

    /// ③「按成语分组」：错题数降序，右侧「N 道」。
    private func groupedState() -> MistakeBookState {
        MistakeBookState(
            loading: false,
            dimension: .grouped,
            total: 7,
            pending: 4,
            groups: [
                MistakeGroupView(id: "i1", word: "按部就班", countText: "3 道"),
                MistakeGroupView(id: "i2", word: "墨守成规", countText: "2 道"),
                MistakeGroupView(id: "i3", word: "别出心裁", countText: "1 道")
            ]
        )
    }

    /// ③「按录入时间」：题干 + 录入时间 + 关联成语（P10 在工作包 2.7，行不可点）。
    private func flatState() -> MistakeBookState {
        MistakeBookState(
            loading: false,
            dimension: .createdAt,
            total: 7,
            pending: 4,
            entries: [
                MistakeEntryView(
                    id: "m1",
                    stem: "下列各句中，加点的成语使用不恰当的一项是",
                    dateText: "2026-11-14 录入",
                    idiomsText: "墨守成规、按部就班"
                ),
                MistakeEntryView(
                    id: "m2",
                    stem: "填入横线处最恰当的一项：改革不能______",
                    dateText: "2026-11-09 录入",
                    idiomsText: "按部就班"
                ),
                // 关联成语为空（「暂无」）在 2.6 不可达：标错至少挑一个成语，故快照只取有归属的行，空值由单元测试覆盖。
                MistakeEntryView(
                    id: "m3",
                    stem: "文段中三处成语使用，哪一处不当",
                    dateText: "2026-10-28 录入",
                    idiomsText: "别出心裁"
                )
            ]
        )
    }

    private func emptyState() -> MistakeBookState {
        MistakeBookState(loading: false, total: 0, pending: 0)
    }

    private func actions() -> MistakeBookActions {
        MistakeBookActions(onSelectDimension: { _ in }, onOpenIdiom: { _ in }, onCapture: {}, onRetry: {})
    }

    func testGroupedLight() {
        assertP08(groupedState(), dark: false)
    }

    func testGroupedDark() {
        assertP08(groupedState(), dark: true)
    }

    func testFlatLight() {
        assertP08(flatState(), dark: false)
    }

    func testFlatDark() {
        assertP08(flatState(), dark: true)
    }

    func testEmptyLight() {
        assertP08(emptyState(), dark: false)
    }

    func testEmptyDark() {
        assertP08(emptyState(), dark: true)
    }
}
