import XCTest
@testable import ChenYuBang

/// P20 候选状态机单测：三档判定 / 唯一勾选 / 换加删 / 空态回填 / JSON 降级（工作包 2.4 验收样例 A/B/C）。
/// 与 Android P20MatchViewModelTest 同口径，样例词表对齐 shared/fixtures/idioms.sample.json。
@MainActor
final class P20MatchModelTests: XCTestCase {
    private let idAnbu = "11111111-1111-4111-8111-111111111111"
    private let idBiechu = "22222222-2222-4222-8222-222222222222"
    private let idMoshou = "33333333-3333-4333-8333-333333333333"

    private var anbu: MatchIdiom { idiom(idAnbu, "按部就班", 5, "[\"墨守成规\",\"循规蹈矩\"]") }
    private var biechu: MatchIdiom { idiom(idBiechu, "别出心裁", 4, "[\"独出心裁\",\"标新立异\"]") }
    private var moshou: MatchIdiom { idiom(idMoshou, "墨守成规", 4, "[\"故步自封\",\"按部就班\"]") }
    private var sampleIdioms: [MatchIdiom] { [anbu, biechu, moshou] }

    func testSampleA_threeTiersWithUniqueBest() {
        let text = "下列各句中，加点的成语使用不恰当的一项是……\n"
            + "A．新厂长按部就班，提出了一整套创新方案\n"
            + "B．他做事标新立异，从不墨守成规"
        let model = P20MatchModel()
        model.bootstrap(text: text, idioms: sampleIdioms)
        let state = model.state
        XCTAssertEqual(state.poolSize, 3)
        XCTAssertEqual(state.candidates.count, 3)
        XCTAssertEqual(state.checkedId, idAnbu)
        XCTAssertTrue(tier(of: idAnbu, in: state) == .best)
        XCTAssertTrue(tier(of: idMoshou, in: state) == .sameQuestion)
        XCTAssertTrue(tier(of: idBiechu, in: state) == .semantic)
        XCTAssertEqual(state.candidates.filter { $0.id == state.checkedId }.count, 1)
    }

    func testBestTieBrokenByFrequencyThenWord() {
        let yi = idiom("id-y", "乙成语", 4)
        let jia = idiom("id-j", "甲成语", 4)
        let model = P20MatchModel()
        model.bootstrap(text: "乙成语 和 甲成语", idioms: [yi, jia])
        let state = model.state
        XCTAssertEqual(state.checkedId, "id-y") // 乙(U+4E59) < 甲(U+7532)
        XCTAssertTrue(tier(of: "id-y", in: state) == .best)
        XCTAssertTrue(tier(of: "id-j", in: state) == .sameQuestion)
    }

    func testSampleB_emptyThenManualBackfill() {
        let model = P20MatchModel()
        model.bootstrap(text: "今天天气不错，我们出去散步吧。", idioms: sampleIdioms)
        XCTAssertTrue(model.state.empty)
        XCTAssertNil(model.state.checkedId)

        // 不选直接关闭：候选状态不变。
        model.setManualPicker(visible: true)
        XCTAssertTrue(model.state.showManualPicker)
        model.setManualPicker(visible: false)
        XCTAssertFalse(model.state.showManualPicker)
        XCTAssertTrue(model.state.empty)

        // 选中回填：覆盖候选区、默认勾该条、标「手动选择」。
        model.setManualPicker(visible: true)
        model.pickManual(id: idBiechu)
        let state = model.state
        XCTAssertFalse(state.showManualPicker)
        XCTAssertEqual(state.candidates.count, 1)
        XCTAssertEqual(state.checkedId, idBiechu)
        XCTAssertEqual(state.candidates.first?.tier, .manual)
        XCTAssertEqual(state.candidates.first?.tier.label, "手动选择")
    }

    func testSampleC_typoDoesNotFuzzyMatch() {
        let model = P20MatchModel()
        model.bootstrap(text: "新厂长按步就班，提出了一整套方案", idioms: sampleIdioms)
        XCTAssertTrue(model.state.empty)
        XCTAssertTrue(model.state.candidates.isEmpty)
    }

    func testSynonymsJsonFailureDegradesToEmpty() {
        XCTAssertEqual(P20Matcher.synonyms(of: "{not json"), [])
        XCTAssertEqual(P20Matcher.synonyms(of: "[\"ok\", broken"), [])
        let broken = idiom("id-broken", "无关词", 3, "{\"标新立异\"}")
        let model = P20MatchModel()
        model.bootstrap(text: "他做事标新立异", idioms: [broken])
        XCTAssertTrue(model.state.empty)
    }

