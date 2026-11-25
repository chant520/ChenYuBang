import CangyuData
import XCTest

@testable import ChenYuBang

/// P08 读库口子夹具：只回固定的总览数与一行列表，用于钉「开始复习」的页内行为，不碰数据库。
private struct StubMistakeReader: MistakeBookReading {
    var pending = 4
    var failOnRead = false

    func overview() throws -> MistakeOverview {
        if failOnRead { throw StubMistakeError.read }
        return MistakeOverview(total: 7, pending: pending)
    }

    func idiomGroups() throws -> [MistakeIdiomGroup] {
        if failOnRead { throw StubMistakeError.read }
        return [MistakeIdiomGroup(
            idiomID: "i1",
            word: "按部就班",
            pinyin: "an bu jiu ban",
            initials: "abjb",
            count: 3
        )]
    }

    func rows(order: MistakeRowOrder) throws -> [MistakeRow] {
        if failOnRead { throw StubMistakeError.read }
        return [MistakeRow(
            id: "m1",
            userExampleID: "e1",
            stem: "下列各句中，加点的成语使用不恰当的一项是",
            createdAt: "2026-11-14T08:30:00Z",
            examType: "国考",
            state: "待复习",
            idiomWords: ["按部就班"]
        )]
    }
}

private enum StubMistakeError: Error { case read }

/// 复习入口在 P08 的落地（信息架构 v1.4 跳转表 19）：队列已空时不开复习、页内如实提示。
/// 提示条与置灰按钮在 C9 手动标「已掌握」后可达；列表与总数仍保留，不与零错题空态混淆。
@MainActor
final class P08ReviewHintTests: XCTestCase {
    func testEmptyReviewHintIsTheGlobalQueueEmptyTitle() {
        XCTAssertEqual(MistakeBrowse.emptyReviewHint, "今天没有待复习的错题")
        XCTAssertEqual(MistakeBrowse.gotIt, "知道了")
    }

    func testLoadKeepsPendingCountForReviewButton() async {
        let model = MistakeBookViewModel(reader: StubMistakeReader())
        await model.load()
        XCTAssertFalse(model.state.loading)
        XCTAssertEqual(model.state.total, 7)
        XCTAssertEqual(model.state.pending, 4)
        XCTAssertEqual(MistakeBrowse.reviewTitle(pending: model.state.pending), "开始复习 · 4 道待复习")
    }

    func testPendingClearedCopyDistinguishesAllLearnedFromNoMistakes() {
        XCTAssertEqual(MistakeBrowse.pendingClearedTitle, "待复习已清空")
        XCTAssertEqual(MistakeBrowse.pendingClearedNote, "已掌握的错题仍保留在错题本中，可切回待复习后继续复习。")
    }

    func testPendingZeroStillPrintsZeroInsteadOfHidingTheNumber() async {
        let model = MistakeBookViewModel(reader: StubMistakeReader(pending: 0))
        await model.load()
        XCTAssertEqual(MistakeBrowse.reviewTitle(pending: model.state.pending), "开始复习 · 0 道待复习")
        XCTAssertEqual(model.state.total, 7)
        XCTAssertEqual(model.state.groups.count, 1, "全部已掌握仍显示错题列表，不误入「还没有错题」空态")
    }

    func testHintAppearsAndIsDismissableWithoutTouchingTheList() async {
        let model = MistakeBookViewModel(reader: StubMistakeReader())
        await model.load()
        model.showEmptyReviewHint()
        XCTAssertEqual(model.state.pageMessage, MistakeBrowse.emptyReviewHint)
        XCTAssertEqual(model.state.groups.count, 1, "提示只加一条中性说明，列表与总览数不动")
        model.dismissMessage()
        XCTAssertNil(model.state.pageMessage)
    }

    func testReadFailureUsesTheFailureBarInsteadOfTheNeutralHint() async {
        let model = MistakeBookViewModel(reader: StubMistakeReader(failOnRead: true))
        await model.load()
        XCTAssertTrue(model.state.failed)
        XCTAssertTrue(model.state.groups.isEmpty)
        XCTAssertNil(model.state.pageMessage, "读取失败不给「今天没有待复习的错题」这种误导文案")
    }

    func testViewSwitchKeepsOverviewNumbersAndOnlyReloadsTheList() async {
        let model = MistakeBookViewModel(reader: StubMistakeReader())
        await model.load()
        await model.select(.createdAt)
        XCTAssertEqual(model.state.dimension, .createdAt)
        XCTAssertEqual(model.state.entries.count, 1)
        XCTAssertTrue(model.state.groups.isEmpty)
        XCTAssertEqual(model.state.pending, 4)
    }
}
