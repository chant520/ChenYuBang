import CangyuData
import GRDB
import XCTest

@testable import ChenYuBang

private final class StubUserExampleReader: UserExampleReading, @unchecked Sendable {
    var detail: UserExampleDetail?
    var readCount = 0
    var onRead: (() -> Void)?

    func userExample(id: String) throws -> UserExampleDetail? {
        readCount += 1
        onRead?()
        return detail
    }
}

private final class StubUserExampleWriter: UserExampleWriting {
    func saveEdit(_ input: UserExampleEditInput) throws {}
    func deleteMistake(userExampleID: String) throws {}
    func deleteUserExample(id: String) throws {}
}

private final class StubMistakeStateWriter: MistakeStateWriting, @unchecked Sendable {
    var result: MistakeStateUpdateResult = .updated
    var failure: Error?
    var calls: [(id: String, state: MistakeReviewState)] = []
    var onWrite: (@MainActor @Sendable () async -> Void)?

    func setReviewState(
        mistakeID: String,
        state: MistakeReviewState
    ) async throws -> MistakeStateUpdateResult {
        calls.append((mistakeID, state))
        await onWrite?()
        if let failure { throw failure }
        return result
    }
}

private enum StubMistakeStateError: Error {
    case writeFailed
}

/// P10 手动掌握态：精确 id、成功刷新与回调、失败提示、重复点击和编辑互斥。
@MainActor
final class P10ReviewStateTests: XCTestCase {
    func testSuccessfulWriteUsesLatestMistakeIDThenReloadsAndNotifies() async throws {
        let reader = StubUserExampleReader()
        reader.detail = makeDetail()
        let editor = StubUserExampleWriter()
        let writer = StubMistakeStateWriter()
        var contentChanges = 0
        let model = UserExampleViewModel(
            id: "example-1",
            reader: reader,
            editor: editor,
            stateWriter: writer,
            libraryReader: IdiomLibraryReader(queue: try DatabaseQueue()),
            dataDirectory: nil,
            onContentChanged: { contentChanges += 1 }
        )
        await model.load()
        writer.onWrite = { reader.detail = self.makeDetail(reviewState: "已掌握") }

        await model.setReviewState(.proficient)

        XCTAssertEqual(writer.calls.count, 1)
        XCTAssertEqual(writer.calls.first?.id, "mistake-latest")
        XCTAssertEqual(writer.calls.first?.state, .proficient)
        XCTAssertEqual(contentChanges, 1)
        XCTAssertEqual(model.state.detail?.mistakeState, "已掌握")
        XCTAssertFalse(model.state.loading)
        XCTAssertFalse(model.state.updatingReviewState)
        XCTAssertNil(model.state.issue)
    }

    func testNotFoundShowsExplicitErrorWithoutCallbackOrReload() async throws {
        let reader = StubUserExampleReader()
        reader.detail = makeDetail()
        let writer = StubMistakeStateWriter()
        writer.result = .notFound
        var contentChanges = 0
        let model = try makeModel(reader, writer, onContentChanged: { contentChanges += 1 })
        await model.load()

        await model.setReviewState(.proficient)

        XCTAssertEqual(writer.calls.first?.id, "mistake-latest")
        XCTAssertEqual(model.state.issue?.message, UserExampleReviewStateCopy.notFound)
        XCTAssertEqual(model.state.detail?.mistakeState, "待复习")
        XCTAssertEqual(contentChanges, 0)
        XCTAssertEqual(reader.readCount, 1)
        XCTAssertFalse(model.state.updatingReviewState)
    }

    func testWriteExceptionShowsRetryableErrorAndKeepsCurrentState() async throws {
        let reader = StubUserExampleReader()
        reader.detail = makeDetail()
        let writer = StubMistakeStateWriter()
        writer.failure = StubMistakeStateError.writeFailed
        var contentChanges = 0
        let model = try makeModel(reader, writer, onContentChanged: { contentChanges += 1 })
        await model.load()

        await model.setReviewState(.proficient)

        XCTAssertEqual(model.state.issue?.message, UserExampleReviewStateCopy.updateFailed)
        XCTAssertEqual(model.state.detail?.mistakeState, "待复习")
        XCTAssertEqual(contentChanges, 0)
        XCTAssertEqual(reader.readCount, 1)
    }

