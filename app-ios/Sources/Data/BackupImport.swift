import Foundation
import GRDB
import ZIPFoundation

public enum BackupImportMode: String, CaseIterable, Sendable {
    case merge
    case replace
}

public struct BackupImportSummary: Equatable, Sendable {
    public let mode: BackupImportMode
    public let mistakes: Int
    public let userExamples: Int
    public let reviews: Int
    public let idiomStates: Int
    public let scopes: Int
    public let automaticBackup: URL?

    public init(mode: BackupImportMode, mistakes: Int, userExamples: Int, reviews: Int,
                idiomStates: Int, scopes: Int, automaticBackup: URL?) {
        self.mode = mode
        self.mistakes = mistakes
        self.userExamples = userExamples
        self.reviews = reviews
        self.idiomStates = idiomStates
        self.scopes = scopes
        self.automaticBackup = automaticBackup
    }

    public var message: String {
        let modeText = mode == .replace ? "覆盖" : "合并"
        return "\(modeText)导入完成：备份含 \(mistakes) 条错题、\(userExamples) 条录入题、\(reviews) 条复习记录。"
    }
}

/// 已通过 schemaVersion、sha256、结构、引用关系校验，可安全进入 P22 选择导入方式。
public struct ValidatedBackup: Equatable, Sendable {
    internal let manifest: BackupManifestPayload
    internal let payload: BackupDataPayload

    public var counts: BackupCounts {
        BackupCounts(mistakes: payload.mistakes.count,
                     userExamples: payload.userExamples?.count ?? 0,
                     reviews: payload.reviews.count,
                     idiomStates: payload.idiomStates.count,
                     scopes: payload.scopes.count)
    }

    public var exportedAt: String { manifest.exportedAt }
    public var sha256: String { manifest.sha256 }
}

struct BackupManifestPayload: Decodable, Equatable, Sendable {
    let schemaVersion: Int
    let appVersion: String
    let build: Int
    let exportedAt: String
    let counts: BackupCountsPayload
    let sha256: String
}

struct BackupCountsPayload: Decodable, Equatable, Sendable {
    let mistakes: Int
    let userExamples: Int?
    let reviews: Int
    let idiomStates: Int
    let scopes: Int
}

struct BackupDataPayload: Decodable, Equatable, Sendable {
    let mistakes: [BackupMistakePayload]
    let userExamples: [BackupUserExamplePayload]?
    let reviews: [BackupReviewPayload]
    let idiomStates: [BackupIdiomStatePayload]
    let scopes: [BackupScopePayload]
    let tags: [BackupTagPayload]
}

struct BackupMistakePayload: Decodable, Equatable, Sendable {
    let id: String
    let userExampleId: String?
    let stem: String
    let options: BackupOptionsPayload
    let imagePath: String?
    let ocrText: String?
    let myAnswer: String?
    let reason: String?
    let tagNames: [String]?
    let examType: String?
    let year: Int?
    let region: String?
    let state: String
    let createdAt: String
    let lastReviewedAt: String?
    let reviewCount: Int
    let idiomIds: [String]
}

struct BackupOptionsPayload: Decodable, Equatable, Sendable {
    let optionA: String
    let optionB: String
    let optionC: String
    let optionD: String

    enum CodingKeys: String, CodingKey {
        case optionA = "A", optionB = "B", optionC = "C", optionD = "D"
    }
}

struct BackupUserExamplePayload: Decodable, Equatable, Sendable {
    let id: String
    let imagePath: String?
    let ocrText: String?
    let stem: String
    let optionA: String?
    let optionB: String?
    let optionC: String?
    let optionD: String?
    let myAnswer: String?
    let isMistake: Bool
    let examType: String?
    let year: Int?
    let region: String?
    let createdAt: String
    let idiomIds: [String]
}

struct BackupReviewPayload: Decodable, Equatable, Sendable {
    let id: String
    let mistakeId: String
    let result: String
    let at: String
}

struct BackupIdiomStatePayload: Decodable, Equatable, Sendable {
    let idiomId: String
    let status: String
    let favorite: Bool
}

struct BackupScopePayload: Decodable, Equatable, Sendable {
    let type: String
    let province: String?
    let enabled: Bool
}

struct BackupTagPayload: Decodable, Equatable, Sendable {
    let name: String
}

enum BackupImport {
    static func validate(_ data: Data, db: Database) throws -> ValidatedBackup {
        let files = try BackupArchive.read(data)
        let manifestJSON = try parse(files["manifest.json"], "manifest.json")
        let dataJSON = try parse(files["data.json"], "data.json")
        do {
            try BackupSchema.manifest.check(manifestJSON, path: "manifest")
            try BackupSchema.data.check(dataJSON, path: "data")
        } catch {
            throw BackupError.format("备份字段不符合共享 schema：\(error.localizedDescription)")
        }
        let manifest = try decode(BackupManifestPayload.self, manifestJSON, "manifest.json")
        let payload = try decode(BackupDataPayload.self, dataJSON, "data.json")
        guard manifest.schemaVersion <= Backup.exchangeSchemaVersion else {
            throw BackupError.format("备份版本过高（schemaVersion \(manifest.schemaVersion)），请升级 App 后重试。")
        }
        guard manifest.schemaVersion >= 1 else {
            throw BackupError.format("备份版本无效（schemaVersion \(manifest.schemaVersion)）。")
        }
        let actualDigest = Backup.digest(of: PackCanonical.text(dataJSON))
        guard actualDigest.lowercased() == manifest.sha256.lowercased() else {
            throw BackupError.format("备份摘要不符，文件可能已损坏或被改写。")
        }
        try payload.validate()
        try validateCounts(manifest.counts, payload)
        try validateReferences(payload, db)
        return ValidatedBackup(manifest: manifest, payload: payload)
    }

