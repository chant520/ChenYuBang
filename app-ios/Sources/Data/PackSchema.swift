import Foundation

public struct PackCounts: Equatable, Sendable {
    public let idioms: Int
    public let examples: Int
}

struct PackObject {
    let fields: [String: PackJSON]

    init(_ value: PackJSON) throws {
        guard case let .object(fields) = value else { throw PackError.schema("Expected object") }
        self.fields = fields
    }

    func string(_ key: String) throws -> String {
        guard case let .string(value)? = fields[key] else { throw PackError.schema("Expected string: \(key)") }
        return value
    }

    func optionalString(_ key: String) throws -> String? {
        fields[key] == nil ? nil : try string(key)
    }

    func integer(_ key: String) throws -> Int {
        guard case let .integer(value)? = fields[key], let number = Int(value) else {
            throw PackError.schema("Expected bounded integer: \(key)")
        }
        return number
    }

    func array(_ key: String) throws -> [PackJSON] {
        guard case let .array(value)? = fields[key] else { throw PackError.schema("Expected array: \(key)") }
        return value
    }

    func strings(_ key: String) throws -> [String] {
        try array(key).map {
            guard case let .string(value) = $0 else { throw PackError.schema("Expected string array: \(key)") }
            return value
        }
    }

    func json(_ key: String) -> String { PackCanonical.text(fields[key] ?? .array([])) }
}

struct PackIdiom {
    let row: PackObject
    let id: String
    let word: String
    let scopes: Set<String>
}

struct PackExample {
    let row: PackObject
    let id: String
    let idiomIDs: Set<String>
    let examType: String
    let region: String?
}

struct PackContent {
    let idioms: [PackIdiom]
    let examples: [PackExample]
}

/// Mirrors all keywords used by the three checked-in draft-07 schemas; no guessed fields.
indirect enum PackRule {
    case object(required: Set<String>, properties: [String: PackRule])
    case array(PackRule, minimum: Int)
    case string(pattern: String?, values: Set<String>?)
    case integer(minimum: Int, maximum: Int)
    case boolean
    case nullable(PackRule)

    func check(_ value: PackJSON, path: String) throws {
        switch (self, value) {
        case .nullable:
            try checkNullable(value, path: path)
        case let (.object(required, properties), .object(object)):
            try checkObject(object, required: required, properties: properties, path: path)
        case let (.array(rule, minimum), .array(array)):
            guard array.count >= minimum else { throw PackError.schema("Too few items: \(path)") }
            for (index, item) in array.enumerated() { try rule.check(item, path: "\(path)[\(index)]") }
        case let (.string(pattern, values), .string(string)):
            try checkString(string, pattern: pattern, values: values, path: path)
        case let (.integer(minimum, maximum), .integer(token)):
            guard let integer = Int(token), (minimum...maximum).contains(integer) else {
                throw PackError.schema("Integer range: \(path)")
            }
        case (.boolean, .boolean):
            return
        default: throw PackError.schema("Invalid type: \(path)")
        }
    }

    private func checkNullable(_ value: PackJSON, path: String) throws {
        switch (self, value) {
        case (.nullable, .null):
            return
        case let (.nullable(rule), value):
            try rule.check(value, path: path)
        default:
            throw PackError.schema("Invalid nullable rule: \(path)")
        }
    }

    private func checkObject(
        _ object: [String: PackJSON], required: Set<String>, properties: [String: PackRule], path: String
    ) throws {
        guard required.isSubset(of: Set(object.keys)), Set(object.keys).isSubset(of: Set(properties.keys)) else {
            throw PackError.schema("Missing or unknown property: \(path)")
        }
        for (key, field) in object {
            guard let rule = properties[key] else { throw PackError.schema("Unknown field: \(path).\(key)") }
            try rule.check(field, path: path + "." + key)
        }
    }

    private func checkString(_ string: String, pattern: String?, values: Set<String>?, path: String) throws {
        if let values, !values.contains(string) { throw PackError.schema("Invalid enum: \(path)") }
        if let pattern {
            let regex = try NSRegularExpression(pattern: pattern, options: [.dotMatchesLineSeparators])
            let range = NSRange(string.startIndex..<string.endIndex, in: string)
            guard regex.firstMatch(in: string, range: range) != nil else {
                throw PackError.schema("Invalid pattern: \(path)")
            }
        }
    }
}

enum PackSchema {
    private static let uuid = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
    private static let word = "^[\\u4e00-\\u9fa5]{2,8}$"
    private static let examTypes: Set<String> = ["国考", "省考", "事业编", "教师编"]

