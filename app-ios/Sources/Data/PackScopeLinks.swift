import Foundation
import GRDB

/// Scope rows belong to the user. Only enabled, existing rows may receive content links.
enum PackScopeLinks {
    static func rebuild(_ content: PackContent, in db: Database) throws {
        let rows = try Row.fetchAll(db, sql: "SELECT id, type, province FROM exam_scope WHERE enabled = 1")
        var provincialEvidence: [String: Set<String>] = [:]
        for example in content.examples where example.examType == "省考" {
            if let region = example.region {
                provincialEvidence[region, default: []].formUnion(example.idiomIDs)
            }
        }
        try db.execute(sql: "DELETE FROM idiom_scope")
        for row in rows {
            let scopeID: String = row["id"]
            let type: String = row["type"]
            let province: String? = row["province"]
            for idiom in content.idioms where idiom.scopes.contains(type) {
                if type == "省考" {
                    guard let province, !province.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                          provincialEvidence[province]?.contains(idiom.id) == true else { continue }
                }
                try db.execute(sql: "INSERT INTO idiom_scope(idiomId, scopeId) VALUES (?, ?)",
                               arguments: [idiom.id, scopeID])
            }
        }
    }
}
