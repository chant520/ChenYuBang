import XCTest
@testable import ChenYuBang

/// P20 状态机单测：三档判定 / 多归属（≥1）/ 换加删 / 空态回填 / 表单校验 / JSON 降级。
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
        XCTAssertEqual(state.checked.map(\.id), [idAnbu])
        XCTAssertTrue(tier(of: idAnbu, in: state) == .best)
        XCTAssertTrue(tier(of: idMoshou, in: state) == .sameQuestion)
        XCTAssertTrue(tier(of: idBiechu, in: state) == .semantic)
        XCTAssertEqual(state.candidates.filter { state.checkedIDs.contains($0.id) }.count, 1)
    }

    func testBestTieBrokenByFrequencyThenWord() {
        let yiWord = idiom("id-y", "乙成语", 4)
        let jia = idiom("id-j", "甲成语", 4)
        let model = P20MatchModel()
        model.bootstrap(text: "乙成语 和 甲成语", idioms: [yiWord, jia])
        let state = model.state
        XCTAssertEqual(state.checked.map(\.id), ["id-y"]) // 乙(U+4E59) < 甲(U+7532)
        XCTAssertTrue(tier(of: "id-y", in: state) == .best)
        XCTAssertTrue(tier(of: "id-j", in: state) == .sameQuestion)
    }

    func testSampleB_emptyThenManualBackfill() {
        let model = P20MatchModel()
        model.bootstrap(text: "今天天气不错，我们出去散步吧。", idioms: sampleIdioms)
        XCTAssertTrue(model.state.empty)
        XCTAssertTrue(model.state.checked.isEmpty)
        XCTAssertFalse(model.state.canSave)

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
        XCTAssertEqual(state.checked.map(\.id), [idBiechu])
        XCTAssertEqual(state.candidates.first?.tier, .manual)
        XCTAssertEqual(state.candidates.first?.tier.label, "手动选择")
        XCTAssertTrue(state.canSave)
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

    func testCycleAdvancesByWindowAndWrapsWithoutLosingChecked() {
        let idioms = ["甲一", "乙二", "丙三", "丁四", "戊五"].enumerated().map {
            idiom("id-\($0.offset)", $0.element, 3)
        }
        let ordered = idioms.map(\.word).sorted() // 与 buildPool 的 word 升序一致
        let model = P20MatchModel()
        model.bootstrap(text: "甲一 乙二 丙三 丁四 戊五", idioms: idioms)
        XCTAssertEqual(model.state.candidates.map(\.word), Array(ordered.prefix(3)))
        XCTAssertTrue(model.state.canCycle)
        let checked = model.state.checked

        model.cycle()
        XCTAssertEqual(model.state.candidates.map(\.word), Array(ordered.dropFirst(3).prefix(3)))
        XCTAssertEqual(model.state.checked, checked)
        model.cycle()
        XCTAssertEqual(model.state.candidates.map(\.word), Array(ordered.dropFirst(1).prefix(3)))
        XCTAssertEqual(model.state.checked, checked)

        let small = P20MatchModel()
        small.bootstrap(text: "甲一 乙二 丙三", idioms: Array(idioms.prefix(3)))
        XCTAssertFalse(small.state.canCycle)
        small.cycle()
        XCTAssertEqual(small.state.info, P20Matcher.noMoreCandidates)
    }

    func testCheckedOutsideWindowCanBeRemovedFromChips() throws {
        let idioms = ["甲一", "乙二", "丙三", "丁四", "戊五"].enumerated().map {
            idiom("id-\($0.offset)", $0.element, 3)
        }
        let model = P20MatchModel()
        model.bootstrap(text: "甲一 乙二 丙三 丁四 戊五", idioms: idioms)
        let id = try XCTUnwrap(model.state.checked.first?.id)
        model.cycle()
        XCTAssertFalse(model.state.candidates.contains { $0.id == id })
        XCTAssertTrue(model.state.checkedIDs.contains(id))
        XCTAssertTrue(model.state.showsCheckedChips)

        model.select(id: id)
        XCTAssertFalse(model.state.checkedIDs.contains(id))
        XCTAssertFalse(model.state.showsCheckedChips)
        XCTAssertFalse(model.state.canSave)
    }

    func testAddAppendsByFrequencyWithoutChangingCheck() {
        let extra = [idiom("id-extra", "额外词", 9), idiom("id-low", "低频词", 1)]
        let model = P20MatchModel()
        model.bootstrap(text: "按部就班", idioms: sampleIdioms + extra)
        let before = model.state.checked
        model.add()
        let state = model.state
        // 「按部就班」同时是墨守成规的 synonyms，故命中 2 条，加一个后为 3 条。
        XCTAssertEqual(state.candidates.count, 3)
        XCTAssertEqual(state.candidates.last?.word, "额外词")
        XCTAssertEqual(state.candidates.last?.tier, .added)
        XCTAssertEqual(state.checked, before)
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

    func testDeleteCheckedEntryRemovesItWithoutAutoReselect() {
        // 「标新立异」只让别出心裁走 synonyms 档；删掉最匹配后剩下的词命中档重算。
        let model = P20MatchModel()
        model.bootstrap(text: "按部就班 墨守成规 标新立异", idioms: sampleIdioms)
        model.toggleDeleteMode()
        XCTAssertTrue(model.state.deleteMode)
        model.remove(id: idAnbu)
        XCTAssertFalse(model.state.candidates.contains { $0.id == idAnbu })
        XCTAssertFalse(model.state.checkedIDs.contains(idAnbu))
        XCTAssertTrue(model.state.checked.isEmpty)
        XCTAssertFalse(model.state.canSave)

        model.remove(id: idMoshou)
        model.remove(id: idBiechu)
        XCTAssertTrue(model.state.empty)
        XCTAssertTrue(model.state.checked.isEmpty)
        XCTAssertFalse(model.state.deleteMode)
    }

    func testDeleteOtherRowKeepsUserCheckAndRepromotesBest() {
        let model = P20MatchModel()
        model.bootstrap(text: "按部就班 墨守成规 标新立异", idioms: sampleIdioms)
        model.select(id: idMoshou)
        XCTAssertEqual(model.state.checkedIDs, [idAnbu, idMoshou])
        model.toggleDeleteMode()

        // 删语义相近行：不动用户勾选。
        model.remove(id: idBiechu)
        XCTAssertEqual(model.state.checkedIDs, [idAnbu, idMoshou])

        // 删掉原最匹配：勾选项仍是墨守成规，且升为最匹配。
        model.remove(id: idAnbu)
        XCTAssertEqual(model.state.checked.map(\.id), [idMoshou])
        XCTAssertTrue(tier(of: idMoshou, in: model.state) == .best)
    }

    func testBestSurvivesCandidateTruncation() {
        // 61 条词命中，最匹配按 word 升序排在第 51 位之后，不能被 prefix(50) 截掉。
        let filler = (0..<60).map { idiom("id-f\($0)", String(format: "阿%02d", $0), 1) }
        let best = idiom("id-best", "词最末", 99)
        let all = filler + [best]
        let model = P20MatchModel()
        model.bootstrap(text: all.map(\.word).joined(separator: " "), idioms: all)
        let state = model.state
        XCTAssertEqual(state.poolSize, 50)
        XCTAssertTrue(state.candidates.contains { $0.id == "id-best" })
        XCTAssertEqual(state.checked.map(\.id), ["id-best"])
        XCTAssertTrue(tier(of: "id-best", in: state) == .best)
    }

    func testSelectTogglesMultipleAndCancelLastDisablesSave() {
        let model = P20MatchModel()
        model.bootstrap(text: "按部就班 墨守成规 标新立异", idioms: sampleIdioms)
        XCTAssertTrue(model.state.canSave)

        model.select(id: idMoshou)
        XCTAssertEqual(model.state.checkedIDs, [idAnbu, idMoshou])
        XCTAssertTrue(model.state.showsCheckedChips)
        model.select(id: idMoshou)
        XCTAssertEqual(model.state.checkedIDs, [idAnbu])

        model.select(id: idAnbu)
        XCTAssertTrue(model.state.checked.isEmpty)
        XCTAssertFalse(model.state.canSave)
        XCTAssertNil(model.state.info)
    }

    func testFormSanitizeConditionalFieldsAndSaveGate() {
        let model = P20MatchModel()
        model.bootstrap(text: "按部就班", idioms: sampleIdioms)

        model.setAnswerText("b9x")
        XCTAssertEqual(model.state.form.answerText, "B")
        XCTAssertEqual(model.state.form.answer, "B")
        model.setAnswerText("z")
        XCTAssertEqual(model.state.form.answerText, "")
        model.setAnswerText("")

        model.setReason("未标错不可填")
        XCTAssertEqual(model.state.form.reason, "")
        model.toggleMistake()
        XCTAssertTrue(model.state.form.isMistake)
        model.setReason("望文生义")
        XCTAssertEqual(model.state.form.reason, "望文生义")
        XCTAssertTrue(model.addTag("易混词"))
        XCTAssertEqual(model.state.form.tags, ["易混词"])
        XCTAssertFalse(model.addTag("易混词"))
        XCTAssertEqual(model.state.info, P20Matcher.tagRule)
        XCTAssertFalse(model.addTag("123456789"))
        XCTAssertFalse(model.addTag(" "))
        model.removeTag("易混词")
        XCTAssertTrue(model.state.form.tags.isEmpty)

        model.setYearText("20a23")
        XCTAssertEqual(model.state.form.yearText, "2023")
        XCTAssertEqual(model.state.form.year, 2023)
        model.setYearText("12")
        XCTAssertTrue(model.state.form.yearInvalid)
        XCTAssertFalse(model.state.canSave)
        model.setYearText("2023")
        XCTAssertFalse(model.state.form.yearInvalid)
        XCTAssertTrue(model.state.canSave)

        model.setExamTypeText(" 国考 ")
        XCTAssertEqual(model.state.form.examType, "国考")
        model.setExamTypeText("  ")
        XCTAssertNil(model.state.form.examType)

        model.toggleMistake()
        XCTAssertFalse(model.state.form.isMistake)
        XCTAssertEqual(model.state.form.reason, "")
        XCTAssertTrue(model.state.form.tags.isEmpty)
        model.setReason("toggle 后不可填")
        XCTAssertEqual(model.state.form.reason, "")
    }

    func testSaveLifecycleKeepsInputOnFailure() {
        let model = P20MatchModel()
        model.bootstrap(text: "按部就班", idioms: sampleIdioms)
        model.setAnswerText("B")
        model.beginSave()
        XCTAssertTrue(model.state.form.saving)
        XCTAssertFalse(model.state.canSave)
        model.failSave()
        XCTAssertFalse(model.state.form.saving)
        XCTAssertEqual(model.state.form.saveError, P20Matcher.saveFailed)
        XCTAssertEqual(model.state.form.answerText, "B")
        model.dismissSaveError()
        XCTAssertNil(model.state.form.saveError)
        model.beginSave()
        model.finishSave()
        XCTAssertFalse(model.state.form.saving)
        XCTAssertTrue(model.state.canSave)
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
