import XCTest
import CangyuTokens

final class TokensTests: XCTestCase {
    func testHitTargetMeetsMinimum() {
        XCTAssertGreaterThanOrEqual(Tokens.Space.hitMin, 44)
    }

    func testRadiusLadderOrdered() {
        XCTAssertGreaterThan(Tokens.Radius.r6, Tokens.Radius.r2)
    }

    func testLightAndDarkPalettesDiffer() {
        XCTAssertNotEqual(Tokens.light.page, Tokens.dark.page)
    }
}