import Foundation
import ZIPFoundation

/// Deliberately excludes ZIP64, encryption, spanning and executable/symlink entries.
/// Limits apply both to untrusted metadata and to every actual decompressed chunk.
enum PackArchive {
    static let maximumInput = 4 * 1024 * 1024
    static let maximumFile = 4 * 1024 * 1024
    static let maximumExpanded = 8 * 1024 * 1024
    static let names: Set<String> = ["idioms.json", "examples.json", "manifest.json"]

    static func read(_ data: Data) throws -> [String: Data] {
        guard data.count <= maximumInput else { throw PackError.limit("ZIP input") }
        let metadata = try PackZIPDirectory(data).inspect()
        let archive = try Archive(data: data, accessMode: .read)
        let entries = Array(archive)
        guard entries.count == 3 else { throw PackError.archive("Incomplete directory enumeration") }
        var output: [String: Data] = [:]
        var total = 0
        for entry in entries {
            guard entry.type == .file, let expected = metadata[entry.path], output[entry.path] == nil else {
                throw PackError.archive("Unexpected or duplicate entry")
            }
            var file = Data()
            let checksum = try archive.extract(entry, bufferSize: 16 * 1024, skipCRC32: false) { chunk in
                guard chunk.count <= maximumFile - file.count,
                      chunk.count <= maximumExpanded - total else { throw PackError.limit("ZIP expansion") }
                file.append(chunk)
                total += chunk.count
            }
            guard file.count == expected.size, checksum == expected.crc else {
                throw PackError.archive("CRC or expanded size mismatch")
            }
            output[entry.path] = file
        }
        guard Set(output.keys) == names else { throw PackError.archive("Missing root JSON") }
        return output
    }
}

struct PackZIPEntry {
    let name: String
    let crc: UInt32
    let size: Int
    let compressedSize: Int
    let localOffset: Int
    let flags: Int
    let method: Int
    let version: Int
    let time: Int
    let date: Int
}

/// ZIPFoundation's iterator can terminate early and trusts local-header fields for extraction.
/// Independently check the complete directory and its bijection with contiguous local records.
private struct PackZIPDirectory {
    let bytes: [UInt8]

    init(_ data: Data) { bytes = Array(data) }

    func inspect() throws -> [String: PackZIPEntry] {
        let end = try endRecord()
        guard try uint(end + 4, 2) == 0, try uint(end + 6, 2) == 0,
              try uint(end + 8, 2) == 3, try uint(end + 10, 2) == 3 else {
            throw PackError.archive("Expected three entries on one disk")
        }
        let directorySize = try uint(end + 12, 4)
        let directoryStart = try uint(end + 16, 4)
        guard directoryStart <= end, directorySize == end - directoryStart else {
            throw PackError.archive("Missing or inconsistent central directory")
        }
        var cursor = directoryStart
        var records: [String: PackZIPEntry] = [:]
        var expanded = 0
        for _ in 0..<3 {
            let record = try centralRecord(at: cursor)
            guard records[record.name] == nil else { throw PackError.archive("Duplicate ZIP path") }
            records[record.name] = record
            expanded += record.size
            guard expanded <= PackArchive.maximumExpanded else { throw PackError.limit("ZIP total metadata") }
            cursor += 46 + (try uint(cursor + 28, 2)) + (try uint(cursor + 30, 2)) + (try uint(cursor + 32, 2))
            guard cursor <= end else { throw PackError.archive("Truncated directory") }
        }
        guard cursor == end, Set(records.keys) == PackArchive.names else {
            throw PackError.archive("Extra or missing directory record")
        }
        cursor = 0
        for record in records.values.sorted(by: { $0.localOffset < $1.localOffset }) {
            guard record.localOffset == cursor else { throw PackError.archive("Unindexed/overlapping local records") }
            cursor = try localEnd(record, directoryStart: directoryStart)
        }
        guard cursor == directoryStart else { throw PackError.archive("Unindexed local data") }
        return records
    }

    private func endRecord() throws -> Int {
        guard bytes.count >= 22 else { throw PackError.archive("Missing EOCD") }
        let minimum = max(0, bytes.count - 22 - 65_535)
        for position in stride(from: bytes.count - 22, through: minimum, by: -1) {
            if try uint(position, 4) == 0x06054B50,
               try uint(position + 20, 2) == bytes.count - position - 22 { return position }
        }
        throw PackError.archive("Missing/truncated EOCD")
    }

