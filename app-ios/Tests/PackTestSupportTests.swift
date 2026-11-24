@testable import CangyuData
import Foundation
import GRDB
import XCTest

/// All package mutations originate in the copied, published 59/11 fixture.
struct PackFixture {
    static let digest = "f0e98e97a47ea92945f042fa5f0d8439ab17a4fddca10941b14089f9203c7fc0"
    var idioms: [[String: Any]]
    var examples: [[String: Any]]
    var manifest: [String: Any]

    static func published() throws -> Data {
        let bundle = Bundle(for: PackDatabaseTestCase.self)
        for directory in ["Fixtures/pack", "pack", ""] {
            if let url = bundle.url(forResource: "pack-v1.0.0", withExtension: "zip", subdirectory: directory) {
                return try Data(contentsOf: url)
            }
        }
        // Host-side SwiftPM tests can read the checked-in fixture without modifying Package.swift.
        let sourceURL = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
            .appendingPathComponent("Fixtures/pack/pack-v1.0.0.zip")
        return try Data(contentsOf: sourceURL)
    }

    static func mock() throws -> Data {
        let bundle = Bundle(for: PackDatabaseTestCase.self)
        for directory in ["Fixtures/pack", "pack", ""] {
            if let url = bundle.url(forResource: "pack-mock-v0.0.1", withExtension: "zip", subdirectory: directory) {
                return try Data(contentsOf: url)
            }
        }
        let sourceURL = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
            .appendingPathComponent("Fixtures/pack/pack-mock-v0.0.1.zip")
        return try Data(contentsOf: sourceURL)
    }

    init() throws {
        let files = try PackArchive.read(Self.published())
        idioms = try Self.decode(files, "idioms.json", as: [[String: Any]].self)
        examples = try Self.decode(files, "examples.json", as: [[String: Any]].self)
        manifest = try Self.decode(files, "manifest.json", as: [String: Any].self)
    }

    func files(rehash: Bool = true, recount: Bool = true) throws -> [(String, Data)] {
        let idiomData = try JSONSerialization.data(withJSONObject: idioms)
        let exampleData = try JSONSerialization.data(withJSONObject: examples)
        var updated = manifest
        if rehash {
            updated["sha256"] = try PackCanonical.digest(idioms: Self.json(idiomData), examples: Self.json(exampleData))
        }
        if recount { updated["counts"] = ["idioms": idioms.count, "examples": examples.count] }
        return [("idioms.json", idiomData), ("examples.json", exampleData),
                ("manifest.json", try JSONSerialization.data(withJSONObject: updated))]
    }

    func zip(rehash: Bool = true, recount: Bool = true) throws -> Data {
        try PackZIPFixture.make(files(rehash: rehash, recount: recount))
    }

    static func json(_ data: Data) throws -> PackJSON {
        var reader = try PackJSONReader(data)
        return try reader.read()
    }

    private static func decode<Value>(_ files: [String: Data], _ name: String, as type: Value.Type) throws -> Value {
        let data = try XCTUnwrap(files[name])
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? Value)
    }
}

/// Stored ZIP writer permits duplicate paths and surgical corruptions that normal ZIP writers reject.
/// Contents are never invented: callers supply original or mutated real fixture bytes.
enum PackZIPFixture {
    static func make(_ files: [(String, Data)]) -> Data {
        var local = Data()
        var central = Data()
        for (name, data) in files {
            let nameData = Data(name.utf8)
            let crc = crc32(data)
            let offset = local.count
            local.appendLE(0x04034B50, width: 4)
            local.appendLE(20, width: 2)
            local.append(Data(repeating: 0, count: 8))
            local.appendLE(UInt64(crc), width: 4)
            local.appendLE(UInt64(data.count), width: 4)
            local.appendLE(UInt64(data.count), width: 4)
            local.appendLE(UInt64(nameData.count), width: 2)
            local.appendLE(0, width: 2)
            local.append(nameData)
            local.append(data)
            central.appendLE(0x02014B50, width: 4)
            central.appendLE(20, width: 2)
            central.appendLE(20, width: 2)
            central.append(Data(repeating: 0, count: 8))
            central.appendLE(UInt64(crc), width: 4)
            central.appendLE(UInt64(data.count), width: 4)
            central.appendLE(UInt64(data.count), width: 4)
            central.appendLE(UInt64(nameData.count), width: 2)
            central.append(Data(repeating: 0, count: 12))
            central.appendLE(UInt64(offset), width: 4)
            central.append(nameData)
        }
        let start = local.count
        local.append(central)
        local.appendLE(0x06054B50, width: 4)
        local.append(Data(repeating: 0, count: 4))
        local.appendLE(UInt64(files.count), width: 2)
        local.appendLE(UInt64(files.count), width: 2)
        local.appendLE(UInt64(central.count), width: 4)
        local.appendLE(UInt64(start), width: 4)
        local.appendLE(0, width: 2)
        return local
    }

    static func crc32(_ data: Data) -> UInt32 {
        var crc = UInt32.max
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 { crc = (crc >> 1) ^ (crc & 1 == 0 ? 0 : 0xEDB88320) }
        }
        return ~crc
    }

    static func centralStart(_ data: Data) -> Int { Int(data.uintLE(at: data.count - 6, width: 4)) }
}

extension Data {
    mutating func appendLE(_ value: UInt64, width: Int) {
        for index in 0..<width { append(UInt8(truncatingIfNeeded: value >> (8 * index))) }
    }

    mutating func setLE(_ value: UInt64, at offset: Int, width: Int) {
        for index in 0..<width { self[offset + index] = UInt8(truncatingIfNeeded: value >> (8 * index)) }
    }

    func uintLE(at offset: Int, width: Int) -> UInt64 {
        (0..<width).reduce(0) { $0 | UInt64(self[offset + $1]) << (8 * $1) }
    }
}

class PackDatabaseTestCase: XCTestCase {
    var queue: DatabaseQueue!
    var importer: PackImporter!

    override func setUpWithError() throws {
        queue = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(queue)
        importer = PackImporter(queue: queue)
    }

    override func tearDownWithError() throws {
        importer = nil
        queue = nil
    }

    func snapshot(_ tables: [String]) throws -> [String: [Row]] {
        try queue.read { db in
            var output: [String: [Row]] = [:]
            for table in tables { output[table] = try Row.fetchAll(db, sql: "SELECT * FROM \(table) ORDER BY 1, 2") }
            return output
        }
    }

    func count(_ table: String) throws -> Int {
        try queue.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") ?? -1 }
    }

    func linkedIDs(scope: String) throws -> Set<String> {
        try queue.read { db in
            Set(try String.fetchAll(db, sql: "SELECT idiomId FROM idiom_scope WHERE scopeId = ?", arguments: [scope]))
        }
    }
}
