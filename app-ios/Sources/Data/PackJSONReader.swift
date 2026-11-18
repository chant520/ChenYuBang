import Foundation

/// A bounded JSON token reader: Foundation's dictionary decoder silently loses duplicate keys.
struct PackJSONReader {
    private let bytes: [UInt8]
    private var offset = 0
    private var nodes = 0

    init(_ data: Data) throws {
        guard String(data: data, encoding: .utf8) != nil else { throw PackError.json("Invalid UTF-8") }
        bytes = Array(data)
    }

    mutating func read() throws -> PackJSON {
        let result = try value(depth: 0)
        whitespace()
        guard offset == bytes.count else { throw PackError.json("Trailing JSON data") }
        return result
    }

    private mutating func value(depth: Int) throws -> PackJSON {
        nodes += 1
        guard depth <= 64, nodes <= 200_000 else { throw PackError.limit("JSON complexity") }
        whitespace()
        switch peek {
        case 123: return try object(depth: depth + 1)
        case 91: return try array(depth: depth + 1)
        case 34: return .string(try string())
        case 116: try literal("true"); return .boolean(true)
        case 102: try literal("false"); return .boolean(false)
        case 110: try literal("null"); return .null
        case 45, 48...57: return try number()
        default: throw PackError.json("Expected JSON value at \(offset)")
        }
    }

    private mutating func object(depth: Int) throws -> PackJSON {
        offset += 1
        whitespace()
        var result: [String: PackJSON] = [:]
        if take(125) { return .object(result) }
        repeat {
            whitespace()
            let key = try string()
            guard result[key] == nil else { throw PackError.json("Duplicate or NFC-colliding key: \(key)") }
            whitespace()
            try expect(58)
            result[key] = try value(depth: depth)
            whitespace()
            if take(125) { return .object(result) }
            try expect(44)
        } while true
    }

    private mutating func array(depth: Int) throws -> PackJSON {
        offset += 1
        whitespace()
        var result: [PackJSON] = []
        if take(93) { return .array(result) }
        repeat {
            result.append(try value(depth: depth))
            whitespace()
            if take(93) { return .array(result) }
            try expect(44)
        } while true
    }

    private mutating func string() throws -> String {
        try expect(34)
        var output = ""
        var start = offset
        while let byte = peek {
            if byte == 34 || byte == 92 {
                guard let segment = String(bytes: bytes[start..<offset], encoding: .utf8) else {
                    throw PackError.json("Invalid string encoding")
                }
                output += segment
                offset += 1
                if byte == 34 { return output.precomposedStringWithCanonicalMapping }
                output += try escapedCharacter()
                start = offset
            } else {
                guard byte >= 32 else { throw PackError.json("Unescaped control character") }
                offset += 1
            }
        }
        throw PackError.json("Unterminated string")
    }

    private mutating func escapedCharacter() throws -> String {
        guard let escape = peek else { throw PackError.json("Truncated escape") }
        offset += 1
        let simple: [UInt8: String] = [
            34: "\"", 92: "\\", 47: "/", 98: "\u{0008}", 102: "\u{000C}", 110: "\n", 114: "\r", 116: "\t"
        ]
        if let character = simple[escape] { return character }
        guard escape == 117 else { throw PackError.json("Invalid escape") }
        return String(try escapedScalar())
    }

    private mutating func escapedScalar() throws -> Unicode.Scalar {
        let first = try hexQuad()
        var scalar = first
        if (0xD800...0xDBFF).contains(first) {
            try expect(92)
            try expect(117)
            let second = try hexQuad()
            guard (0xDC00...0xDFFF).contains(second) else { throw PackError.json("Invalid surrogate pair") }
            scalar = 0x10000 + (first - 0xD800) * 1024 + second - 0xDC00
        }
        guard let result = Unicode.Scalar(scalar) else { throw PackError.json("Unpaired surrogate") }
        return result
    }

    private mutating func hexQuad() throws -> UInt32 {
        var result: UInt32 = 0
        for _ in 0..<4 {
            guard let byte = peek else { throw PackError.json("Truncated Unicode escape") }
            let digit: UInt32
            switch byte {
            case 48...57: digit = UInt32(byte - 48)
            case 65...70: digit = UInt32(byte - 55)
            case 97...102: digit = UInt32(byte - 87)
            default: throw PackError.json("Invalid Unicode escape")
            }
            result = result * 16 + digit
            offset += 1
        }
        return result
    }

    private mutating func number() throws -> PackJSON {
        let start = offset
        _ = take(45)
        if !take(48) {
            guard let byte = peek, (49...57).contains(byte) else { throw PackError.json("Invalid number") }
            digits()
        }
        var floating = false
        if take(46) {
            floating = true
            try requiredDigits()
        }
        if take(101) || take(69) {
            floating = true
            if !take(43) { _ = take(45) }
            try requiredDigits()
        }
        guard offset - start <= 256 else { throw PackError.limit("Number length") }
        guard let token = String(bytes: bytes[start..<offset], encoding: .utf8) else {
            throw PackError.json("Invalid number encoding")
        }
        if !floating { return .integer(token == "-0" ? "0" : token) }
        // Match Python json.loads -> float -> int, including whole-valued exponent notation.
        guard let double = Double(token), double.isFinite, double.rounded(.towardZero) == double else {
            throw PackError.json("Nonintegral or nonfinite number")
        }
        return .integer(double == 0 ? "0" : String(format: "%.0f", locale: Locale(identifier: "en_US_POSIX"), double))
    }

    private mutating func requiredDigits() throws {
        guard let byte = peek, (48...57).contains(byte) else { throw PackError.json("Expected digit") }
        digits()
    }

    private mutating func digits() {
        while let byte = peek, (48...57).contains(byte) { offset += 1 }
    }

    private mutating func literal(_ token: String) throws {
        for byte in token.utf8 { try expect(byte) }
    }

    private mutating func whitespace() {
        while let byte = peek, [9, 10, 13, 32].contains(byte) { offset += 1 }
    }

    private var peek: UInt8? { offset < bytes.count ? bytes[offset] : nil }

    private mutating func take(_ byte: UInt8) -> Bool {
        guard peek == byte else { return false }
        offset += 1
        return true
    }

    private mutating func expect(_ byte: UInt8) throws {
        guard take(byte) else { throw PackError.json("Unexpected token at \(offset)") }
    }
}
