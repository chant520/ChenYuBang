import XCTest
import SnapshotTesting
import SwiftUI
import CangyuData
import CangyuTokens
@testable import ChenYuBang

/// P21 保存成功快照：单归属 / 多归属 × 浅深，基线目录 __Snapshots__/P21DoneSnapshotTests/。
@MainActor
final class P21DoneSnapshotTests: XCTestCase {
    override func setUp() {
        super.setUp()
        isRecording = ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1"
    }

    private func assertP21(
        idioms: [SavedIdiom],
        dark: Bool,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let view = P21DoneScreen(
            idioms: idioms,
            onOpenIdiom: { _ in },
            onOpenMistakes: {},
            onContinue: {}
        )
        .environment(\.colorScheme, dark ? .dark : .light)
        assertSnapshot(
            of: view,
            as: .image(layout: .fixed(width: 390, height: 844)),
            file: file,
            testName: testName,
            line: line
        )
    }

    private var single: [SavedIdiom] {
        [SavedIdiom(id: "idiom-anbu", word: "按部就班")]
    }

    private var multiple: [SavedIdiom] {
        [
            SavedIdiom(id: "idiom-anbu", word: "按部就班"),
            SavedIdiom(id: "idiom-moshou", word: "墨守成规")
        ]
    }

    func testSingleLight() {
        assertP21(idioms: single, dark: false)
    }

    func testSingleDark() {
        assertP21(idioms: single, dark: true)
    }

    func testMultipleLight() {
        assertP21(idioms: multiple, dark: false)
    }

    func testMultipleDark() {
        assertP21(idioms: multiple, dark: true)
    }
}