    private static func parse(_ data: Data?, _ name: String) throws -> PackJSON {
        guard let data else { throw BackupError.format("备份缺少 \(name)。") }
        var reader = try PackJSONReader(data)
        do {
            return try reader.read()
        } catch {
            throw BackupError.format("\(name) 不是合法 JSON：\(error.localizedDescription)")
        }
    }

    private static func decode<T: Decodable>(_ type: T.Type, _ json: PackJSON,
                                             _ name: String) throws -> T {
        do {
            return try JSONDecoder().decode(type, from: Data(PackCanonical.text(json).utf8))
        } catch {
            throw BackupError.format("\(name) 字段不符合备份格式：\(error.localizedDescription)")
        }
    }

    private static func validateCounts(_ counts: BackupCountsPayload,
                                       _ payload: BackupDataPayload) throws {
        guard counts.mistakes == payload.mistakes.count,
              counts.reviews == payload.reviews.count,
              counts.idiomStates == payload.idiomStates.count,
              counts.scopes == payload.scopes.count,
              counts.userExamples == nil || counts.userExamples == payload.userExamples?.count else {
            throw BackupError.format("备份条数与 data.json 实际内容不一致。")
        }
    }

    private static func validateReferences(_ payload: BackupDataPayload, _ db: Database) throws {
        let mistakeIDs = try uniqueIDs(payload.mistakes.map(\.id), "错题")
        let exampleIDs = try uniqueIDs(payload.userExamples?.map(\.id) ?? [], "录入题")
        _ = try uniqueIDs(payload.reviews.map(\.id), "复习记录")
        let idiomIDs = try existingIdiomIDs(payload, db)
        for mistake in payload.mistakes {
            guard mistake.idiomIds.allSatisfy({ idiomIDs.contains($0) }) else {
                throw BackupError.format("错题 \(mistake.id) 引用了不存在的成语。")
            }
            if let exampleID = mistake.userExampleId, !exampleIDs.contains(exampleID) {
                throw BackupError.format("错题 \(mistake.id) 找不到对应录入题。")
            }
        }
        for example in payload.userExamples ?? [] {
            guard example.idiomIds.allSatisfy({ idiomIDs.contains($0) }) else {
                throw BackupError.format("录入题 \(example.id) 引用了不存在的成语。")
            }
        }
        for review in payload.reviews {
            guard mistakeIDs.contains(review.mistakeId) else {
                throw BackupError.format("复习记录 \(review.id) 找不到对应错题。")
            }
        }
    }

    fileprivate static func uniqueIDs(_ ids: [String], _ label: String) throws -> Set<String> {
        guard Set(ids).count == ids.count else { throw BackupError.format("备份中有重复的\(label) id。") }
        return Set(ids)
    }

    private static func existingIdiomIDs(_ payload: BackupDataPayload,
                                          _ db: Database) throws -> Set<String> {
        let referenced = Set((payload.mistakes.flatMap(\.idiomIds)
            + (payload.userExamples ?? []).flatMap(\.idiomIds)
            + payload.idiomStates.map(\.idiomId)))
        guard !referenced.isEmpty else { return [] }
        let placeholders = referenced.map { _ in "?" }.joined(separator: ",")
        let rows = try Row.fetchAll(db, sql: "SELECT id FROM idiom WHERE id IN (\(placeholders))",
                                    arguments: StatementArguments(Array(referenced)))
        let existing = Set(rows.map { row in row["id"] as String })
        guard existing == referenced else { throw BackupError.format("备份引用了本机不存在的成语。") }
        return existing
    }
}

