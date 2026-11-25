import Foundation
import XCTest
@testable import ChenYuBang

@MainActor
final class PackUpdateTriggerTests: XCTestCase {
    func testTaskIDChangesOnActivationReselectionAndDetailReturn() throws {
        let router = RouterState()
        let launch = FirstLaunchState()
        router.selectTab(.example)
        let entry = try XCTUnwrap(router.entries(for: .example).first)
        let page = { isActive in
            RoutePlaceholderPage(router: router, launch: launch, entry: entry, tab: .example, isActive: isActive)
        }
        XCTAssertEqual(page(true).packUpdateTaskID, 1)

        XCTAssertTrue(router.push(.exampleDetail, recordID: "example-fixture"))
        XCTAssertNil(page(false).packUpdateTaskID)

        router.back()
        XCTAssertEqual(page(true).packUpdateTaskID, 1)
        router.selectTab(.example)
        XCTAssertEqual(page(true).packUpdateTaskID, 2)
    }
}