    static let manifest: PackRule = .object(
        required: ["schemaVersion", "packVersion", "builtAt", "counts", "sha256"], properties: [
            "schemaVersion": .integer(minimum: 1, maximum: Int.max),
            "packVersion": pattern("^v\\d+\\.\\d+\\.\\d+$"),
            "builtAt": pattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$"),
            "sha256": pattern("^[0-9a-f]{64}$"),
            "counts": .object(required: ["idioms", "examples"], properties: [
                "idioms": .integer(minimum: 1, maximum: Int.max),
                "examples": .integer(minimum: 1, maximum: Int.max)
            ])
        ]
    )

    private static let idioms: PackRule = .array(.object(
        required: ["id", "word", "pinyin", "initials", "sense", "color", "frequency", "scopes"], properties: [
            "id": pattern(uuid), "word": pattern(word),
            "pinyin": pattern("^[a-z]+( [a-z]+){1,7}$"), "initials": pattern("^[a-z]{2,8}$"),
            "sense": pattern(".{4,}"), "origin": pattern(".{4,}"),
            "color": enumeration(["褒义", "贬义", "中性"]),
            "frequency": .integer(minimum: 1, maximum: 5),
            "scopes": .array(enumeration(examTypes), minimum: 1),
            "synonyms": .array(pattern(word), minimum: 0), "antonyms": .array(pattern(word), minimum: 0),
            "pitfalls": .array(.object(required: ["kind", "note"], properties: [
                "kind": enumeration(["望文生义", "对象误用", "褒贬误用", "语义重复", "搭配不当"]),
                "note": pattern(".{4,}")
            ]), minimum: 0)
        ]
    ), minimum: 1)

    private static let examples: PackRule = .array(.object(
        required: ["id", "stem", "options", "answer", "explain", "examType", "year", "questionType", "idiomIds"],
        properties: [
            "id": pattern(uuid), "stem": pattern(".{6,}"), "explain": pattern(".{4,}"),
            "options": .object(required: ["A", "B", "C", "D"], properties: [
                "A": pattern(".{2,}"), "B": pattern(".{2,}"), "C": pattern(".{2,}"), "D": pattern(".{2,}")
            ]),
            "answer": enumeration(["A", "B", "C", "D"]), "examType": enumeration(examTypes),
            "year": .integer(minimum: 2000, maximum: 2100), "region": pattern(".{2,}"),
            "questionType": enumeration(["言语理解", "逻辑填空", "语句表达", "常识判断"]),
            "idiomIds": .array(pattern(uuid), minimum: 1)
        ]
    ), minimum: 1)

    static func content(idioms idiomJSON: PackJSON, examples exampleJSON: PackJSON) throws -> PackContent {
        try idioms.check(idiomJSON, path: "idioms")
        try examples.check(exampleJSON, path: "examples")
        guard case let .array(idiomArray) = idiomJSON, case let .array(exampleArray) = exampleJSON else {
            throw PackError.schema("Expected content arrays")
        }
        let idiomRows = try idiomArray.map { value -> PackIdiom in
            let row = try PackObject(value)
            try checkUUID(row.string("id"))
            return try PackIdiom(
                row: row, id: row.string("id"), word: row.string("word"), scopes: Set(row.strings("scopes"))
            )
        }
        let exampleRows = try exampleArray.map { value -> PackExample in
            let row = try PackObject(value)
            try checkUUID(row.string("id"))
            let type = try row.string("examType")
            let region = try row.optionalString("region")
            if type == "省考", region == nil || region?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == true {
                throw PackError.schema("Provincial example requires region")
            }
            let ids = try row.strings("idiomIds")
            for id in ids { try checkUUID(id) }
            guard Set(ids).count == ids.count else { throw PackError.schema("Duplicate idiom reference") }
            return try PackExample(row: row, id: row.string("id"), idiomIDs: Set(ids), examType: type, region: region)
        }
        let ids = Set(idiomRows.map(\.id))
        let allIDs = idiomRows.map(\.id) + exampleRows.map(\.id)
        guard Set(allIDs).count == allIDs.count, Set(idiomRows.map(\.word)).count == idiomRows.count else {
            throw PackError.schema("Duplicate UUID or word")
        }
        guard exampleRows.allSatisfy({ $0.idiomIDs.isSubset(of: ids) }) else {
            throw PackError.schema("Unclosed idiom reference")
        }
        return PackContent(idioms: idiomRows, examples: exampleRows)
    }

    private static func checkUUID(_ id: String) throws {
        guard id.utf8.count == 36, UUID(uuidString: id) != nil else { throw PackError.schema("Invalid UUID") }
    }

    private static func pattern(_ regex: String) -> PackRule { .string(pattern: regex, values: nil) }
    private static func enumeration(_ values: Set<String>) -> PackRule { .string(pattern: nil, values: values) }
}
