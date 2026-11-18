import Foundation
import GRDB

/// Uses the real v1 migration, including FTS5; never a reduced substitute schema.
enum PackDatabaseValidation {
    static func check(_ content: PackContent, counts: PackCounts) throws {
        let temporary = try DatabaseQueue()
        try ChenYuBangDatabase.makeMigrator().migrate(temporary)
        try temporary.write { db in
            try PackContentWriter.replace(content, in: db)
            guard try count("idiom", in: db) == counts.idioms,
                  try count("example", in: db) == counts.examples,
                  try count("idiom_fts", in: db) == counts.idioms,
                  try count("example_idiom", in: db) == content.examples.reduce(0, { $0 + $1.idiomIDs.count }) else {
                throw PackError.database("Staging counts")
            }
            let broken = try Int.fetchOne(db, sql: """
                SELECT COUNT(*) FROM example_idiom link
                LEFT JOIN idiom ON idiom.id = link.idiomId
                LEFT JOIN example ON example.id = link.exampleId
                WHERE idiom.id IS NULL OR example.id IS NULL
                """) ?? -1
            let inconsistentFTS = try Int.fetchOne(db, sql: """
                SELECT COUNT(*) FROM idiom_fts fts LEFT JOIN idiom ON idiom.id = fts.idiomid
                WHERE idiom.id IS NULL OR fts.word != idiom.word OR fts.initials != idiom.initials
                    OR fts.pinyin != replace(idiom.pinyin, ' ', '') OR fts.sense != idiom.sense
                """) ?? -1
            guard broken == 0, inconsistentFTS == 0 else { throw PackError.database("Staging references/FTS") }
            try db.execute(sql: "INSERT INTO idiom_fts(idiom_fts) VALUES('integrity-check')")
            guard let idiom = content.idioms.first, let scalar = idiom.word.unicodeScalars.first else {
                throw PackError.database("Empty staging content")
            }
            let queries = try [idiom.word, String(scalar), idiom.row.string("pinyin"), idiom.row.string("initials")]
            for query in queries {
                let result = try IdiomSearch.searchIdioms(db, query: query, limit: counts.idioms)
                guard result.contains(where: { $0.id == idiom.id }) else { throw PackError.database("Staging search") }
            }
        }
    }

    private static func count(_ table: String, in db: Database) throws -> Int {
        try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM \(table)") ?? -1
    }
}
