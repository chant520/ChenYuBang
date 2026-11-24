import CryptoKit
import Foundation
import GRDB
import ZIPFoundation

/// 备份导出（P15，开发规范第 4.3 / 4.5 节）。
/// 包名 `backup-YYYYMMDD-HHMMSS.zip`，包内只有 `manifest.json` + `data.json`：
/// 不含原图、不含题库本身，但用户录入题 `userExamples[]` 必须导出。
public enum BackupError: Error, Equatable {
    case database(String)
    case fileIO(String)
    case format(String)
}

public struct BackupCounts: Equatable, Sendable {
    public let mistakes: Int
    public let userExamples: Int
    public let reviews: Int
    public let idiomStates: Int
    public let scopes: Int

    public init(mistakes: Int, userExamples: Int, reviews: Int, idiomStates: Int, scopes: Int) {
        self.mistakes = mistakes
        self.userExamples = userExamples
        self.reviews = reviews
        self.idiomStates = idiomStates
        self.scopes = scopes
    }
}

public struct BackupExport: Equatable, Sendable {
    public let url: URL
    public let sha256: String
    public let counts: BackupCounts

    public init(url: URL, sha256: String, counts: BackupCounts) {
        self.url = url
        self.sha256 = sha256
        self.counts = counts
    }
}

/// manifest 里的应用版本标识（第 4.5 节 `appVersion` / `build`）。
struct BackupIdentity {
    let appVersion: String
    let build: Int

    init(appVersion: String = Backup.bundleAppVersion, build: Int = Backup.bundleBuild) {
        self.appVersion = appVersion
        self.build = build
    }
}

enum Backup {
    /// 交换格式 schemaVersion，与 DB 版本各自独立编号（第 4.5 节）。
    static let exchangeSchemaVersion = 3

    static func export(db: Database, to directory: URL, date: Date,
                       identity: BackupIdentity = BackupIdentity(),
                       namePrefix: String = "backup-") throws -> BackupExport {
        let (payload, counts) = try dataJSON(db)
        do {
            try BackupSchema.data.check(payload, path: "data")
        } catch {
            throw BackupError.format("本机数据不符合备份格式：\(error.localizedDescription)")
        }
        let dataText = PackCanonical.text(payload)
        let digest = digest(of: dataText)
        let manifestText = PackCanonical.text(manifestJSON(counts: counts, sha256: digest,
                                                           date: date, identity: identity))
        let target = try writeArchive(directory: directory, date: date, namePrefix: namePrefix,
                                      dataText: dataText, manifestText: manifestText)
        return BackupExport(url: target, sha256: digest, counts: counts)
    }

    private static func writeArchive(directory: URL, date: Date, namePrefix: String,
                                     dataText: String, manifestText: String) throws -> URL {
        let fileManager = FileManager.default
        let staging = fileManager.temporaryDirectory
            .appendingPathComponent("backup-stage-\(UUID().uuidString)", isDirectory: true)
        let target = directory.appendingPathComponent(fileName(date, prefix: namePrefix))
        do {
            try fileManager.createDirectory(at: staging, withIntermediateDirectories: true)
            try Data(dataText.utf8).write(to: staging.appendingPathComponent("data.json"), options: .atomic)
            try Data(manifestText.utf8)
                .write(to: staging.appendingPathComponent("manifest.json"), options: .atomic)
            if fileManager.fileExists(atPath: target.path) { try fileManager.removeItem(at: target) }
            let archive = try Archive(url: target, accessMode: .create)
            _ = try archive.addEntry(with: "manifest.json",
                                     fileURL: staging.appendingPathComponent("manifest.json"))
            _ = try archive.addEntry(with: "data.json",
                                     fileURL: staging.appendingPathComponent("data.json"))
        } catch {
            throw BackupError.fileIO(String(describing: error))
        }
        try? fileManager.removeItem(at: staging)
        return target
    }

    private static func manifestJSON(counts: BackupCounts, sha256: String,
                                     date: Date, identity: BackupIdentity) -> PackJSON {
        .object([
            "schemaVersion": .integer(String(exchangeSchemaVersion)),
            "appVersion": .string(normalized(appVersion: identity.appVersion)),
            "build": .integer(String(identity.build)),
            "exportedAt": .string(stamp(date)),
            "counts": .object([
                "mistakes": .integer(String(counts.mistakes)),
                "userExamples": .integer(String(counts.userExamples)),
                "reviews": .integer(String(counts.reviews)),
                "idiomStates": .integer(String(counts.idiomStates)),
                "scopes": .integer(String(counts.scopes))
            ]),
            "sha256": .string(sha256)
        ])
    }

