@testable import CangyuData
import Foundation
import XCTest

final class PackCanonicalTests: PackDatabaseTestCase {
    func testPythonUnicodeScalarOrderingNFCAndEscapesVector() throws {
        // Expected text independently produced by tools/canonical.py, not JSONSerialization.sortedKeys.
        let source = "{\"𐀀\":\"e\\u0301/\\n\",\"\u{E000}\":\"汉\\u0001\",\"float\":1.0,\"zero\":-0.0}"
        let expected = "{\"float\":1,\"zero\":0,\"\u{E000}\":\"汉\\u0001\",\"𐀀\":\"é/\\n\"}"
        XCTAssertEqual(PackCanonical.text(try PackFixture.json(Data(source.utf8))), expected)
        let arrays = try PackFixture.json(Data("[3,1,2,1e2,-0,\"a/b\",true,null]".utf8))
        XCTAssertEqual(PackCanonical.text(arrays), "[3,1,2,100,0,\"a/b\",true,null]")
        let arbitraryInteger = "1234567890123456789012345678901234567890"
        XCTAssertEqual(PackCanonical.text(try PackFixture.json(Data(arbitraryInteger.utf8))), arbitraryInteger)
    }

    func testRealFixtureCanonicalDigestAndWholeFloatCompatibility() throws {
        let fixture = try PackFixture()
        var files = try fixture.files()
        let canonical = try PackCanonical.digest(
            idioms: PackFixture.json(files[0].1), examples: PackFixture.json(files[1].1)
        )
        XCTAssertEqual(canonical, PackFixture.digest)
        let raw = try XCTUnwrap(String(data: files[0].1, encoding: .utf8))
        files[0].1 = Data(try replacingFrequency(raw, suffix: ".0").utf8)
        let pack = try importer.validate(PackZIPFixture.make(files))
        XCTAssertEqual(pack.sha256, PackFixture.digest)
        XCTAssertEqual(pack.counts.idioms, 59)
    }

    func testDuplicateEscapedKeyAndNFCCollisionRejected() throws {
        let fixture = try PackFixture()
        var files = try fixture.files()
        let raw = try XCTUnwrap(String(data: files[0].1, encoding: .utf8))
        let regex = try NSRegularExpression(pattern: "\"word\"\\s*:\\s*\"[^\"]+\"")
        let range = NSRange(raw.startIndex..<raw.endIndex, in: raw)
        let match = try XCTUnwrap(regex.firstMatch(in: raw, range: range))
        let swiftRange = try XCTUnwrap(Range(match.range, in: raw))
        let field = String(raw[swiftRange])
        // Duplicate identical value: canonical content/digest are otherwise unchanged.
        for duplicate in [field, field.replacingOccurrences(of: "\"word\"", with: "\"\\u0077ord\"")] {
            var changed = files
            changed[0].1 = Data(raw.replacingCharacters(in: swiftRange, with: field + "," + duplicate).utf8)
            rejectJSON(PackZIPFixture.make(changed))
        }
        var collision = fixture
        collision.idioms[0]["é"] = collision.idioms[0]["sense"]
        files = try collision.files() // Rehash normalized single key before injecting an equivalent duplicate.
        let collisionRaw = try XCTUnwrap(String(data: files[0].1, encoding: .utf8))
        let firstBrace = try XCTUnwrap(collisionRaw.firstIndex(of: "{"))
        files[0].1 = Data(collisionRaw.replacingCharacters(
            in: firstBrace...firstBrace, with: "{\"e\\u0301\":\"重复键变异\","
        ).utf8)
        rejectJSON(PackZIPFixture.make(files))
    }

    func testStrictUTF8JSONGrammarSurrogatesAndNonintegralNumbers() throws {
        let fixture = try PackFixture()
        let original = try fixture.files()
        let raw = try XCTUnwrap(String(data: original[0].1, encoding: .utf8))
        for suffix in [".5", ".", "e", "e+", "e9999"] {
            var files = original
            files[0].1 = Data(try replacingFrequency(raw, suffix: suffix).utf8)
            rejectJSON(PackZIPFixture.make(files))
        }
        var invalidUTF8 = original
        invalidUTF8[0].1[10] = 0xFF
        rejectJSON(PackZIPFixture.make(invalidUTF8))
        for addition in [" garbage", " NaN", " /*comment*/", "\u{0000}"] {
            var files = original
            files[0].1.append(Data(addition.utf8))
            rejectJSON(PackZIPFixture.make(files))
        }
        for malformed in ["\\uD800", "\\uDC00", "\\uD800\\u0041", "\\x00", "\n"] {
            var files = original
            files[0].1 = Data(raw.replacingOccurrences(of: "步步为营", with: malformed).utf8)
            rejectJSON(PackZIPFixture.make(files))
        }
        var bom = original
        bom[0].1 = Data([0xEF, 0xBB, 0xBF]) + bom[0].1
        rejectJSON(PackZIPFixture.make(bom))
    }

    private func replacingFrequency(_ raw: String, suffix: String) throws -> String {
        let regex = try NSRegularExpression(pattern: "\"frequency\"\\s*:\\s*[1-5]")
        let match = try XCTUnwrap(regex.firstMatch(in: raw, range: NSRange(raw.startIndex..<raw.endIndex, in: raw)))
        let range = try XCTUnwrap(Range(match.range, in: raw))
        return raw.replacingCharacters(in: range, with: String(raw[range]) + suffix)
    }

    private func rejectJSON(_ data: Data) {
        XCTAssertThrowsError(try importer.validate(data)) { error in
            guard let packError = error as? PackError, case .json = packError else {
                return XCTFail("Expected strict JSON failure, received \(error)")
            }
        }
    }
}
