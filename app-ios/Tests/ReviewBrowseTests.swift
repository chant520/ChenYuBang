import CangyuData
import XCTest

@testable import ChenYuBang

/// 复习判分与本轮数字（开发规范第 8.2 节 2026-11-23 版内修订）：纯函数，不碰数据库。
/// 判分串逐字钉住第 8.2 节规范串；无法比对的一条与 P10 重做串对照，防止两处文案各自漂移。
final class ReviewBrowseTests: XCTestCase {
    func testDifferentFromFirstAnswerIsCorrect() {
        XCTAssertEqual(ReviewBrowse.judge(first: "A", picked: "C"), .different)
    }

    func testSameAsFirstAnswerIsWrong() {
        XCTAssertEqual(ReviewBrowse.judge(first: "A", picked: "A"), .same)
    }

    func testMissingFirstAnswerIsUncomparableButStillPicked() {
        XCTAssertEqual(ReviewBrowse.judge(first: nil, picked: "B"), .unknownFirst(picked: "B"))
        XCTAssertEqual(ReviewBrowse.judge(first: "", picked: "B"), .unknownFirst(picked: "B"))
        // 首次作答记了非法值（非 A–D）同样不可比对，不猜答案。
        XCTAssertEqual(ReviewBrowse.judge(first: "E", picked: "B"), .unknownFirst(picked: "B"))
    }

    func testNoSelectionHasNoOutcome() {
        XCTAssertNil(ReviewBrowse.judge(first: "A", picked: nil))
        XCTAssertNil(ReviewBrowse.judge(first: "A", picked: ""))
        XCTAssertNil(ReviewBrowse.judge(first: "A", picked: "E"))
    }

    func testComparableResultTextsAreTheSpecStrings() {
        // 第 8.2 节写死的规范串（与 Android `ReviewScoring` 同口径），不重复选项字母。
        XCTAssertEqual(ReviewBrowse.resultText(.same), "与首次作答相同")
        XCTAssertEqual(ReviewBrowse.resultText(.different), "与首次作答不同")
    }

    func testUncomparableResultTextFollowsTheP10Wording() {
        // 无法比对的一条沿用 P10「本题未记录你的首次作答」口径（第 8.2 节）。
        XCTAssertEqual(
            ReviewBrowse.resultText(.unknownFirst(picked: "C")),
            MistakeBrowse.rerollResult(first: nil, picked: 2)
        )
        XCTAssertEqual(
            ReviewBrowse.resultText(.unknownFirst(picked: "C")),
            "本题未记录你的首次作答，无法比对（本次选了 C）"
        )
    }

    func testJudgeNoteIsTheSpecDisclosure() {
        // 判分区说明一行是第 8.2 节的固定句；P10 重做另有一句更长的说明，两者不互抄。
        XCTAssertEqual(ReviewBrowse.judgeNote, "本题未记录正确答案")
        XCTAssertEqual(ReviewBrowse.noOptionsNote, "本题未记录选项，无法作答比对。")
    }

    func testUncomparableQuestionsCountInTotalOnly() {
        // 3 题里 1 题无法比对：答错数只从可比对的 2 题里算。
        let outcome = ReviewRoundOutcome(total: 3, answered: 2, correct: 1)
        XCTAssertEqual(outcome.total, 3)
        XCTAssertEqual(outcome.answered, 2)
        XCTAssertEqual(outcome.wrong, 1)
    }

    func testRoundOutcomeWithoutAnyComparableQuestionHasNoWrong() {
        XCTAssertEqual(ReviewRoundOutcome(total: 2, answered: 0, correct: 0).wrong, 0)
        // 计数被外部算多也不给出负数答错。
        XCTAssertEqual(ReviewRoundOutcome(total: 2, answered: 1, correct: 2).wrong, 0)
    }

    func testScopeLineSeparatesGlobalAndSingleIdiom() {
        XCTAssertEqual(ReviewScope.global().line, "全部待复习")
        XCTAssertEqual(ReviewScope.idiom(id: "i1", word: "按部就班").line, "只看『按部就班』")
    }

    func testReviewTimestampIsIso8601UtcSeconds() {
        let text = ReviewTimestamp.now()
        XCTAssertTrue(text.hasSuffix("Z"), "落库时间用 UTC 串，与 user_example 写库口径一致：\(text)")
        XCTAssertEqual(text.count, 20, "ISO8601 秒级串：\(text)")
    }
}