    // MARK: - data.json

    static func dataJSON(_ db: Database) throws -> (PackJSON, BackupCounts) {
        let counts = try counts(of: db)
        let object = PackJSON.object([
            "mistakes": .array(try mistakeObjects(db)),
            "userExamples": .array(try exampleObjects(db)),
            "reviews": .array(try reviewObjects(db)),
            "idiomStates": .array(try idiomStateObjects(db)),
            "scopes": .array(try scopeObjects(db)),
            "tags": .array(collectedTags(try fetch(db, "SELECT tagNames FROM mistake ORDER BY id")))
        ])
        return (object, counts)
    }

    private static func counts(of db: Database) throws -> BackupCounts {
        BackupCounts(mistakes: try count(db, "mistake"),
                     userExamples: try count(db, "user_example"),
                     reviews: try count(db, "review_log"),
                     idiomStates: try count(db, "idiom_state"),
                     scopes: try count(db, "exam_scope"))
    }

    private static func mistakeObjects(_ db: Database) throws -> [PackJSON] {
        let rows = try fetch(db, """
            SELECT id, userExampleId, imagePath, ocrText, stem, optionA, optionB, optionC, optionD,
                   myAnswer, reason, tagNames, examType, year, region, state, createdAt,
                   lastReviewedAt, reviewCount
            FROM mistake ORDER BY lower(id)
            """)
        let links = try links(db, "mistake_idiom", "mistakeId", "idiomId")
        return rows.map { row in
            let id: String = row["id"]
            return .object([
                "id": .string(id.lowercased()),
                "userExampleId": nullableLowercased(row, "userExampleId"),
                "stem": .string(row["stem"]),
                "options": .object([
                    "A": .string(row["optionA"]),
                    "B": .string(row["optionB"]),
                    "C": .string(row["optionC"]),
                    "D": .string(row["optionD"])
                ]),
                "imagePath": nullable(row, "imagePath"),
                "ocrText": nullable(row, "ocrText"),
                "myAnswer": nullable(row, "myAnswer"),
                "reason": nullable(row, "reason"),
                "tagNames": .array(tagNames(row["tagNames"]).map { PackJSON.string($0) }),
                "examType": nullable(row, "examType"),
                "year": number(row, "year"),
                "region": nullable(row, "region"),
                "state": .string(row["state"]),
                "createdAt": .string(row["createdAt"]),
                "lastReviewedAt": nullable(row, "lastReviewedAt"),
                "reviewCount": .integer(String(row["reviewCount"] as Int)),
                "idiomIds": .array((links[id.lowercased()] ?? []).map { PackJSON.string($0) })
            ])
        }
    }

    private static func exampleObjects(_ db: Database) throws -> [PackJSON] {
        let rows = try fetch(db, """
            SELECT id, imagePath, ocrText, stem, optionA, optionB, optionC, optionD, myAnswer,
                   isMistake, examType, year, region, createdAt
            FROM user_example ORDER BY lower(id)
            """)
        let links = try linkedExampleIDs(db, mistakeRows: try fetch(db, """
            SELECT id, userExampleId FROM mistake ORDER BY lower(id)
            """))
        return rows.map { row in
            let id: String = row["id"]
            return .object([
                "id": .string(id.lowercased()),
                "imagePath": nullable(row, "imagePath"),
                "ocrText": nullable(row, "ocrText"),
                "stem": .string(row["stem"]),
                "optionA": nullable(row, "optionA"),
                "optionB": nullable(row, "optionB"),
                "optionC": nullable(row, "optionC"),
                "optionD": nullable(row, "optionD"),
                "myAnswer": nullable(row, "myAnswer"),
                "isMistake": .boolean(row["isMistake"] as Bool),
                "examType": nullable(row, "examType"),
                "year": number(row, "year"),
                "region": nullable(row, "region"),
                "createdAt": .string(row["createdAt"]),
                "idiomIds": .array((links[id.lowercased()] ?? []).map { PackJSON.string($0) })
            ])
        }
    }

    private static func reviewObjects(_ db: Database) throws -> [PackJSON] {
        try fetch(db, "SELECT id, mistakeId, result, at FROM review_log ORDER BY lower(id)").map { row in
            .object([
                "id": .string((row["id"] as String).lowercased()),
                "mistakeId": .string((row["mistakeId"] as String).lowercased()),
                "result": .string(row["result"]),
                "at": .string(row["at"])
            ])
        }
    }

