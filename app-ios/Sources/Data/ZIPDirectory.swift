import Foundation

struct ZIPEntryMetadata {
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

enum ZIPDirectoryError: Error {
    case invalid(String)
    case limit(String)
}

/// Independently checks the complete directory and its bijection with contiguous local records.
struct ZIPDirectory {
    let bytes: [UInt8]
    let names: Set<String>
    let maximumInput: Int
    let maximumFile: Int
    let maximumExpanded: Int

    init(data: Data, names: Set<String>, maximumInput: Int, maximumFile: Int, maximumExpanded: Int) {
        bytes = Array(data)
        self.names = names
        self.maximumInput = maximumInput
        self.maximumFile = maximumFile
        self.maximumExpanded = maximumExpanded
    }

    func inspect() throws -> [String: ZIPEntryMetadata] {
        let end = try endRecord()
        guard try uint(end + 4, 2) == 0, try uint(end + 6, 2) == 0,
              try uint(end + 8, 2) == names.count, try uint(end + 10, 2) == names.count else {
            throw ZIPDirectoryError.invalid("Unexpected entry count or multi-disk archive")
        }
        let directorySize = try uint(end + 12, 4)
        let directoryStart = try uint(end + 16, 4)
        guard directoryStart <= end, directorySize == end - directoryStart else {
            throw ZIPDirectoryError.invalid("Missing or inconsistent central directory")
        }
        var cursor = directoryStart
        var records: [String: ZIPEntryMetadata] = [:]
        var expanded = 0
        for _ in 0..<names.count {
            let record = try centralRecord(at: cursor)
            guard records[record.name] == nil else { throw ZIPDirectoryError.invalid("Duplicate ZIP path") }
            records[record.name] = record
            expanded += record.size
            guard expanded <= maximumExpanded else { throw ZIPDirectoryError.limit("ZIP total metadata") }
            cursor += 46 + (try uint(cursor + 28, 2)) + (try uint(cursor + 30, 2)) + (try uint(cursor + 32, 2))
            guard cursor <= end else { throw ZIPDirectoryError.invalid("Truncated directory") }
        }
        guard cursor == end, Set(records.keys) == names else {
            throw ZIPDirectoryError.invalid("Extra or missing directory record")
        }
        cursor = 0
        for record in records.values.sorted(by: { $0.localOffset < $1.localOffset }) {
            guard record.localOffset == cursor else {
                throw ZIPDirectoryError.invalid("Unindexed/overlapping local records")
            }
            cursor = try localEnd(record, directoryStart: directoryStart)
        }
        guard cursor == directoryStart else { throw ZIPDirectoryError.invalid("Unindexed local data") }
        return records
    }

    private func endRecord() throws -> Int {
        guard bytes.count >= 22 else { throw ZIPDirectoryError.invalid("Missing EOCD") }
        let minimum = max(0, bytes.count - 22 - 65_535)
        for position in stride(from: bytes.count - 22, through: minimum, by: -1) {
            if try uint(position, 4) == 0x06054B50,
               try uint(position + 20, 2) == bytes.count - position - 22 { return position }
        }
        throw ZIPDirectoryError.invalid("Missing/truncated EOCD")
    }

    private func centralRecord(at position: Int) throws -> ZIPEntryMetadata {
        guard try uint(position, 4) == 0x02014B50 else { throw ZIPDirectoryError.invalid("Invalid central header") }
        let version = try uint(position + 6, 2)
        let flags = try uint(position + 8, 2)
        let method = try uint(position + 10, 2)
        guard version <= 20, flags & ~0x080E == 0, method == 0 || method == 8,
              method != 0 || flags & 6 == 0, try uint(position + 34, 2) == 0 else {
            throw ZIPDirectoryError.invalid("Unsupported ZIP features")
        }
        let size = try uint(position + 24, 4)
        let compressed = try uint(position + 20, 4)
        guard size > 0, size <= maximumFile, compressed <= maximumInput else {
            throw ZIPDirectoryError.limit("ZIP file metadata")
        }
        guard method != 0 || size == compressed else { throw ZIPDirectoryError.invalid("Stored size mismatch") }
        let nameLength = try uint(position + 28, 2)
        let extraLength = try uint(position + 30, 2)
        let name = try rootName(position + 46, length: nameLength)
        try extraFields(position + 46 + nameLength, length: extraLength)
        let attributes = try uint(position + 38, 4)
        let mode = (attributes >> 16) & 0xF000
        guard attributes & 0x10 == 0, mode == 0 || mode == 0x8000 else {
            throw ZIPDirectoryError.invalid("Directory or nonregular entry")
        }
        return ZIPEntryMetadata(
            name: name, crc: UInt32(try uint(position + 16, 4)), size: size,
            compressedSize: compressed, localOffset: try uint(position + 42, 4), flags: flags,
            method: method, version: version, time: try uint(position + 12, 2), date: try uint(position + 14, 2)
        )
    }

    private func localEnd(_ record: ZIPEntryMetadata, directoryStart: Int) throws -> Int {
        let position = record.localOffset
        guard try uint(position, 4) == 0x04034B50,
              try uint(position + 4, 2) == record.version,
              try uint(position + 6, 2) == record.flags,
              try uint(position + 8, 2) == record.method,
              try uint(position + 10, 2) == record.time,
              try uint(position + 12, 2) == record.date else {
            throw ZIPDirectoryError.invalid("Local/central metadata mismatch")
        }
        let nameLength = try uint(position + 26, 2)
        let extraLength = try uint(position + 28, 2)
        guard try rootName(position + 30, length: nameLength) == record.name else {
            throw ZIPDirectoryError.invalid("Local/central name mismatch")
        }
        try extraFields(position + 30 + nameLength, length: extraLength)
        let hasDescriptor = record.flags & 8 != 0
        for (field, expected) in [(14, Int(record.crc)), (18, record.compressedSize), (22, record.size)] {
            let actual = try uint(position + field, 4)
            guard actual == expected || (hasDescriptor && actual == 0) else {
                throw ZIPDirectoryError.invalid("Local/central size or CRC mismatch")
            }
        }
        var end = position + 30 + nameLength + extraLength + record.compressedSize
        guard end <= directoryStart else { throw ZIPDirectoryError.invalid("Truncated/overlapping compressed data") }
        if hasDescriptor {
            if try uint(end, 4) == 0x08074B50 { end += 4 }
            guard try uint(end, 4) == Int(record.crc), try uint(end + 4, 4) == record.compressedSize,
                  try uint(end + 8, 4) == record.size else {
                throw ZIPDirectoryError.invalid("Invalid data descriptor")
            }
            end += 12
        }
        guard end <= directoryStart else { throw ZIPDirectoryError.invalid("Truncated local record") }
        return end
    }

    private func rootName(_ position: Int, length: Int) throws -> String {
        try bounds(position, length)
        guard let name = String(bytes: bytes[position..<(position + length)], encoding: .utf8),
              names.contains(name) else {
            throw ZIPDirectoryError.invalid("Unexpected ZIP path")
        }
        return name
    }

    private func extraFields(_ position: Int, length: Int) throws {
        try bounds(position, length)
        var cursor = position
        while cursor < position + length {
            guard cursor + 4 <= position + length else { throw ZIPDirectoryError.invalid("Malformed ZIP extra field") }
            let tag = try uint(cursor, 2)
            let size = try uint(cursor + 2, 2)
            guard tag != 1, cursor + 4 + size <= position + length else {
                throw ZIPDirectoryError.invalid("ZIP64 or malformed extra field")
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
            throw ZIPDirectoryError.invalid("Truncated ZIP record")
        }
    }
}
