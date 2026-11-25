import CangyuData
import CangyuTokens
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ChenYuBang

/// P24「我的收藏」快照（规范 §11.1）：列表三态 × 浅深 + 空态 × 浅深。
/// 复用 CListRow / CTag 既有构件，只钉住本包新增的组合，不改既有构件基线。
@MainActor
final class P24FavoriteSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertP24(_ state: P24FavoriteState, dark: Bool,
                           file: StaticString = #filePath, testName: String = #function,
                           line: UInt = #line) {
        let colors = dark ? Tokens.dark : Tokens.light
        let view = P24FavoriteContent(state: state, actions: actions(), colors: colors)
            .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(of: view, as: .image(layout: .fixed(width: 390, height: 844)),
                       file: file, testName: testName, line: line)
    }

    private func listState() -> P24FavoriteState {
        P24FavoriteState(
            items: [
                FavoriteIdiomView(idiomId: "i1", word: "按部就班", pinyin: "an bu jiu ban",
                                  frequency: 5, status: .notStarted),
                FavoriteIdiomView(idiomId: "i2", word: "墨守成规", pinyin: "mo shou cheng gui",
                                  frequency: 3, status: .studied),
                FavoriteIdiomView(idiomId: "i3", word: "别出心裁", pinyin: "bie chu xin cai",
                                  frequency: 2, status: .proficient)
            ],
            loading: false
        )
    }

    private func actions() -> P24FavoriteActions {
        P24FavoriteActions(onBack: {}, onOpenIdiom: { _ in }, onGoLibrary: {}, onRetry: {})
    }

    func testListLight() { assertP24(listState(), dark: false) }
    func testListDark() { assertP24(listState(), dark: true) }
    func testEmptyLight() { assertP24(P24FavoriteState(loading: false), dark: false) }
    func testEmptyDark() { assertP24(P24FavoriteState(loading: false), dark: true) }
}