    private static func idiomStateObjects(_ db: Database) throws -> [PackJSON] {
        try fetch(db, "SELECT idiomId, status, favorite FROM idiom_state ORDER BY lower(idiomId)").map { row in
            .object([
                "idiomId": .string((row["idiomId"] as String).lowercased()),
                "status": .string(row["status"]),
                "favorite": .boolean(row["favorite"] as Bool)
            ])
        }
    }

    private static func scopeObjects(_ db: Database) throws -> [PackJSON] {
        try fetch(db, "SELECT type, province, enabled FROM exam_scope ORDER BY type, province").map { row in
            .object([
                "type": .string(row["type"]),
                "province": nullable(row, "province"),
                "enabled": .boolean(row["enabled"] as Bool)
            ])
        }
    }

    /// v3 之前的历史录入题可能没有关联行，回落错题快照的归属；仍为空则如实导出空数组。
    private static func linkedExampleIDs(_ db: Database,
                                         mistakeRows: [Row]) throws -> [String: [String]] {
        var exampleLinks = try links(db, "user_example_idiom", "userExampleId", "idiomId")
        let mistakeLinks = try links(db, "mistake_idiom", "mistakeId", "idiomId")
        for row in mistakeRows {
            let mistakeID: String = row["id"]
            guard let exampleID: String = row["userExampleId"] else { continue }
            let normalizedExampleID = exampleID.lowercased()
            guard exampleLinks[normalizedExampleID] == nil,
                  let ids = mistakeLinks[mistakeID.lowercased()], !ids.isEmpty else { continue }
            exampleLinks[normalizedExampleID] = ids
        }
        return exampleLinks
    }

    private static func collectedTags(_ rows: [Row]) -> [PackJSON] {
        var tags = Set<String>()
        for row in rows { tags.formUnion(tagNames(row["tagNames"])) }
        return tags.sorted().map { name in .object(["name": .string(name)]) }
    }

    // MARK: - 取值与工具

    private static func fetch(_ db: Database, _ sql: String) throws -> [Row] {
        do {
            return try Row.fetchAll(db, sql: sql)
        } catch {
            throw BackupError.database(String(describing: error))
        }
    }

    private static func count(_ db: Database, _ table: String) throws -> Int {
        do {
            return try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") ?? 0
        } catch {
            throw BackupError.database(String(describing: error))
        }
    }

    private static func links(_ db: Database, _ table: String,
                              _ own: String, _ other: String) throws -> [String: [String]] {
        var map: [String: [String]] = [:]
        for row in try fetch(db, "SELECT \(own), \(other) FROM \(table) ORDER BY lower(\(other))") {
            let key: String = row[own]
            let value: String = row[other]
            map[key.lowercased(), default: []].append(value.lowercased())
        }
        return map
    }

    private static func nullable(_ row: Row, _ column: String) -> PackJSON {
        let value: String? = row[column]
        guard let value else { return .null }
        return .string(value)
    }

    private static func nullableLowercased(_ row: Row, _ column: String) -> PackJSON {
        guard let value: String = row[column] else { return .null }
        return .string(value.lowercased())
    }

    private static func number(_ row: Row, _ column: String) -> PackJSON {
        let value: Int? = row[column]
        guard let value else { return .null }
        return .integer(String(value))
    }

    private static func tagNames(_ raw: String) -> [String] {
        (try? JSONSerialization.jsonObject(with: Data(raw.utf8))) as? [String] ?? []
    }

    static func digest(of canonicalText: String) -> String {
        var hash = SHA256()
        hash.update(data: Data(canonicalText.utf8))
        return hash.finalize().map { String(format: "%02x", $0) }.joined()
    }

}

/// P15 的导出入口：只读库，产物落在临时目录，交给系统分享面板另存网盘。
public final class BackupService: @unchecked Sendable {
    private let queue: DatabaseQueue

    public init(queue: DatabaseQueue) {
        self.queue = queue
    }

    public func export(to directory: URL = FileManager.default.temporaryDirectory) throws -> BackupExport {
        try queue.read { db in
            try Backup.export(db: db, to: directory, date: Date())
        }
    }

    public func validate(_ data: Data) throws -> ValidatedBackup {
        try queue.read { db in try BackupImport.validate(data, db: db) }
    }

    public func importBackup(_ validated: ValidatedBackup,
                             mode: BackupImportMode) throws -> BackupImportSummary {
        switch mode {
        case .merge:
            return try queue.write { db in try BackupImportWriter.merge(validated.payload, db: db) }
        case .replace:
            return try BackupImportWriter.replace(validated.payload, queue: queue)
        }
    }
}
