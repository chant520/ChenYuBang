import CangyuData
import CangyuTokens
import SnapshotTesting
import SwiftUI
import XCTest

@testable import ChenYuBang

/// P10 录入题详情快照（规范 §11.1）：遮罩 / 重做三态 / 未标错 × 浅深，基线目录 `__Snapshots__/P10UserExampleSnapshotTests/`。
///
/// 只录本版可达状态：`已掌握` 标签要等 3.1 复习判分改 `mistake.state` 才会出现，本版不录；
/// 区块 ⑥「正确答案 + 一句解析」本版无来源、不渲染（信息架构 v1.4 版内修订第 ① 条），故无对应帧；
/// 读取失败（橙条）与「没有找到这道录入题」属边界，由构件矩阵覆盖同款 `CBar` / `CEmptyState`；
/// 有原图的缩略帧取的是本机拍照文件，快照不注入二进制，图片位以「原图已不在」橙占位为准。
///
/// 本页是长页：844 视口帧只排到区块 ⑨ 首行，滚动区外的 ⑦⑧⑨⑩ 与底部删除行拍不到，
/// 只对视口上方负责；故另出 `testBlocksLight` / `testBlocksDark` 两帧，按自然高度单帧覆盖 ⑤–⑩ 全段。
/// 区块帧取标错的录入题（含底部红描边删除行）；`testUnmarked*` 两帧同时钉住未标错态整行不出现
/// （信息架构 v1.4 版内修订第 ④ 条，用户 2026-11-22 拍板），其删除入口只有顶部置灰「删除」。
@MainActor
final class P10UserExampleSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertP10(
        _ state: UserExampleState,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P10UserExampleContent(state: state, actions: actions(), colors: dark ? Tokens.dark : Tokens.light)
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 390, height: 844)),
            file: file,
            testName: testName,
            line: line
        )
    }

    /// 下半页区块（⑤–⑩ 与底部删除行）：844 视口拍不到滚动区外的内容，故按自然高度单帧覆盖。
    private func assertP10Blocks(
        _ state: UserExampleState,
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        guard let detail = state.detail else {
            XCTFail("区块帧需要 detail")
            return
        }
        let view = P10UserExampleBlocks(
            state: state,
            detail: detail,
            actions: actions(),
            colors: dark ? Tokens.dark : Tokens.light
        )
        .frame(width: 390 - Tokens.Space.pageX * 2, alignment: .top)
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.block)
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .sizeThatFits),
            file: file,
            testName: testName,
            line: line
        )
    }

    private func example(isMistake: Bool) -> UserExampleDetail {
        UserExampleDetail(
            id: "e1",
            stem: "下列各句中，加点的成语使用不恰当的一项是",
            options: [
                "新厂长按部就班，提出了一整套创新方案",
                "他别出心裁地解决了这个难题",
                "这种做法墨守成规，难有突破",
                nil
            ],
            myAnswer: "B",
            isMistake: isMistake,
            examType: "国考",
            year: 2023,
            imagePath: isMistake ? "images/e1.jpg" : nil,
            createdAt: "2026-11-14T00:00:00Z",
            idioms: [
                LinkedIdiom(id: "i1", word: "按部就班"),
                LinkedIdiom(id: "i2", word: "墨守成规")
            ],
            mistakeState: isMistake ? "待复习" : nil,
            reason: isMistake ? "望文生义：只按字面理解「按部就班」的褒贬" : nil,
            tagNames: isMistake ? ["易混词", "褒贬误用"] : []
        )
    }

    /// 标错的录入题：D 项 OCR 缺项走「暂无」占位，原图位按「已不在」橙占位（本机无图片文件）。
    private func mistakeState(
        revealed: Bool = false,
        rerolling: Bool = false,
        picked: Int? = nil,
        checked: Bool = false
    ) -> UserExampleState {
        UserExampleState(
            loading: false,
            detail: example(isMistake: true),
            revealed: revealed,
            rerolling: rerolling,
            picked: picked,
            checked: checked,
            imageUnreadable: true
        )
    }

    private func actions() -> UserExampleActions {
        UserExampleActions(
            onBack: {},
            onRetry: {},
            onToggleAnswer: {},
            onPickOption: { _ in },
            onStartReroll: {},
            onCheck: {},
            onExitReroll: {},
            onOpenIdiom: { _ in },
            onEdit: {},
            onDelete: {}
        )
    }

    func testMaskedLight() {
        assertP10(mistakeState(), dark: false)
    }

    func testMaskedDark() {
        assertP10(mistakeState(), dark: true)
    }

    func testRevealedLight() {
        assertP10(mistakeState(revealed: true), dark: false)
    }

    func testRevealedDark() {
        assertP10(mistakeState(revealed: true), dark: true)
    }

    func testRerollingPickedLight() {
        assertP10(mistakeState(rerolling: true, picked: 2), dark: false)
    }

    func testRerollingPickedDark() {
        assertP10(mistakeState(rerolling: true, picked: 2), dark: true)
    }

    /// 对答案后：露出首次作答，并给出与首次作答的比对结果（本版不判分）。
    func testRerollCheckedLight() {
        assertP10(mistakeState(rerolling: true, picked: 2, checked: true), dark: false)
    }

    func testRerollCheckedDark() {
        assertP10(mistakeState(rerolling: true, picked: 2, checked: true), dark: true)
    }

    func testUnmarkedLight() {
        assertP10(UserExampleState(loading: false, detail: example(isMistake: false)), dark: false)
    }

    func testUnmarkedDark() {
        assertP10(UserExampleState(loading: false, detail: example(isMistake: false)), dark: true)
    }

    /// 区块帧取「已显示答案」的标错态：一次覆盖 ⑤ 遮罩行与重做按钮、⑦ 出处、⑧ 关联成语、⑨ 答案与错因与标签、⑩ 预留位、底部删除行。
    func testBlocksLight() {
        assertP10Blocks(mistakeState(revealed: true), dark: false)
    }

    func testBlocksDark() {
        assertP10Blocks(mistakeState(revealed: true), dark: true)
    }
}
