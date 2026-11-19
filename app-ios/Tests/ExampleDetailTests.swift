import CangyuData
import GRDB
import XCTest
@testable import ChenYuBang

/// P07 例题详情（工作包 1.5，验收 5/7）：取值口径、成语高亮匹配、关联成语读库与视图模型。
/// 口径与 Android ExampleDetail / IdiomHighlight / ExampleDao 对齐；macOS CI 的 xcodebuild test 执行。
final class ExampleDetailTests: XCTestCase {
    private var queue: DatabaseQueue!

    override func setUpWithError() throws {
        queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
    }

    override func tearDownWithError() throws {
        queue = nil
    }

    // MARK: - 取值口径

    func testSourceLineJoinsAllParts() throws {
        let record = try insertExample(id: "ex-1", year: 2023, region: "国考", examType: "言语理解", questionType: "逻辑填空")
        XCTAssertEqual(ExampleDetail.sourceLine(record), "2023 · 国考 · 言语理解 · 逻辑填空")
    }

    func testSourceLineSkipsMissingParts() throws {
        let record = try insertExample(id: "ex-2", year: 0, region: " ", examType: "省考", questionType: "")
        XCTAssertEqual(ExampleDetail.sourceLine(record), "省考")
    }

    func testSourceLineAllMissingFallsBackToPlaceholder() throws {
        let record = try insertExample(id: "ex-3", year: 0, region: " ", examType: "", questionType: "")
        XCTAssertEqual(ExampleDetail.sourceLine(record), ExampleDetail.placeholder)
    }

    func testViewFillsPlaceholdersForBlankFields() throws {
        let record = try insertExample(id: "ex-4", year: 2024, region: "国考", examType: "中央", questionType: "填空")
        let view = ExampleDetail.view(of: record)
        XCTAssertEqual(view.id, "ex-4")
        XCTAssertEqual(view.stem, ExampleDetail.placeholder)
        XCTAssertEqual(view.answer, ExampleDetail.placeholder)
        XCTAssertEqual(view.sourceLine, "2024 · 国考 · 中央 · 填空")
    }

    // MARK: - 成语高亮

    private let wordIndex = [
        "idiom-anbu": "按部就班",
        "idiom-moshou": "墨守成规",
        "idiom-biechu": "别出心裁",
        "idiom-chenggui": "成规"
    ]

    func testHighlightsMultipleIdioms() {
        let text = "新厂长按部就班地创新，又墨守成规地守旧。"
        let hits = IdiomHighlight.match(text, wordIndex: ["idiom-anbu": "按部就班", "idiom-moshou": "墨守成规"])
        XCTAssertEqual(hits.map { $0.word }, ["按部就班", "墨守成规"])
        XCTAssertEqual(hits.map { $0.idiomID }, ["idiom-anbu", "idiom-moshou"])
    }

    func testUnknownIdiomDoesNotMatch() {
        XCTAssertTrue(IdiomHighlight.match("新厂长循序渐进地推进。", wordIndex: wordIndex).isEmpty)
    }

    func testEmptyInputsDoNotMatch() {
        XCTAssertTrue(IdiomHighlight.match("", wordIndex: wordIndex).isEmpty)
        XCTAssertTrue(IdiomHighlight.match("按部就班", wordIndex: [:]).isEmpty)
    }

    func testLongerWordWinsOverSubstring() {
        let hits = IdiomHighlight.match("做法墨守成规难有突破", wordIndex: wordIndex)
        XCTAssertEqual(hits.map { $0.word }, ["墨守成规"])
    }

    func testRepeatedOccurrencesUseRealOffsets() {
        let text = "按部就班不是按部就班地拖延。"
        let hits = IdiomHighlight.match(text, wordIndex: ["idiom-anbu": "按部就班"])
        XCTAssertEqual(hits.map { $0.start }, [0, 6])
        XCTAssertEqual(hits.map { $0.end }, [4, 10])
        let characters = Array(text)
        XCTAssertEqual(String(characters[hits[1].start..<hits[1].end]), "按部就班")
    }

