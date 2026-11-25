import CangyuData
import XCTest
@testable import ChenYuBang

/// P13 E2 ViewModel：三计数取 idiom_state，错题/待复习取 overview；全空 0，失败全 0。
@MainActor
final class P13OverviewTests: XCTestCase {
    func testRefreshAggregatesFiveCounts() {
        let reader = StubOverviewReader(
            learned: 4, proficient: 2, favorite: 3,
            overview: MistakeOverview(total: 7, pending: 5)
        )
        let model = P13OverviewModel(reader: reader)
        model.refresh()
        XCTAssertEqual(model.stats, P13OverviewStats(
            learned: 4, favorites: 3, mistakes: 7, pending: 5, proficient: 2
        ))
    }

    func testAllEmptyShowsZero() {
        let reader = StubOverviewReader(
            learned: 0, proficient: 0, favorite: 0,
            overview: MistakeOverview(total: 0, pending: 0)
        )
        let model = P13OverviewModel(reader: reader)
        model.refresh()
        XCTAssertEqual(model.stats, P13OverviewStats())
    }

    func testReadFailureKeepsAllZero() {
        let reader = StubOverviewReader(learned: 9, proficient: 9, favorite: 9,
                                        overview: MistakeOverview(total: 9, pending: 9),
                                        fails: true)
        let model = P13OverviewModel(reader: reader)
        model.refresh()
        XCTAssertEqual(model.stats, P13OverviewStats())
    }

    private struct StubOverviewReader: OverviewReading {
        let learned: Int
        let proficient: Int
        let favorite: Int
        let overview: MistakeOverview
        var fails = false

        func countLearned() throws -> Int { try guardFail(learned) }
        func countProficient() throws -> Int { try guardFail(proficient) }
        func countFavorite() throws -> Int { try guardFail(favorite) }
        func mistakeOverview() throws -> MistakeOverview { try guardFail(overview) }

        private func guardFail<T>(_ value: T) throws -> T {
            if fails { throw TestError.boom }
            return value
        }
    }

    private enum TestError: Error { case boom }
}
