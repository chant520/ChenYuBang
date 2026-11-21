import CangyuData
import XCTest

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
        XCTAssertEqual(view.stem, row.stem)
        XCTAssertEqual(view.dateText, "2026-11-14 录入")
        XCTAssertEqual(view.idiomsText, "按部就班、墨守成规")
    }

    func testMissingIdiomWordsFallBackToPlaceholder() {
        XCTAssertEqual(MistakeBrowse.idiomsText([]), MistakeBrowse.placeholder)
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

    func testReviewTitleAndFooters() {
        XCTAssertEqual(MistakeBrowse.reviewTitle(pending: 7), "开始复习 · 7 道待复习")
        XCTAssertEqual(MistakeBrowse.reviewTitle(pending: 0), "开始复习 · 0 道待复习")
        XCTAssertEqual(MistakeBrowse.bookFooter(12), "共 12 条 · 已到末尾")
        XCTAssertEqual(MistakeBrowse.capturedFooter(3), "3 道 · 已到末尾")
    }

    func testDimensionLabelsAndRowOrders() {
        XCTAssertEqual(MistakeDimension.allCases.map(\.label), ["按成语分组", "按录入时间", "按考试类型"])
        XCTAssertEqual(MistakeDimension.createdAt.rowOrder, .byCreatedAt)
        XCTAssertEqual(MistakeDimension.examType.rowOrder, .byExamType)
    }
}
