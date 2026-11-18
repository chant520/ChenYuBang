@testable import CangyuData
import Foundation
import XCTest

final class PackArchiveTests: PackDatabaseTestCase {
    func testExtraDuplicateMissingTraversalDirectoryAndSymlink() throws {
        let files = try PackFixture().files()
        rejectArchive(PackZIPFixture.make(files + [("extra.json", files[0].1)]))
        rejectArchive(PackZIPFixture.make([files[0], files[0], files[2]]))
        rejectArchive(PackZIPFixture.make(Array(files.prefix(2))))
        for name in ["../idioms.json", "/idioms.json", "folder/idioms.json", "folder\\idioms.json", "idioms.json/"] {
            var changed = files
            changed[0].0 = name
            rejectArchive(PackZIPFixture.make(changed))
        }
        var symlink = PackZIPFixture.make(files)
        let central = PackZIPFixture.centralStart(symlink)
        symlink.setLE(0xA1FF0000, at: central + 38, width: 4)
        rejectArchive(symlink)
        var directory = PackZIPFixture.make(files)
        directory.setLE(0x10, at: central + 38, width: 4)
        rejectArchive(directory)
    }

    func testTruncationMissingCentralDirectoryAndEOCD() throws {
        let data = try PackFixture.published()
        let central = PackZIPFixture.centralStart(data)
        for cut in [1, 10, 22, data.count - central, data.count - 30] {
            rejectArchive(Data(data.dropLast(cut)))
        }
        var missingCentral = data
        missingCentral[central] = 0
        rejectArchive(missingCentral)
        var badDirectorySize = data
        badDirectorySize.setLE(1, at: data.count - 10, width: 4)
        rejectArchive(badDirectorySize)
        var trailing = data
        trailing.append(0)
        rejectArchive(trailing)
    }

    func testCRCAndLocalCentralHeaderDisagreement() throws {
        let files = try PackFixture().files()
        let original = PackZIPFixture.make(files)
        let central = PackZIPFixture.centralStart(original)
        var crc = original
        let incorrect = original.uintLE(at: 14, width: 4) ^ 1
        crc.setLE(incorrect, at: 14, width: 4)
        crc.setLE(incorrect, at: central + 16, width: 4)
        rejectArchive(crc)
        for (position, width) in [(14, 4), (18, 4), (22, 4), (6, 2), (8, 2), (30, 1)] {
            var mismatch = original
            mismatch.setLE(original.uintLE(at: position, width: width) ^ 1, at: position, width: width)
            rejectArchive(mismatch)
        }
        var overlap = original
        let second = central + 46 + files[0].0.utf8.count
        overlap.setLE(0, at: second + 42, width: 4)
        rejectArchive(overlap)
        var encrypted = original
        encrypted.setLE(1, at: 6, width: 2)
        encrypted.setLE(1, at: central + 8, width: 2)
        rejectArchive(encrypted)
    }

    func testUnindexedLocalHeaderIsRejectedEvenWithThreeCentralEntries() throws {
        let files = try PackFixture().files()
        var data = PackZIPFixture.make(files + [files[0]])
        let central = PackZIPFixture.centralStart(data)
        let originalEnd = data.count - 22
        let fourth = central + files.reduce(0) { $0 + 46 + $1.0.utf8.count }
        data.removeSubrange(fourth..<originalEnd)
        let end = data.count - 22
        data.setLE(3, at: end + 8, width: 2)
        data.setLE(3, at: end + 10, width: 2)
        data.setLE(UInt64(fourth - central), at: end + 12, width: 4)
        rejectArchive(data)
    }

    func testInputSingleFileTotalMetadataAndActualExpansionLimits() throws {
        var oversizedInput = try PackFixture.published()
        oversizedInput.append(Data(repeating: 32, count: PackArchive.maximumInput + 1 - oversizedInput.count))
        rejectLimit(oversizedInput)
        let files = try PackFixture().files()
        var oversizedFile = PackZIPFixture.make(files)
        let central = PackZIPFixture.centralStart(oversizedFile)
        oversizedFile.setLE(UInt64(PackArchive.maximumFile + 1), at: central + 24, width: 4)
        rejectLimit(oversizedFile)
        var oversizedTotal = PackZIPFixture.make(files)
        var cursor = central
        for (name, _) in files {
            oversizedTotal.setLE(8, at: cursor + 10, width: 2)
            oversizedTotal.setLE(UInt64(3 * 1024 * 1024), at: cursor + 24, width: 4)
            cursor += 46 + name.utf8.count
        }
        rejectLimit(oversizedTotal)
        // Valid raw DEFLATE stream: real idioms JSON followed only by legal JSON whitespace.
        // Lie about expanded size to ensure the consumer enforces the actual byte limit too.
        let deflated = PackDeflateFixture.whitespaceExpansion(of: files[0].1, repetitions: 17_000)
        var bombFiles = files
        bombFiles[0].1 = deflated
        var bomb = PackZIPFixture.make(bombFiles)
        let bombCentral = PackZIPFixture.centralStart(bomb)
        bomb.setLE(8, at: 8, width: 2)
        bomb.setLE(8, at: bombCentral + 10, width: 2)
        bomb.setLE(UInt64(files[0].1.count), at: 22, width: 4)
        bomb.setLE(UInt64(files[0].1.count), at: bombCentral + 24, width: 4)
        rejectLimit(bomb)
    }

    private func rejectArchive(_ data: Data) {
        XCTAssertThrowsError(try importer.validate(data))
    }

    private func rejectLimit(_ data: Data) {
        XCTAssertThrowsError(try importer.validate(data)) { error in
            guard let packError = error as? PackError, case .limit = packError else {
                return XCTFail("Expected bounded-size failure, received \(error)")
            }
        }
    }
}

/// RFC 1951 fixed Huffman block; no compressor dependency or generated content fixture.
private struct PackDeflateFixture {
    private var output = Data()
    private var pending: UInt64 = 0
    private var count = 0

    static func whitespaceExpansion(of realJSON: Data, repetitions: Int) -> Data {
        var stream = Self()
        stream.bits(1, width: 1) // Last block.
        stream.bits(1, width: 2) // Fixed Huffman codes.
        for byte in realJSON { stream.symbol(Int(byte)) }
        stream.symbol(32)
        for _ in 0..<repetitions {
            stream.symbol(285) // Length 258.
            stream.bits(0, width: 5) // Distance 1, repeating the preceding space.
        }
        stream.symbol(256)
        if stream.count > 0 { stream.output.append(UInt8(truncatingIfNeeded: stream.pending)) }
        return stream.output
    }

    private mutating func symbol(_ symbol: Int) {
        let code: Int
        let width: Int
        switch symbol {
        case 0...143: code = 0x30 + symbol; width = 8
        case 144...255: code = 0x190 + symbol - 144; width = 9
        case 256...279: code = symbol - 256; width = 7
        default: code = 0xC0 + symbol - 280; width = 8
        }
        var reversed: UInt64 = 0
        for index in 0..<width { reversed = (reversed << 1) | UInt64((code >> index) & 1) }
        bits(reversed, width: width)
    }

    private mutating func bits(_ value: UInt64, width: Int) {
        pending |= value << count
        count += width
        while count >= 8 {
            output.append(UInt8(truncatingIfNeeded: pending))
            pending >>= 8
            count -= 8
        }
    }
}