extension BackupDataPayload {
    fileprivate func validate() throws {
        let tagNames = Set(tags.map(\.name))
        guard tagNames.count == tags.count else { throw BackupError.format("备份中有重复标签。") }
        let stateIDs = idiomStates.map(\.idiomId)
        guard Set(stateIDs).count == stateIDs.count else { throw BackupError.format("备份中有重复成语学习状态。") }
        let scopeKeys = scopes.map { "\($0.type)\u{0}\($0.province ?? "")" }
        guard Set(scopeKeys).count == scopeKeys.count else { throw BackupError.format("备份中有重复考试范围。") }
        let reviewCounts = Dictionary(grouping: reviews, by: \.mistakeId).mapValues(\.count)
        for mistake in mistakes {
            try mistake.validate()
            _ = try BackupImport.uniqueIDs(mistake.idiomIds, "错题成语关联")
            let names = mistake.tagNames ?? []
            guard names.allSatisfy({ tagNames.contains($0) }) else {
                throw BackupError.format("错题 \(mistake.id) 的标签不在 tags 投影中。")
            }
            guard reviewCounts[mistake.id] ?? 0 == mistake.reviewCount else {
                throw BackupError.format("错题 \(mistake.id) 的复习条数与复习记录不一致。")
            }
            if let path = mistake.imagePath, !path.hasPrefix("images/") {
                throw BackupError.format("错题 \(mistake.id) 的原图路径不合法。")
            }
        }
        for example in userExamples ?? [] {
            try example.validate()
            _ = try BackupImport.uniqueIDs(example.idiomIds, "录入题成语关联")
            if let path = example.imagePath, !path.hasPrefix("images/") {
                throw BackupError.format("录入题 \(example.id) 的原图路径不合法。")
            }
        }
        for review in reviews {
            try BackupValidationRule.require(review.id, "复习记录 id")
            try BackupValidationRule.require(review.mistakeId, "复习记录 mistakeId")
            guard ["对", "错"].contains(review.result) else { throw BackupError.format("复习结果不合法。") }
        }
        for state in idiomStates {
            try BackupValidationRule.require(state.idiomId, "学习状态 idiomId")
            guard ["未学", "学过", "已掌握"].contains(state.status) else {
                throw BackupError.format("成语学习状态不合法。")
            }
        }
        for scope in scopes {
            guard ["国考", "省考", "事业编", "教师编"].contains(scope.type) else {
                throw BackupError.format("考试范围类型不合法。")
            }
        }
        for tag in tags {
            guard (1...8).contains(tag.name.count) else { throw BackupError.format("错因标签长度须为 1–8 字。") }
        }
    }
}

extension BackupMistakePayload {
    fileprivate func validate() throws {
        try BackupValidationRule.require(id, "错题 id")
        try BackupValidationRule.require(stem, "错题题干")
        guard stem.count >= 2, ["待复习", "已掌握"].contains(state), reviewCount >= 0,
              validAnswer(myAnswer), validExamType(examType), validYear(year),
              !idiomIds.isEmpty else { throw BackupError.format("错题 \(id) 字段不符合备份格式。") }
    }
}

extension BackupUserExamplePayload {
    fileprivate func validate() throws {
        try BackupValidationRule.require(id, "录入题 id")
        try BackupValidationRule.require(stem, "录入题题干")
        guard stem.count >= 2, validAnswer(myAnswer), validExamType(examType), validYear(year),
              !idiomIds.isEmpty else { throw BackupError.format("录入题 \(id) 字段不符合备份格式。") }
    }
}

enum BackupValidationRule {
    static func require(_ value: String, _ label: String) throws {
        guard value.count == 36, UUID(uuidString: value) != nil, value == value.lowercased() else {
            throw BackupError.format("\(label) 不是小写 UUID。")
        }
    }

    static func validAnswer(_ answer: String?) -> Bool {
        answer == nil || ["A", "B", "C", "D"].contains(answer ?? "")
    }

    static func validExamType(_ type: String?) -> Bool {
        type == nil || ["国考", "省考", "事业编", "教师编"].contains(type ?? "")
    }

    static func validYear(_ year: Int?) -> Bool {
        year == nil || (2_000...2_100).contains(year ?? 0)
    }
}

/// 只接受两个根文件；解压前检查元数据，解压时限制每文件与总大小并校验 CRC。
enum BackupArchive {
    static let maximumInput = 16 * 1024 * 1024
    static let maximumFile = 16 * 1024 * 1024
    static let maximumExpanded = 32 * 1024 * 1024
    static let names: Set<String> = ["manifest.json", "data.json"]

    static func read(_ data: Data) throws -> [String: Data] {
        guard data.count <= maximumInput else { throw BackupError.format("备份文件过大。") }
        let archive = try Archive(data: data, accessMode: .read)
        let entries = Array(archive)
        guard entries.count == 2 else { throw BackupError.format("备份包必须只含 manifest.json 与 data.json。") }
        var files: [String: Data] = [:]
        var total = 0
        for entry in entries {
            guard entry.type == .file, names.contains(entry.path), files[entry.path] == nil,
                  entry.uncompressedSize <= UInt32(maximumFile),
                  entry.uncompressedSize <= UInt32(maximumExpanded - total) else {
                throw BackupError.format("备份包目录或条目大小不合法。")
            }
            var file = Data()
            let checksum = try archive.extract(entry, bufferSize: 16 * 1024, skipCRC32: false) { chunk in
                guard chunk.count <= maximumFile - file.count,
                      chunk.count <= maximumExpanded - total - file.count else {
                    throw BackupError.format("备份包解压大小超出限制。")
                }
                file.append(chunk)
            }
            guard file.count == Int(entry.uncompressedSize), checksum == entry.crc else {
                throw BackupError.format("备份条目 CRC 或大小校验失败。")
            }
            files[entry.path] = file
            total += file.count
        }
        guard Set(files.keys) == names else { throw BackupError.format("备份缺少 manifest.json 或 data.json。") }
        return files
    }
}
