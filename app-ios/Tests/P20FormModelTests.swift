import XCTest
@testable import ChenYuBang

/// P20 表单区单测：答案 / 错因 / 标签 / 年份 / 来源清洗与保存回执，自匹配用例拆出。
@MainActor
final class P20FormModelTests: XCTestCase {
    private var sampleIdioms: [MatchIdiom] {
        [idiom("11111111-1111-4111-8111-111111111111", "按部就班", 5, "[\"墨守成规\",\"循规蹈矩\"]")]
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

    private func idiom(
        _ id: String,
        _ word: String,
        _ frequency: Int,
        _ synonyms: String = "[]"
    ) -> MatchIdiom {
        MatchIdiom(id: id, word: word, synonyms: synonyms, frequency: frequency)
    }
}