    private func centralRecord(at position: Int) throws -> PackZIPEntry {
        guard try uint(position, 4) == 0x02014B50 else { throw PackError.archive("Invalid central header") }
        let version = try uint(position + 6, 2)
        let flags = try uint(position + 8, 2)
        let method = try uint(position + 10, 2)
        guard version <= 20, flags & ~0x080E == 0, method == 0 || method == 8,
              method != 0 || flags & 6 == 0, try uint(position + 34, 2) == 0 else {
            throw PackError.archive("Unsupported ZIP features")
        }
        let size = try uint(position + 24, 4)
        let compressed = try uint(position + 20, 4)
        guard size > 0, size <= PackArchive.maximumFile, compressed <= PackArchive.maximumInput else {
            throw PackError.limit("ZIP file metadata")
        }
        guard method != 0 || size == compressed else { throw PackError.archive("Stored size mismatch") }
        let nameLength = try uint(position + 28, 2)
        let extraLength = try uint(position + 30, 2)
        let name = try rootName(position + 46, length: nameLength)
        try extraFields(position + 46 + nameLength, length: extraLength)
        let attributes = try uint(position + 38, 4)
        let mode = (attributes >> 16) & 0xF000
        guard attributes & 0x10 == 0, mode == 0 || mode == 0x8000 else {
            throw PackError.archive("Directory or nonregular entry")
        }
        return PackZIPEntry(
            name: name, crc: UInt32(try uint(position + 16, 4)), size: size,
            compressedSize: compressed, localOffset: try uint(position + 42, 4), flags: flags,
            method: method, version: version, time: try uint(position + 12, 2), date: try uint(position + 14, 2)
        )
    }

    private func localEnd(_ record: PackZIPEntry, directoryStart: Int) throws -> Int {
        let position = record.localOffset
        guard try uint(position, 4) == 0x04034B50,
              try uint(position + 4, 2) == record.version,
              try uint(position + 6, 2) == record.flags,
              try uint(position + 8, 2) == record.method,
              try uint(position + 10, 2) == record.time,
              try uint(position + 12, 2) == record.date else {
            throw PackError.archive("Local/central metadata mismatch")
        }
        let nameLength = try uint(position + 26, 2)
        let extraLength = try uint(position + 28, 2)
        guard try rootName(position + 30, length: nameLength) == record.name else {
            throw PackError.archive("Local/central name mismatch")
        }
        try extraFields(position + 30 + nameLength, length: extraLength)
        let hasDescriptor = record.flags & 8 != 0
        for (field, expected) in [(14, Int(record.crc)), (18, record.compressedSize), (22, record.size)] {
            let actual = try uint(position + field, 4)
            guard actual == expected || (hasDescriptor && actual == 0) else {
                throw PackError.archive("Local/central size or CRC mismatch")
            }
        }
        var end = position + 30 + nameLength + extraLength + record.compressedSize
        guard end <= directoryStart else { throw PackError.archive("Truncated/overlapping compressed data") }
        if hasDescriptor {
            if try uint(end, 4) == 0x08074B50 { end += 4 }
            guard try uint(end, 4) == Int(record.crc), try uint(end + 4, 4) == record.compressedSize,
                  try uint(end + 8, 4) == record.size else { throw PackError.archive("Invalid data descriptor") }
            end += 12
        }
        guard end <= directoryStart else { throw PackError.archive("Truncated local record") }
        return end
    }

    private func rootName(_ position: Int, length: Int) throws -> String {
        try bounds(position, length)
        guard let name = String(bytes: bytes[position..<(position + length)], encoding: .utf8),
              PackArchive.names.contains(name) else { throw PackError.archive("Only three root JSON paths allowed") }
        return name
    }

    private func extraFields(_ position: Int, length: Int) throws {
        try bounds(position, length)
        var cursor = position
        while cursor < position + length {
            guard cursor + 4 <= position + length else { throw PackError.archive("Malformed ZIP extra field") }
            let tag = try uint(cursor, 2)
            let size = try uint(cursor + 2, 2)
            guard tag != 1, cursor + 4 + size <= position + length else {
                throw PackError.archive("ZIP64 or malformed extra field")
            }
            cursor += 4 + size
        }
    }

    private func uint(_ position: Int, _ length: Int) throws -> Int {
        try bounds(position, length)
        var result = 0
        for index in 0..<length { result |= Int(bytes[position + index]) << (index * 8) }
        return result
    }

    private func bounds(_ position: Int, _ length: Int) throws {
        guard position >= 0, length >= 0, position <= bytes.count, length <= bytes.count - position else {
            throw PackError.archive("Truncated ZIP record")
        }
    }
}
