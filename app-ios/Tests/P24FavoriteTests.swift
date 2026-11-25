import CangyuData
import XCTest
@testable import ChenYuBang

/// P24 ViewModel：只读取 favoriteIdioms，失败保留空态，缺状态串投影未学。
@MainActor
final class P24FavoriteTests: XCTestCase {
    private let idiomA = "11111111-1111-4111-8111-111111111111"
    private let idiomB = "22222222-2222-4222-8222-222222222222"

    func testLoadProjectsFavoritesAndThreeStates() {
        let rows = [
            FavoriteIdiom(idiomId: idiomA, word: "按部就班", pinyin: "an bu jiu ban",
                          initials: "abjb", frequency: 5, status: "已掌握"),
            FavoriteIdiom(idiomId: idiomB, word: "别出心裁", pinyin: "bie chu xin cai",
                          initials: "bcxc", frequency: 3, status: "  ")
        ]
        let model = P24FavoriteModel(reader: StubFavoriteReader(result: .success(rows)))
        model.load()
        XCTAssertFalse(model.state.loading)
        XCTAssertFalse(model.state.failed)
        XCTAssertEqual(model.state.items.count, 2)
        XCTAssertEqual(model.state.items.first?.status, .proficient)
        XCTAssertEqual(model.state.items.last?.status, .notStarted)
        XCTAssertFalse(model.state.isEmpty)
    }

    func testEmptyResultShowsEmptyState() {
        let model = P24FavoriteModel(reader: StubFavoriteReader(result: .success([])))
        model.load()
        XCTAssertTrue(model.state.isEmpty)
    }

    func testReadFailureKeepsFailedState() {
        let model = P24FavoriteModel(reader: StubFavoriteReader(result: .failure(TestError.boom)))
        model.load()
        XCTAssertTrue(model.state.failed)
        XCTAssertTrue(model.state.items.isEmpty)
        XCTAssertFalse(model.state.isEmpty)
    }

    private enum TestError: Error { case boom }

    private struct StubFavoriteReader: FavoriteReading {
        let result: Result<[FavoriteIdiom], Error>
        func favoriteIdioms() throws -> [FavoriteIdiom] { try result.get() }
    }
}
