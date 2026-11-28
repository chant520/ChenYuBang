import XCTest
@testable import ChenYuBang

/// 决策 2 双端口径：iOS 文案逐字跟齐 Android 主线，防止再次分叉。
@MainActor
final class P25SelfTestCopyTests: XCTestCase {
    func testExitTitleMatchesAndroidBaseline() {
        XCTAssertEqual(P25SelfTestCopy.exitTitle, "退出本轮自测？")
    }

    func testInsufficientCandidatesMatchesAndroidBaseline() {
        XCTAssertEqual(P25SelfTestCopy.insufficientTitle, "可自测的候选项不足")
        XCTAssertEqual(
            P25SelfTestCopy.insufficientDetail,
            "全库至少需要 4 条词与释义都唯一的成语，当前不足以组成四选一。"
        )
    }
}
