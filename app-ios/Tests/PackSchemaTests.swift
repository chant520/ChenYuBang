@testable import CangyuData
import Foundation
import XCTest

final class PackSchemaTests: PackDatabaseTestCase {
    func testBadDigestAndUnsupportedSchemaNeverWriteDestination() throws {
        let pack = try importer.validate(PackFixture.published())
        _ = try importer.importPack(pack)
        let before = try snapshot(["idiom", "example", "idiom_fts", "settings"])
        var fixture = try PackFixture()
        fixture.manifest["sha256"] = String(repeating: "0", count: 64)
        XCTAssertThrowsError(try importer.validate(fixture.zip(rehash: false))) {
            XCTAssertEqual($0 as? PackError, .digestMismatch)
        }
        fixture.manifest["schemaVersion"] = 2
        try rejectSchema(fixture)
        XCTAssertEqual(try snapshot(["idiom", "example", "idiom_fts", "settings"]), before)
    }

    func testUUIDWordAndReferenceInvariantsAfterRehash() throws {
        let base = try PackFixture()
        var fixture = base
        fixture.idioms[1]["id"] = fixture.idioms[0]["id"]
        try rejectSchema(fixture)
        fixture = base
        fixture.examples[1]["id"] = fixture.examples[0]["id"]
        try rejectSchema(fixture)
        fixture = base
        fixture.examples[0]["id"] = fixture.idioms[0]["id"]
        try rejectSchema(fixture)
        fixture = base
        fixture.idioms[1]["word"] = fixture.idioms[0]["word"]
        try rejectSchema(fixture)
        fixture = base
        var uuid = Array(try XCTUnwrap(fixture.idioms[0]["id"] as? String))
        uuid[14] = "1"
        fixture.idioms[0]["id"] = String(uuid)
        try rejectSchema(fixture)
        fixture = base
        fixture.idioms[0]["id"] = try XCTUnwrap(fixture.idioms[0]["id"] as? String) + "\n"
        try rejectSchema(fixture)
        fixture = base
        fixture.examples[0]["idiomIds"] = [try XCTUnwrap(fixture.examples[0]["id"] as? String)]
        try rejectSchema(fixture)
        fixture = base
        let reference = try XCTUnwrap(fixture.examples[0]["idiomIds"] as? [String])[0]
        fixture.examples[0]["idiomIds"] = [reference, reference]
        try rejectSchema(fixture)
    }

    func testEveryRequiredFieldAndClosedNestedObject() throws {
        let base = try PackFixture()
        for key in ["schemaVersion", "packVersion", "builtAt", "counts", "sha256"] {
            var fixture = base
            fixture.manifest.removeValue(forKey: key)
            // Do not let the builder restore deliberately removed manifest fields.
            let files = try fixture.files()
            var manifest = try XCTUnwrap(JSONSerialization.jsonObject(with: files[2].1) as? [String: Any])
            manifest.removeValue(forKey: key)
            var changed = files
            changed[2].1 = try JSONSerialization.data(withJSONObject: manifest)
            rejectSchemaZIP(PackZIPFixture.make(changed))
        }
        for key in ["id", "word", "pinyin", "initials", "sense", "color", "frequency", "scopes"] {
            var fixture = base
            fixture.idioms[0].removeValue(forKey: key)
            try rejectSchema(fixture)
        }
        for key in ["id", "stem", "options", "answer", "explain", "examType", "year", "questionType", "idiomIds"] {
            var fixture = base
            fixture.examples[0].removeValue(forKey: key)
            try rejectSchema(fixture)
        }
    }

    func testNestedRequiredFieldsAndUnknownProperties() throws {
        let base = try PackFixture()
        for key in ["A", "B", "C", "D", "unknown"] {
            var fixture = base
            var options = try XCTUnwrap(fixture.examples[0]["options"] as? [String: Any])
            if key == "unknown" { options[key] = options["A"] } else { options.removeValue(forKey: key) }
            fixture.examples[0]["options"] = options
            try rejectSchema(fixture)
        }
        for key in ["idioms", "examples", "unknown"] {
            var fixture = base
            var counts = ["idioms": 59, "examples": 11]
            if key == "unknown" { counts[key] = 1 } else { counts.removeValue(forKey: key) }
            fixture.manifest["counts"] = counts
            try rejectSchema(fixture, recount: false)
        }
        for target in 0..<3 {
            var fixture = base
            if target == 0 { fixture.manifest["unknown"] = true }
            if target == 1 { fixture.idioms[0]["unknown"] = true }
            if target == 2 { fixture.examples[0]["unknown"] = true }
            try rejectSchema(fixture)
        }
        // The real first release has empty pitfalls arrays. Mutate structure using its actual sense text.
        for key in ["kind", "note", "unknown"] {
            var fixture = base
            var pitfall: [String: Any] = ["kind": "望文生义", "note": try XCTUnwrap(base.idioms[0]["sense"])]
            if key == "unknown" { pitfall[key] = true } else { pitfall.removeValue(forKey: key) }
            fixture.idioms[0]["pitfalls"] = [pitfall]
            try rejectSchema(fixture)
        }
    }

    func testTypesEnumsPatternsBoundsAndCounts() throws {
        let base = try PackFixture()
        let idiomChanges: [(String, Any)] = [
            ("id", 1), ("word", "abc"), ("pinyin", "UPPER"), ("initials", "a"), ("sense", "短"),
            ("origin", NSNull()), ("color", "未知"), ("frequency", true), ("frequency", 0), ("frequency", 6),
            ("scopes", []), ("scopes", ["未知"]), ("synonyms", [1]), ("antonyms", ["x"]), ("pitfalls", true)
        ]
        for (key, value) in idiomChanges {
            var fixture = base
            fixture.idioms[0][key] = value
            try rejectSchema(fixture)
        }
        let exampleChanges: [(String, Any)] = [
            ("stem", "短"), ("explain", false), ("options", []), ("answer", "E"), ("examType", "未知"),
            ("year", true), ("year", 1999), ("year", 2101), ("region", ""), ("questionType", "未知"),
            ("idiomIds", []), ("idiomIds", [false])
        ]
        for (key, value) in exampleChanges {
            var fixture = base
            fixture.examples[0][key] = value
            try rejectSchema(fixture)
        }
        for (key, value) in [("packVersion", "1.0.0"), ("builtAt", "2026-11-18"), ("sha256", "xyz")] {
            var fixture = base
            fixture.manifest[key] = value
            try rejectSchema(fixture, rehash: key != "sha256")
        }
        var fixture = base
        fixture.manifest["counts"] = ["idioms": 58, "examples": 11]
        try rejectSchema(fixture, recount: false)
        fixture = base
        fixture.manifest["schemaVersion"] = true
        try rejectSchema(fixture)
        fixture = base
        fixture.examples[0].removeValue(forKey: "region")
        try rejectSchema(fixture)
        fixture = base
        fixture.examples[0]["region"] = "  "
        try rejectSchema(fixture)
    }

    private func rejectSchema(_ fixture: PackFixture, rehash: Bool = true, recount: Bool = true) throws {
        rejectSchemaZIP(try fixture.zip(rehash: rehash, recount: recount))
    }

    private func rejectSchemaZIP(_ data: Data) {
        XCTAssertThrowsError(try importer.validate(data)) { error in
            guard let packError = error as? PackError, case .schema = packError else {
                return XCTFail("Expected schema, received \(error)")
            }
        }
    }
}
