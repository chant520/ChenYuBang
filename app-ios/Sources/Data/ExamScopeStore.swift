import Foundation
import GRDB

public struct ExamScopeSelection: Equatable, Sendable {
    public var national: Bool
    public var provincial: Bool
    public var institution: Bool
    public var provinces: Set<String>

    public init(
        national: Bool = true,
        provincial: Bool = true,
        institution: Bool = true,
        provinces: Set<String> = []
    ) {
        self.national = national
        self.provincial = provincial
        self.institution = institution
        self.provinces = provinces
    }

    public var isEmpty: Bool { !national && !provincial && !institution }
    public var provinceMissing: Bool { provincial && provinces.isEmpty }
    public var description: String {
        var labels: [String] = []
        if national { labels.append("国考") }
        if provincial {
            labels.append(provinceMissing ? "省考（未选省份）" : "省考 · " + provinces.sorted().joined(separator: "、"))
        }
        if institution { labels.append("事业编") }
        return labels.isEmpty ? "未选考试范围" : labels.joined(separator: " · ")
    }
}

public struct LaunchPreferences: Equatable, Sendable {
    public let complete: Bool
    public let scope: ExamScopeSelection

    public init(complete: Bool, scope: ExamScopeSelection) {
        self.complete = complete
        self.scope = scope
    }
}

/// P01/P14 共用；不迁移、不删 scope、不触碰内容和 FTS。
public final class ExamScopeStore {
    private let queue: DatabaseQueue
    private static let completeKey = "firstLaunch.complete"
    private static let provincesKey = "examScope.provinces"

    public init(queue: DatabaseQueue) { self.queue = queue }

    public func load() throws -> LaunchPreferences {
        try queue.read { db in
            let complete = try String.fetchOne(
                db, sql: "SELECT value FROM settings WHERE key = ?", arguments: [Self.completeKey]
            ) == "true"
            let rows = try Row.fetchAll(db, sql: "SELECT type, province, enabled FROM exam_scope")
            guard !rows.isEmpty else { return LaunchPreferences(complete: complete, scope: ExamScopeSelection()) }
            func enabled(_ type: String) -> Bool {
                rows.contains { row in
                    let rowType: String = row["type"]
                    let isEnabled: Bool = row["enabled"]
                    return rowType == type && isEnabled
                }
            }
            let savedProvinces = try String.fetchOne(
                db, sql: "SELECT value FROM settings WHERE key = ?", arguments: [Self.provincesKey]
            )
            let provinces: Set<String>
            if let savedProvinces {
                provinces = Set(try JSONDecoder().decode([String].self, from: Data(savedProvinces.utf8)))
            } else {
                provinces = Set(rows.compactMap { row -> String? in
                    let type: String = row["type"]
                    let isEnabled: Bool = row["enabled"]
                    return type == "省考" && isEnabled ? row["province"] : nil
                })
            }
            return LaunchPreferences(complete: complete, scope: ExamScopeSelection(
                national: enabled("国考"), provincial: enabled("省考"),
                institution: enabled("事业编"), provinces: provinces
            ))
        }
    }

    public func availableProvinces() throws -> [String] {
        try queue.read { db in
            try String.fetchAll(db, sql: """
                SELECT DISTINCT region FROM example
                WHERE examType = '省考' AND trim(region) != '' ORDER BY region
                """)
        }
    }

    /// 完成标记仅向 true 推进；省份选择另存设置，以保留省考关闭时的编辑选择。
    public func save(scope: ExamScopeSelection, complete: Bool = false) throws {
        guard scope.provinces.allSatisfy({ !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) else {
            throw ScopeError.emptyProvince
        }
        let provincesData = try JSONEncoder().encode(scope.provinces.sorted())
        guard let provincesJSON = String(data: provincesData, encoding: .utf8) else {
            throw ScopeError.invalidEncoding
        }
        try queue.write { db in
            try db.execute(sql: "UPDATE exam_scope SET enabled = 0")
            try Self.upsert(db, type: "国考", province: nil, enabled: scope.national)
            try Self.upsert(db, type: "事业编", province: nil, enabled: scope.institution)
            try Self.upsert(db, type: "教师编", province: nil, enabled: false)
            if scope.provinces.isEmpty {
                try Self.upsert(db, type: "省考", province: nil, enabled: scope.provincial)
            } else {
                for province in scope.provinces.sorted() {
                    try Self.upsert(db, type: "省考", province: province, enabled: scope.provincial)
                }
            }
            try Self.setValue(db, key: Self.provincesKey, value: provincesJSON)
            if complete { try Self.setValue(db, key: Self.completeKey, value: "true") }
        }
    }

    private static func upsert(_ db: Database, type: String, province: String?, enabled: Bool) throws {
        let scopeUUID = try String.fetchOne(db, sql: """
            SELECT id FROM exam_scope WHERE type = ? AND province IS ? ORDER BY id LIMIT 1
            """, arguments: [type, province]) ?? UUID().uuidString
        try db.execute(sql: """
            INSERT INTO exam_scope (id, type, province, enabled) VALUES (?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET enabled = excluded.enabled
            """, arguments: [scopeUUID, type, province, enabled])
    }

    private static func setValue(_ db: Database, key: String, value: String) throws {
        try db.execute(sql: """
            INSERT INTO settings (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """, arguments: [key, value])
    }

    private enum ScopeError: Error { case emptyProvince, invalidEncoding }
}
