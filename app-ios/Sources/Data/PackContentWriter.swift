import Foundation
import GRDB

/// Only content DML. Does not replace the user's database or mutate user tables.
enum PackContentWriter {
    static func replace(_ content: PackContent, in db: Database) throws {
        let incoming = Set(content.idioms.map(\.id))
        let oldIDs = try String.fetchAll(db, sql: "SELECT id FROM idiom")
        try db.execute(sql: "DELETE FROM idiom_scope")
        try db.execute(sql: "DELETE FROM example_idiom")
        try db.execute(sql: "DELETE FROM example")
        for idiom in content.idioms { try insert(idiom, in: db) }
        for example in content.examples { try insert(example, in: db) }
        for oldID in oldIDs where !incoming.contains(oldID) {
            try db.execute(sql: """
                DELETE FROM idiom WHERE id = ?
                AND NOT EXISTS (SELECT 1 FROM mistake_idiom WHERE idiomId = idiom.id)
                AND NOT EXISTS (SELECT 1 FROM idiom_state WHERE idiomId = idiom.id)
                """, arguments: [oldID])
        }
        let duplicate = try Int.fetchOne(db, sql: "SELECT 1 FROM idiom GROUP BY word HAVING COUNT(*) > 1 LIMIT 1")
        guard duplicate == nil else { throw PackError.database("Pack word conflicts with retained user content") }
        try IdiomSearch.rebuildIdiomFts(db)
        // Retained orphan content is available to mistakes/state but not content search or scope lists.
        for oldID in oldIDs where !incoming.contains(oldID) {
            try db.execute(sql: "DELETE FROM idiom_fts WHERE idiomid = ?", arguments: [oldID])
        }
        try PackScopeLinks.rebuild(content, in: db)
    }

    private static func insert(_ idiom: PackIdiom, in db: Database) throws {
        let row = idiom.row
        let arguments: StatementArguments = try [
            idiom.id, idiom.word, row.string("pinyin"), row.string("initials"), row.string("sense"),
            row.optionalString("origin"), row.string("color"), row.json("pitfalls"), row.json("synonyms"),
            row.json("antonyms"), row.integer("frequency")
        ]
        try db.execute(sql: """
            INSERT INTO idiom(id, word, pinyin, initials, sense, origin, color, pitfalls, synonyms, antonyms, frequency)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET word = excluded.word, pinyin = excluded.pinyin,
                initials = excluded.initials, sense = excluded.sense, origin = excluded.origin,
                color = excluded.color, pitfalls = excluded.pitfalls, synonyms = excluded.synonyms,
                antonyms = excluded.antonyms, frequency = excluded.frequency
            """, arguments: arguments)
    }

    private static func insert(_ example: PackExample, in db: Database) throws {
        let row = example.row
        guard let optionsJSON = row.fields["options"] else { throw PackError.schema("Missing options") }
        let options = try PackObject(optionsJSON)
        let arguments: StatementArguments = try [
            example.id, row.string("stem"), options.string("A"), options.string("B"), options.string("C"),
            options.string("D"), row.string("answer"), row.string("explain"), example.examType,
            row.integer("year"), example.region ?? "", row.string("questionType")
        ]
        try db.execute(sql: """
            INSERT INTO example(id, stem, optionA, optionB, optionC, optionD, answer, explain,
                examType, year, region, questionType, imagePath)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
            """, arguments: arguments)
        for idiomID in example.idiomIDs.sorted() {
            try db.execute(sql: "INSERT INTO example_idiom(exampleId, idiomId) VALUES (?, ?)",
                           arguments: [example.id, idiomID])
        }
    }
}
