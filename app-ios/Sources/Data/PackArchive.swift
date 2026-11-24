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
        let metadata: [String: ZIPEntryMetadata]
        do {
            metadata = try ZIPDirectory(data: data, names: names, maximumInput: maximumInput,
                                        maximumFile: maximumFile, maximumExpanded: maximumExpanded).inspect()
        } catch let error as ZIPDirectoryError {
            switch error {
            case .invalid(let message): throw PackError.archive(message)
            case .limit(let message): throw PackError.limit(message)
            }
        }
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