    func testWriteInProgressBlocksRepeatedWriteEditAndDelete() async throws {
        let reader = StubUserExampleReader()
        reader.detail = makeDetail()
        let writer = StubMistakeStateWriter()
        let model = try makeModel(reader, writer)
        await model.load()
        writer.onWrite = {
            await model.setReviewState(.pending)
            model.startEdit()
            model.requestDelete()
        }

        await model.setReviewState(.proficient)

        XCTAssertEqual(writer.calls.count, 1, "处理中再次点击不产生第二次写入")
        XCTAssertNil(model.state.draft)
        XCTAssertFalse(model.state.confirmDelete)
    }

    func testEditingBlocksStateWriteUntilCancelled() async throws {
        let reader = StubUserExampleReader()
        reader.detail = makeDetail()
        let writer = StubMistakeStateWriter()
        let model = try makeModel(reader, writer)
        await model.load()

        model.startEdit()
        XCTAssertFalse(model.state.canChangeReviewState)
        await model.setReviewState(.proficient)
        XCTAssertTrue(writer.calls.isEmpty)

        model.cancelEdit()
        XCTAssertTrue(model.state.canChangeReviewState)
        await model.setReviewState(.proficient)
        XCTAssertEqual(writer.calls.count, 1)
    }

    func testDetailWithoutMistakeIDCannotWrite() async throws {
        let reader = StubUserExampleReader()
        reader.detail = makeDetail(mistakeID: nil)
        let writer = StubMistakeStateWriter()
        let model = try makeModel(reader, writer)
        await model.load()

        await model.setReviewState(.proficient)

        XCTAssertFalse(model.state.canChangeReviewState)
        XCTAssertTrue(writer.calls.isEmpty)
    }

    func testConsecutiveChangesReloadAndNotifyEachTime() async throws {
        let reader = StubUserExampleReader()
        reader.detail = makeDetail()
        let writer = StubMistakeStateWriter()
        var contentChanges = 0
        let model = try makeModel(reader, writer, onContentChanged: { contentChanges += 1 })
        await model.load()
        writer.onWrite = { reader.detail = self.makeDetail(reviewState: "已掌握") }

        await model.setReviewState(.proficient)
        XCTAssertEqual(contentChanges, 1)
        XCTAssertEqual(model.state.detail?.mistakeState, "已掌握")
        writer.onWrite = { reader.detail = self.makeDetail(reviewState: "待复习") }

        await model.setReviewState(.pending)
        XCTAssertEqual(writer.calls.map(\.state), [.proficient, .pending])
        XCTAssertEqual(reader.readCount, 3)
        XCTAssertEqual(contentChanges, 2)
        XCTAssertEqual(model.state.detail?.mistakeState, "待复习")
        XCTAssertFalse(model.state.updatingReviewState)
        XCTAssertNil(model.state.issue)
    }

    private func makeModel(
        _ reader: StubUserExampleReader,
        _ writer: StubMistakeStateWriter,
        onContentChanged: @escaping () -> Void = {}
    ) throws -> UserExampleViewModel {
        UserExampleViewModel(
            id: "example-1",
            reader: reader,
            editor: StubUserExampleWriter(),
            stateWriter: writer,
            libraryReader: IdiomLibraryReader(queue: try DatabaseQueue()),
            dataDirectory: nil,
            onContentChanged: onContentChanged
        )
    }

    private func makeDetail(
        reviewState: String = "待复习",
        mistakeID: String? = "mistake-latest"
    ) -> UserExampleDetail {
        UserExampleDetail(
            id: "example-1",
            stem: "目标错题",
            options: ["甲", "乙", "丙", "丁"],
            myAnswer: "B",
            isMistake: true,
            examType: "国考",
            year: 2026,
            imagePath: nil,
            createdAt: "2026-11-25T00:00:00Z",
            idioms: [LinkedIdiom(id: "idiom-1", word: "按部就班")],
            mistakeState: reviewState,
            mistakeID: mistakeID,
            reason: "望文生义",
            tagNames: []
        )
    }
}
