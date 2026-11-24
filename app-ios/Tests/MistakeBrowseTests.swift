import CangyuData
import XCTest

@testable import ChenYuBang

/// P08 / P09 取值纯函数（与 Android MistakeBrowse 同口径）：只验字段呈现，不碰数据库。
final class MistakeBrowseTests: XCTestCase {
    func testGroupRowTexts() {
        let group = MistakeIdiomGroup(
            idiomID: "i1",
            word: "按部就班",
            pinyin: "an bu jiu ban",
            initials: "abjb",
            count: 3
        )
        let view = MistakeBrowse.view(of: group)
        XCTAssertEqual(view.id, "i1")
        XCTAssertEqual(view.word, "按部就班")
        XCTAssertEqual(view.countText, "3 道")
    }

    func testFlatRowCarriesDateAndAssociatedIdioms() {
        let row = MistakeRow(
            id: "m1",
            userExampleID: "e1",
            stem: "下列各句中，加点的成语使用不恰当的一项是",
            createdAt: "2026-11-14T08:30:00Z",
            examType: "国考",
            state: "待复习",
            idiomWords: ["按部就班", "墨守成规"]
        )
        let view = MistakeBrowse.view(of: row)
        XCTAssertEqual(view.id, "m1")
        // P08 平铺行点入 P10 用的是录入题 id（路由 `mistake/{id}` 的值），不是错题 id。
        XCTAssertEqual(view.userExampleID, "e1")
        XCTAssertEqual(view.stem, row.stem)
        XCTAssertEqual(view.dateText, "2026-11-14 录入")
        XCTAssertEqual(view.idiomsText, "按部就班、墨守成规")
    }

    func testMissingIdiomWordsShowImportStateHint() {
        XCTAssertEqual(MistakeBrowse.idiomsText([]), "暂未关联成语")
        XCTAssertEqual(MistakeBrowse.idiomsText(["", "按部就班"]), "按部就班")
    }

    func testDateTextKeepsStoredUtcDateWithoutTimezoneShift() {
        // 库内按 UTC 秒级写入，显示只取日期段：跨零点录入的题不能显示成第二天。
        XCTAssertEqual(MistakeBrowse.dateText("2026-11-14T23:59:59Z"), "2026-11-14 录入")
    }

    func testMistakeRowKeepsMarkedFlagAndTrimmedMistakeFields() {
        let example = IdiomCapturedExample(
            id: "e1",
            stem: "文段中三处成语使用，哪一处不当",
            createdAt: "2026-11-10T00:00:00Z",
            isMistake: true,
            mistakeState: "待复习",
            reason: "  望文生义\n"
        )
        let view = MistakeBrowse.view(of: example)
        XCTAssertTrue(view.isMistake)
        XCTAssertEqual(view.state, "待复习")
        XCTAssertEqual(view.reason, "望文生义")
        XCTAssertEqual(view.dateText, "2026-11-10 录入")
    }

    func testUnmarkedRowHasNoStateOrReason() {
        let view = MistakeBrowse.view(of: IdiomCapturedExample(
            id: "e2",
            stem: "填入横线处最恰当的一项",
            createdAt: "2026-11-09T00:00:00Z",
            isMistake: false,
            mistakeState: nil,
            reason: ""
        ))
        XCTAssertFalse(view.isMistake)
        XCTAssertNil(view.state)
        XCTAssertNil(view.reason)
    }

    func testSenseLineTrimsAndFallsBackToPlaceholder() {
        XCTAssertEqual(MistakeBrowse.senseLine("贬义。原指照老规矩办事。"), "贬义。原指照老规矩办事。")
        XCTAssertEqual(MistakeBrowse.senseLine("   "), MistakeBrowse.placeholder)
        XCTAssertEqual(MistakeBrowse.senseLine(nil), MistakeBrowse.placeholder)
    }

    func testReviewTitleAndFooter() {
        XCTAssertEqual(MistakeBrowse.reviewTitle(pending: 7), "开始复习 · 7 道待复习")
        XCTAssertEqual(MistakeBrowse.reviewTitle(pending: 0), "开始复习 · 0 道待复习")
        XCTAssertEqual(MistakeBrowse.capturedFooter(3), "3 道 · 已到末尾")
    }

    func testDimensionLabelsAndRowOrders() {
        XCTAssertEqual(MistakeDimension.allCases.map(\.label), ["按成语分组", "按录入时间", "按考试类型"])
        XCTAssertEqual(MistakeDimension.createdAt.rowOrder, .byCreatedAt)
        XCTAssertEqual(MistakeDimension.examType.rowOrder, .byExamType)
    }

