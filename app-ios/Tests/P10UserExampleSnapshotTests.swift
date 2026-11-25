import CangyuData
import CangyuTokens
import SnapshotTesting
import SwiftUI
import XCTest

@testable import ChenYuBang

/// P10 录入题详情快照（规范 §11.1）：遮罩 / 重做三态 / 未标错 × 浅深，基线目录 `__Snapshots__/P10UserExampleSnapshotTests/`。
///
/// 只录本版可达状态：C9 待复习 / 已掌握 / 处理中均由手动切换或写入中到达；
/// 区块 ⑥「正确答案 + 一句解析」本版无来源、不渲染（信息架构 v1.4 版内修订第 ① 条），故无对应帧；
/// 读取失败（橙条）与「没有找到这道录入题」属边界，由构件矩阵覆盖同款 `CBar` / `CEmptyState`；
/// 有原图的缩略帧取的是本机拍照文件，快照不注入二进制，图片位以「原图已不在」橙占位为准。
///
/// 本页是长页：844 视口帧只排到区块 ⑨ 首行，滚动区外的 ⑦⑧⑨⑩ 与底部删除行拍不到，
/// 只对视口上方负责；故另出按自然高度的区块帧覆盖 ⑤–⑩ 全段：`testBlocks*` 取标错的录入题（含底部红描边删除行），
/// `testBlocksUnmarked*` 取未标错的，钉住该整行不出现（信息架构 v1.4 版内修订第 ④ 条，用户 2026-11-22 拍板）——
/// 未标错态的删除入口只有顶部置灰「删除」，页底无红动作在 844 视口帧里本来就拍不到。
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
        file: StaticString = #filePath, testName: String = #function, line: UInt = #line
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

    private func example(isMistake: Bool, reviewState: String? = nil) -> UserExampleDetail {
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
            mistakeState: reviewState ?? (isMistake ? "待复习" : nil),
            mistakeID: isMistake ? "mistake-e1" : nil,
            reason: isMistake ? "望文生义：只按字面理解「按部就班」的褒贬" : nil,
            tagNames: isMistake ? ["易混词", "褒贬误用"] : []
        )
    }

    /// 标错的录入题：D 项 OCR 缺项走「暂无」占位，原图位按「已不在」橙占位（本机无图片文件）。
    private func mistakeState(
        revealed: Bool = false,
        rerolling: Bool = false,
        picked: Int? = nil,
        checked: Bool = false, reviewState: String = "待复习",
        updatingReviewState: Bool = false
    ) -> UserExampleState {
        UserExampleState(
            loading: false,
            detail: example(isMistake: true, reviewState: reviewState),
            revealed: revealed,
            rerolling: rerolling,
            picked: picked,
            checked: checked,
            updatingReviewState: updatingReviewState,
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

    /// 区块帧取「已显示答案」的标错态：覆盖 ⑤ 遮罩行、⑦⑧⑨、⑩ 两态分段控件与底部红描边删除行。
    func testBlocksLight() {
        assertP10Blocks(mistakeState(revealed: true), dark: false)
    }

    func testBlocksDark() {
        assertP10Blocks(mistakeState(revealed: true), dark: true)
    }

    func testBlocksProficientLight() {
        assertP10Blocks(mistakeState(revealed: true, reviewState: "已掌握"), dark: false)
    }

    func testBlocksProficientDark() {
        assertP10Blocks(mistakeState(revealed: true, reviewState: "已掌握"), dark: true)
    }

    /// 写入中：分段控件禁用并显示处理中，底部红动作同时禁用。
    func testBlocksUpdatingReviewStateLight() { assertP10Blocks(mistakeState(updatingReviewState: true), dark: false) }

    /// 未标错的区块帧：844 视口拍不到页底，整行删除是否出现只有按自然高度的帧才算证据——本对帧钉住「⑤ 与底部红描边删除行整段不出现」。
    func testBlocksUnmarkedLight() {
        assertP10Blocks(UserExampleState(loading: false, detail: example(isMistake: false)), dark: false)
    }

    func testBlocksUnmarkedDark() {
        assertP10Blocks(UserExampleState(loading: false, detail: example(isMistake: false)), dark: true)
    }

    // MARK: - 2.8 编辑与删除（C8）

    /// 编辑态整页帧（844 视口）：钉住顶部换成「取消 / 保存」、原图位仍在且只读、题面已换成输入框。
    private func assertP10Page(
        _ state: UserExampleState,
        dark: Bool,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P10UserExampleContent(state: state, actions: actions(), colors: dark ? Tokens.dark : Tokens.light)
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(of: view, as: .image(layout: .fixed(width: 390, height: 844)),
                       testName: testName, line: line)
    }

    /// 编辑态表单（自然高度）：844 视口拍不全题干以下与归属区，故单帧覆盖整张表单。
    private func assertP10EditForm(
        _ draft: UserExampleDraft,
        saving: Bool = false,
        dark: Bool,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P10UserExampleEditForm(draft: draft, saving: saving, actions: .inert)
            .frame(width: 390 - Tokens.Space.pageX * 2, alignment: .top)
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.block)
            .background((dark ? Tokens.dark : Tokens.light).page)
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(of: view, as: .image(layout: .sizeThatFits), testName: testName, line: line)
    }

    /// 单条二次确认（规范 §8.1.1）：满屏帧含蒙层，钉住弹层覆起后「同一屏最多一处红」的那一处。
    private func assertP10DeleteConfirm(
        isMistake: Bool,
        dark: Bool,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P10UserExampleDeleteConfirm(isMistake: isMistake, deleting: false, onConfirm: {}, onDismiss: {})
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(of: view, as: .image(layout: .fixed(width: 390, height: 844)), testName: testName, line: line)
    }

    /// 字段级非法草稿：年份 `20` 与答案 `E` 两处非法。
    /// 不塞超长标签——标签在 P20 与本页编辑器都是加入时把关，库里存不出越界长度，
    /// 把它画进基线等于给走不到的分支钉图。
    private func draft(isMistake: Bool, invalid: Bool = false) -> UserExampleDraft {
        var draft = UserExampleDraft(detail: example(isMistake: isMistake))
        guard invalid else { return draft }
        draft.yearText = "20"
        draft.answerText = "E"
        return draft
    }

    private func editingState(draft: UserExampleDraft, issue: UserExampleIssue? = nil) -> UserExampleState {
        var state = UserExampleState(
            loading: false,
            detail: example(isMistake: draft.isMistake),
            imageUnreadable: draft.isMistake
        )
        state.draft = draft
        state.issue = issue
        return state
    }

    func testEditingLight() {
        assertP10Page(editingState(draft: draft(isMistake: true)), dark: false)
    }

    func testEditingDark() {
        assertP10Page(editingState(draft: draft(isMistake: true)), dark: true)
    }

    /// 字段级非法：年份与答案标红、「保存」置灰但仍在位（校验不隐藏入口）。
    /// 844 视口只排到「我的答案」，年份那一栏在滚动区外，故另出下方 `testEditFormInvalidLight` 整帧。
    func testEditingInvalidLight() {
        assertP10Page(editingState(draft: draft(isMistake: true, invalid: true)), dark: false)
    }

    /// 写库失败：红错误条 + 「知道了」，草稿留在原地不清空（规范 §9.1）。
    func testEditingSaveFailedLight() {
        assertP10Page(
            editingState(
                draft: draft(isMistake: true),
                issue: UserExampleIssue(kind: .error, message: UserExampleEditCopy.saveFailed)
            ),
            dark: false
        )
    }

    func testEditFormLight() {
        assertP10EditForm(draft(isMistake: true), dark: false)
    }

    func testEditFormDark() {
        assertP10EditForm(draft(isMistake: true), dark: true)
    }

    /// 未标错的表单：错因与标签两栏整段不出现（这两项只存在于 `mistake`，规范 §4.1）。
    func testEditFormUnmarkedLight() {
        assertP10EditForm(draft(isMistake: false), dark: false)
    }

    /// 非法草稿的整张表单（自然高度）：844 视口拍不到年份，本帧才真有「年份标红 + 规则提示」的证据。
    func testEditFormInvalidLight() {
        assertP10EditForm(draft(isMistake: true, invalid: true), dark: false)
    }

    /// 保存中：字段全部禁用、增补入口（加入标签 / 添加归属成语）整段收起。
    func testEditFormSavingLight() {
        assertP10EditForm(draft(isMistake: true), saving: true, dark: false)
    }

    func testConfirmDeleteMistakeLight() {
        assertP10DeleteConfirm(isMistake: true, dark: false)
    }

    func testConfirmDeleteMistakeDark() {
        assertP10DeleteConfirm(isMistake: true, dark: true)
    }

    func testConfirmDeleteExampleLight() {
        assertP10DeleteConfirm(isMistake: false, dark: false)
    }
}
