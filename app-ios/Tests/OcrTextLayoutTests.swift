import CoreGraphics
import XCTest
@testable import ChenYuBang

final class OcrTextLayoutTests: XCTestCase {
    func testRowsMergeSegmentsOnSameLineAndOrderTopDown() {
        let lines = [
            OcrLine(text: "A．刻舟求剑", confidence: 0.9, box: CGRect(x: 0.05, y: 0.45, width: 0.4, height: 0.08)),
            OcrLine(text: "使用正确的是", confidence: 0.9, box: CGRect(x: 0.58, y: 0.8, width: 0.34, height: 0.08)),
            OcrLine(text: "下列成语", confidence: 0.9, box: CGRect(x: 0.05, y: 0.8, width: 0.5, height: 0.08))
        ]
        XCTAssertEqual(OcrTextLayout.rows(from: lines), ["下列成语使用正确的是", "A．刻舟求剑"])
    }

    func testRowsKeepSeparateHeightLinesSeparate() {
        let lines = [
            OcrLine(text: "第一行", confidence: 0.9, box: CGRect(x: 0.05, y: 0.8, width: 0.3, height: 0.08)),
            OcrLine(text: "第二行", confidence: 0.9, box: CGRect(x: 0.05, y: 0.6, width: 0.3, height: 0.08))
        ]
        XCTAssertEqual(OcrTextLayout.rows(from: lines), ["第一行", "第二行"])
    }

    func testClassifySplitsStemAndOptionsWithWrappedLines() {
        let result = OcrTextLayout.classify([
            "下列成语使用正确的是",
            "A．一成不变",
            "B．刻舟求剑",
            "（折行）"
        ])
        XCTAssertEqual(result.stem, "下列成语使用正确的是")
        XCTAssertEqual(result.options, ["A": "一成不变", "B": "刻舟求剑（折行）"])
    }

    func testClassifyRecognizesFullWidthAndLowercaseMarkers() {
        let result = OcrTextLayout.classify(["题干", "ａ、第一", "D: 第四"])
        XCTAssertEqual(result.stem, "题干")
        XCTAssertEqual(result.options, ["A": "第一", "D": "第四"])
    }

    func testClassifyKeepsLinesWithoutMarkerSeparatorInStem() {
        let result = OcrTextLayout.classify(["Apple pie", "A", "  "])
        XCTAssertEqual(result.stem, "Apple pie\nA")
        XCTAssertTrue(result.options.isEmpty)
    }
}