    // MARK: - P10 录入题详情

    func testSourceLineJoinsYearAndExamType() {
        XCTAssertEqual(MistakeBrowse.sourceLine(examType: "国考", year: 2023), "2023 · 国考")
        // 空白类型当作没填，只出年份；两者皆缺显示「未填」，不拿别的字段凑。
        XCTAssertEqual(MistakeBrowse.sourceLine(examType: " 省考 ", year: nil), "省考")
        XCTAssertEqual(MistakeBrowse.sourceLine(examType: "国考", year: nil), "国考")
        XCTAssertEqual(MistakeBrowse.sourceLine(examType: "", year: nil), "未填")
        XCTAssertEqual(MistakeBrowse.sourceLine(examType: nil, year: nil), "未填")
    }

    func testRerollResultComparesWithFirstAnswerOnly() {
        // 本版无正确答案来源：只与首次「我的答案」比，页面上不出现「答对 / 答错」判分字样。
        XCTAssertEqual(MistakeBrowse.rerollResult(first: 1, picked: 1), "与首次作答一致（B）")
        XCTAssertEqual(MistakeBrowse.rerollResult(first: 1, picked: 2), "与首次作答不同：首次 B，本次 C")
        XCTAssertEqual(
            MistakeBrowse.rerollResult(first: nil, picked: 3),
            "本题未记录你的首次作答，无法比对（本次选了 D）"
        )
        XCTAssertEqual(MistakeBrowse.rerollResult(first: 2, picked: nil), "尚未选择")
        XCTAssertTrue(MistakeBrowse.rerollNote.contains("未记录正确答案"))
    }

    func testMaskAndRerollGateAnswerVisibility() {
        var state = UserExampleState(loading: false, detail: detail(isMistake: true, myAnswer: "B"))
        XCTAssertTrue(state.answerHidden, "⑤ 默认遮住")
        state.revealed = true
        XCTAssertFalse(state.answerHidden)
        XCTAssertTrue(state.canToggleMask)
        // 进重做即盖回首次作答，中途不给看，对答案后才露出（信息架构 v1.4 版内修订第 ③ 条）。
        state.rerolling = true
        XCTAssertTrue(state.answerHidden)
        XCTAssertFalse(state.canToggleMask)
        state.picked = 2
        state.checked = true
        XCTAssertFalse(state.answerHidden)
        XCTAssertEqual(state.firstPick, 1)
    }

    func testUnmarkedDetailShowsAnswerAndNoReroll() {
        let state = UserExampleState(loading: false, detail: detail(isMistake: false, myAnswer: "C"))
        XCTAssertFalse(state.isMistake)
        XCTAssertFalse(state.answerHidden, "未标错的走普通详情")
        XCTAssertFalse(state.canToggleMask)
        XCTAssertEqual(state.firstPick, 2)
        XCTAssertEqual(UserExampleState(loading: false, detail: detail(isMistake: true, myAnswer: nil)).firstPick, nil)
    }

    func testBottomDeleteRowOnlyForMarkedExample() {
        // 一处红规则的适用范围（信息架构 v1.4 版内修订第 ④ 条，用户 2026-11-22 拍板）：
        // 底部红描边「删除这道错题」仅标错的录入题渲染，未标错整行不出现（不是置灰）。
        let marked = UserExampleState(loading: false, detail: detail(isMistake: true, myAnswer: "B"))
        let unmarked = UserExampleState(loading: false, detail: detail(isMistake: false, myAnswer: "C"))
        XCTAssertTrue(marked.showsBottomDeleteRow)
        XCTAssertFalse(unmarked.showsBottomDeleteRow)
    }

    private func detail(isMistake: Bool, myAnswer: String?) -> UserExampleDetail {
        UserExampleDetail(
            id: "e1",
            stem: "下列各句中，加点的成语使用不恰当的一项是",
            options: [
                "新厂长按部就班，提出了一整套创新方案",
                "他别出心裁地解决了这个难题",
                "这种做法墨守成规，难有突破",
                "方案循序渐进，稳步推进"
            ],
            myAnswer: myAnswer,
            isMistake: isMistake,
            examType: "国考",
            year: 2023,
            imagePath: nil,
            createdAt: "2026-11-14T00:00:00Z",
            idioms: [LinkedIdiom(id: "i1", word: "按部就班")],
            mistakeState: isMistake ? "待复习" : nil,
            reason: isMistake ? "望文生义" : nil,
            tagNames: isMistake ? ["易混词"] : []
        )
    }
}
