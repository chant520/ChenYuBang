import CryptoKit
import Foundation

public enum PackError: Error, Equatable {
    case archive(String)
    case limit(String)
    case json(String)
    case schema(String)
    case unsupportedSchema
    case digestMismatch
    case database(String)
}

indirect enum PackJSON: Equatable {
    case object([String: PackJSON])
    case array([PackJSON])
    case string(String)
    case integer(String)
    case boolean(Bool)
    case null
}

/// Python canonical.py: NFC, scalar-order keys, original array order, no slash escaping.
enum PackCanonical {
    static func text(_ value: PackJSON) -> String {
        switch value {
        case let .object(object):
            let keys = object.keys.sorted {
                $0.unicodeScalars.map(\.value).lexicographicallyPrecedes($1.unicodeScalars.map(\.value))
            }
            return "{" + keys.map { quote($0) + ":" + text(object[$0] ?? .null) }.joined(separator: ",") + "}"
        case let .array(array): return "[" + array.map(text).joined(separator: ",") + "]"
        case let .string(string): return quote(string)
        case let .integer(number): return number
        case let .boolean(boolean): return boolean ? "true" : "false"
        case .null: return "null"
        }
    }

    static func digest(idioms: PackJSON, examples: PackJSON) -> String {
        var hash = SHA256()
        hash.update(data: Data(text(examples).utf8))
        hash.update(data: Data(text(idioms).utf8))
        return hash.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private static func quote(_ string: String) -> String {
        var output = "\""
        for scalar in string.precomposedStringWithCanonicalMapping.unicodeScalars {
            switch scalar.value {
            case 34: output += "\\\""
            case 92: output += "\\\\"
            case 8: output += "\\b"
            case 9: output += "\\t"
            case 10: output += "\\n"
            case 12: output += "\\f"
            case 13: output += "\\r"
            case 0...31: output += String(format: "\\u%04x", scalar.value)
            default: output.unicodeScalars.append(scalar)
            }
        }
        return output + "\""
    }
}
