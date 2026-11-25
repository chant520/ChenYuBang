import CangyuData
import CangyuTokens
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ChenYuBang

/// P05 顶部 A6 自评 + A7 收藏两态快照：三态 × 收藏两态，浅深各一帧。
/// 只钉本包新增的顶部状态区（P05StateHeader），不改既有详情长页基线。
@MainActor
final class P05StateSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertHeader(_ state: IdiomLearningState, dark: Bool,
                              file: StaticString = #filePath, testName: String = #function,
                              line: UInt = #line) {
        let view = P05StateHeader(state: state, onSelfAssess: {}, onToggleFavorite: {})
            .environment(\.colorScheme, dark ? .dark : .light)
            .padding(Tokens.Space.block)
            .background((dark ? Tokens.dark : Tokens.light).page)
        assertSnapshot(of: view, as: .image(layout: .fixed(width: 390, height: 90)),
                       file: file, testName: testName, line: line)
    }

    func testNotStartedUnfavoritedLight() {
        assertHeader(IdiomLearningState(idiomID: "i1", status: .notStarted, favorite: false), dark: false)
    }

    func testStudiedFavoritedLight() {
        assertHeader(IdiomLearningState(idiomID: "i1", status: .studied, favorite: true), dark: false)
    }

    func testProficientFavoritedDark() {
        assertHeader(IdiomLearningState(idiomID: "i1", status: .proficient, favorite: true), dark: true)
    }
}