    // MARK: - 读库

    func testReaderReturnsDetailLinksAndWords() throws {
        try insertIdiom(id: "idiom-anbu", word: "按部就班")
        try insertIdiom(id: "idiom-moshou", word: "墨守成规")
        _ = try insertExample(id: "ex-10", year: 2023, region: "国考", examType: "言语理解", questionType: "逻辑填空")
        try queue.write { db in
            try db.execute(
                sql: "INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex-10', 'idiom-moshou')"
            )
            try db.execute(
                sql: "INSERT INTO example_idiom (exampleId, idiomId) VALUES ('ex-10', 'idiom-anbu')"
            )
        }
        let reader = ExampleLibraryReader(queue: queue)
        let detail = try reader.detail(id: "ex-10")
        XCTAssertEqual(detail?.year, 2023)
        XCTAssertEqual(try reader.relatedIdiomIDs(exampleID: "ex-10"), ["idiom-anbu", "idiom-moshou"])
        let words = Set(try reader.idiomWords().map { $0.word })
        XCTAssertEqual(words, Set(["按部就班", "墨守成规"]))
        XCTAssertNil(try reader.detail(id: "ex-missing"))
    }

    // MARK: - 视图模型

    @MainActor
    func testViewModelLoadsRelatedWordsWithPlaceholderForUnknown() async throws {
        try insertIdiom(id: "idiom-anbu", word: "按部就班")
        _ = try insertExample(id: "ex-20", year: 2023, region: "国考", examType: "言语理解", questionType: "逻辑填空")
        insertLinks(exampleID: "ex-20", idiomIDs: ["idiom-anbu", "idiom-ghost"])
        let model = ExampleDetailViewModel(reader: ExampleLibraryReader(queue: queue))
        await model.load("ex-20")
        XCTAssertEqual(model.detail?.id, "ex-20")
        XCTAssertEqual(model.relatedIdioms.map { $0.word }, ["按部就班", ExampleDetail.placeholder])
        XCTAssertFalse(model.missing)
        XCTAssertFalse(model.failed)
    }

    @MainActor
    func testViewModelReportsMissing() async {
        let model = ExampleDetailViewModel(reader: ExampleLibraryReader(queue: queue))
        await model.load("ex-absent")
        XCTAssertTrue(model.missing)
        XCTAssertNil(model.detail)
    }

    // MARK: - Fixtures

    private func insertLinks(exampleID: String, idiomIDs: [String]) throws {
        try queue.write { db in
            for idiomID in idiomIDs {
                try db.execute(
                    sql: "INSERT INTO example_idiom (exampleId, idiomId) VALUES (?, ?)",
                    arguments: [exampleID, idiomID]
                )
            }
        }
    }

    @discardableResult
    private func insertIdiom(id: String, word: String) throws -> IdiomWordRef {
        try queue.write { db in
            try db.execute(
                sql: """
                    INSERT INTO idiom
                        (id, word, pinyin, initials, sense, origin, color, pitfalls, synonyms, antonyms, frequency)
                    VALUES (?, ?, 'pinyin', 'PY', '释义', NULL, '中', '[]', '[]', '[]', 1)
                    """,
                arguments: [id, word]
            )
        }
        return IdiomWordRef(id: id, word: word)
    }

    @discardableResult
    private func insertExample(
        id: String,
        year: Int,
        region: String,
        examType: String,
        questionType: String
    ) throws -> ExampleRecord {
        try queue.write { db in
            try db.execute(
                sql: """
                    INSERT INTO example
                        (id, stem, optionA, optionB, optionC, optionD, answer, explain,
                         examType, year, region, questionType, imagePath)
                    VALUES (?, '', 'A', 'B', 'C', 'D', '', '', ?, ?, ?, ?, NULL)
                    """,
                arguments: [id, examType, year, region, questionType]
            )
        }
        let inserted = try queue.read { db in try ExampleLibrary.detail(db, id: id) }
        guard let record = inserted else {
            throw XCTSkip("inserted example not found")
        }
        return record
    }
}
