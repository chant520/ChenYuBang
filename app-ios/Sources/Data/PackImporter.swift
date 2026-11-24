import Foundation
import GRDB

/// Opaque validated capability: no public or internal initializer can bypass validate.
public struct ValidatedPack {
    public let version: String
    public let builtAt: String
    public let counts: PackCounts
    public let sha256: String
    fileprivate let content: PackContent

    // The fileprivate stored property makes the synthesized memberwise initializer fileprivate.
}

public struct PackImportResult: Equatable, Sendable {
    public let version: String
    public let counts: PackCounts
}

public final class PackImporter {
    private let queue: DatabaseQueue
    private var installedSHA256: String?

    public init(queue: DatabaseQueue) { self.queue = queue }

    /// Validation is read-only with respect to queue; all staging lives in a migrated memory DB.
    public func validate(_ data: Data) throws -> ValidatedPack {
        let files = try PackArchive.read(data)
        func json(_ name: String) throws -> PackJSON {
            guard let file = files[name] else { throw PackError.archive("Missing \(name)") }
            var reader = try PackJSONReader(file)
            return try reader.read()
        }
        let manifestJSON = try json("manifest.json")
        let idioms = try json("idioms.json")
        let examples = try json("examples.json")
        try PackSchema.manifest.check(manifestJSON, path: "manifest")
        let manifest = try PackObject(manifestJSON)
        guard try manifest.integer("schemaVersion") == 1 else { throw PackError.unsupportedSchema }
        let digest = PackCanonical.digest(idioms: idioms, examples: examples)
        guard try digest == manifest.string("sha256") else { throw PackError.digestMismatch }
        let content = try PackSchema.content(idioms: idioms, examples: examples)
        guard let countsJSON = manifest.fields["counts"] else { throw PackError.schema("Missing counts") }
        let countFields = try PackObject(countsJSON)
        let counts = try PackCounts(idioms: countFields.integer("idioms"), examples: countFields.integer("examples"))
        guard counts.idioms == content.idioms.count, counts.examples == content.examples.count else {
            throw PackError.schema("Manifest counts mismatch")
        }
        try PackDatabaseValidation.check(content, counts: counts)
        return try ValidatedPack(
            version: manifest.string("packVersion"), builtAt: manifest.string("builtAt"),
            counts: counts, sha256: digest, content: content
        )
    }

    /// GRDB write wraps content replacement + scope links + FTS in one rollback-capable transaction.
    /// The caller alone owns version settings and calls this only for its selected update.
    public func importPack(_ pack: ValidatedPack) throws -> PackImportResult {
        try queue.writeWithoutTransaction { db in
            try db.inTransaction {
                try PackContentWriter.replace(pack.content, in: db)
                return .commit
            }
            installedSHA256 = pack.sha256
        }
        return PackImportResult(version: pack.version, counts: pack.counts)
    }

    /// Call with the current installed validated pack after P01/P14 saves its user-owned scope.
    public func refreshScopeLinks(_ pack: ValidatedPack) throws {
        try queue.write { db in
            guard installedSHA256 == pack.sha256 else {
                throw PackError.database("refresh requires the pack committed by this importer")
            }
            let activeIDs = Set(try String.fetchAll(db, sql: "SELECT idiomid FROM idiom_fts"))
            let exampleIDs = Set(try String.fetchAll(db, sql: "SELECT id FROM example"))
            guard activeIDs == Set(pack.content.idioms.map(\.id)),
                  exampleIDs == Set(pack.content.examples.map(\.id)) else {
                throw PackError.database("refresh requires the currently installed pack")
            }
            try PackScopeLinks.rebuild(pack.content, in: db)
        }
    }
}