    func testCycleAdvancesByWindowAndWraps() {
        let idioms = ["甲一", "乙二", "丙三", "丁四", "戊五"].enumerated().map {
            idiom("id-\($0.offset)", $0.element, 3)
        }
        let ordered = idioms.map(\.word).sorted() // 与 buildPool 的 word 升序一致
        let model = P20MatchModel()
        model.bootstrap(text: "甲一 乙二 丙三 丁四 戊五", idioms: idioms)
        XCTAssertEqual(model.state.candidates.map(\.word), Array(ordered.prefix(3)))
        XCTAssertTrue(model.state.canCycle)

        model.cycle()
        XCTAssertEqual(model.state.candidates.map(\.word), Array(ordered.dropFirst(3).prefix(3)))
        model.cycle()
        XCTAssertEqual(model.state.candidates.map(\.word), Array(ordered.dropFirst(1).prefix(3)))

        let small = P20MatchModel()
        small.bootstrap(text: "甲一 乙二 丙三", idioms: Array(idioms.prefix(3)))
        XCTAssertFalse(small.state.canCycle)
        small.cycle()
        XCTAssertEqual(small.state.info, P20Matcher.noMoreCandidates)
    }

    func testAddAppendsByFrequencyWithoutChangingCheck() {
        let extra = [idiom("id-extra", "额外词", 9), idiom("id-low", "低频词", 1)]
        let model = P20MatchModel()
        model.bootstrap(text: "按部就班", idioms: sampleIdioms + extra)
        let before = model.state.checkedId
        model.add()
        let state = model.state
        // 「按部就班」同时是墨守成规的 synonyms，故命中 2 条，加一个后为 3 条。
        XCTAssertEqual(state.candidates.count, 3)
        XCTAssertEqual(state.candidates.last?.word, "额外词")
        XCTAssertEqual(state.candidates.last?.tier, .added)
        XCTAssertEqual(state.checkedId, before)
    }

    func testAddAtFullWindowShowsLimit() {
        let many = (0..<50).map { idiom("id-\($0)", "词\($0)", 3) }
        let text = many.map(\.word).joined(separator: " ")
        let model = P20MatchModel()
        model.bootstrap(text: text, idioms: many)
        for _ in 0..<50 { model.add() }
        XCTAssertEqual(model.state.candidates.count, 50)
        XCTAssertFalse(model.state.canAdd)
        XCTAssertEqual(model.state.info, P20Matcher.candidateLimit)
    }

    func testDeleteRemovesEntryAndReselectsThenEmpty() {
        // 「标新立异」只让别出心裁走 synonyms 档；删掉最匹配后剩下唯一的 word 命中词墨守成规。
        let model = P20MatchModel()
        model.bootstrap(text: "按部就班 墨守成规 标新立异", idioms: sampleIdioms)
        model.toggleDeleteMode()
        XCTAssertTrue(model.state.deleteMode)
        model.remove(id: idAnbu)
        XCTAssertFalse(model.state.candidates.contains { $0.id == idAnbu })
        XCTAssertEqual(model.state.checkedId, idMoshou)

        model.remove(id: idMoshou)
        model.remove(id: idBiechu)
        XCTAssertTrue(model.state.empty)
        XCTAssertNil(model.state.checkedId)
        XCTAssertFalse(model.state.deleteMode)
    }

    func testSelectAlwaysKeepsExactlyOne() {
        let model = P20MatchModel()
        model.bootstrap(text: "按部就班 墨守成规", idioms: [anbu, moshou])
        model.select(id: idMoshou)
        XCTAssertEqual(model.state.checkedId, idMoshou)
        model.select(id: idMoshou)
        XCTAssertEqual(model.state.checkedId, idMoshou)
        XCTAssertEqual(model.state.candidates.filter { $0.id == model.state.checkedId }.count, 1)
    }

    private func tier(of id: String, in state: P20MatchState) -> MatchTier? {
        state.candidates.first { $0.id == id }?.tier
    }

    private func idiom(
        _ id: String,
        _ word: String,
        _ frequency: Int,
        _ synonyms: String = "[]"
    ) -> MatchIdiom {
        MatchIdiom(id: id, word: word, synonyms: synonyms, frequency: frequency)
    }
}
