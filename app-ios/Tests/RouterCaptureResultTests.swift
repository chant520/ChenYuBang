import CangyuData
import Foundation
import XCTest
@testable import ChenYuBang

/// P21 保存后的三处去向（信息架构 v1.4 P21）：去成语详情 / 去错题本 / 继续录下一题。
final class RouterCaptureResultTests: XCTestCase {
    func testP21IdiomDestinationOpensFirstSavedIdiomOnOriginTab() throws {
        let router = RouterState()
        router.selectTab(.mistake)
        XCTAssertTrue(router.beginCapture())
        try advanceToSaved(
            router,
            idioms: [
                SavedIdiom(id: "idiom-anbu", word: "按部就班"),
                SavedIdiom(id: "idiom-moshou", word: "墨守成规")
            ]
        )
        XCTAssertEqual(router.currentEntry.route, .done)
        XCTAssertFalse(router.finishCaptureToIdiom("idiom-other"))
        XCTAssertTrue(router.finishCaptureToIdiom("idiom-anbu"))
        XCTAssertNil(router.captureWorkflow)
        XCTAssertEqual(router.selectedTab, .mistake)
        XCTAssertEqual(router.currentEntry.route, .idiomDetail)
        XCTAssertEqual(router.currentEntry.recordID, "idiom-anbu")
        XCTAssertEqual(router.entries(for: .mistake).map(\.route), [.mistakes, .idiomDetail])
        XCTAssertTrue(router.fullScreenEntries.isEmpty)
        router.back()
        XCTAssertEqual(router.currentEntry.route, .mistakes)
    }

    func testP21ContinueKeepsWorkflowButDropsSavedResultAndArtifacts() throws {
        let router = RouterState()
        XCTAssertTrue(router.beginCapture())
        let workflowID = try XCTUnwrap(router.captureWorkflow?.id)
        try advanceToSaved(router, idioms: [SavedIdiom(id: "idiom-anbu", word: "按部就班")])
        XCTAssertTrue(router.continueCapture())
        XCTAssertEqual(router.fullScreenEntries.map(\.route), [.camera])
        XCTAssertEqual(router.captureWorkflow?.id, workflowID)
        XCTAssertNil(router.captureWorkflow?.imageURL)
        XCTAssertNil(router.captureWorkflow?.croppedURL)
        XCTAssertNil(router.captureWorkflow?.recognizedText)
        XCTAssertNil(router.captureWorkflow?.savedUserExampleID)
        XCTAssertNil(router.captureWorkflow?.savedMistakeID)
        XCTAssertEqual(router.captureWorkflow?.savedIdioms, [])
        XCTAssertFalse(router.continueCapture())

        // 上一题已存，下一题走同一 workflow 仍能正常推进。
        let image = URL(fileURLWithPath: "/test-fixture/next.jpg")
        XCTAssertTrue(router.captured(workflowID: workflowID, imageURL: image))
        XCTAssertEqual(router.currentEntry.route, .crop)
    }

    private func advanceToSaved(_ router: RouterState, idioms: [SavedIdiom]) throws {
        let workflowID = try XCTUnwrap(router.captureWorkflow?.id)
        let image = URL(fileURLWithPath: "/test-fixture/image.jpg")
        router.captured(workflowID: workflowID, imageURL: image)
        router.cropped(workflowID: workflowID, imageURL: image)
        router.recognized(workflowID: workflowID, entryID: router.currentEntry.id, text: "按部就班 测试题干")
        XCTAssertTrue(router.saved(
            workflowID: workflowID,
            userExampleID: "user-example-fixture",
            mistakeID: nil,
            idioms: idioms
        ))
    }
}
