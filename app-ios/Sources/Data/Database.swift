import Foundation
import GRDB

/// 10 张表，与 shared/schema/migrations.json 的 v1 一致（规范 §4.5.3）。
/// 迁移只增不删；每版都要有正常路径 + 中断回滚用例。
public enum ChenYuBangDatabase {
    public static func makeMigrator() -> DatabaseMigrator {
        var migrator = DatabaseMigrator()

        migrator.registerMigration("v1") { db in
            try db.create(table: "idiom") { t in
                t.primaryKey("id", .text)
                t.column("word", .text).notNull()
                t.column("pinyin", .text).notNull()
                t.column("initials", .text).notNull()
                t.column("sense", .text).notNull()
                t.column("origin", .text)
                t.column("color", .text).notNull()
                t.column("pitfalls", .text).notNull().defaults(to: "[]")
                t.column("synonyms", .text).notNull().defaults(to: "[]")
                t.column("antonyms", .text).notNull().defaults(to: "[]")
                t.column("frequency", .integer).notNull()
            }
            try db.create(index: "idx_idiom_word", on: "idiom", columns: ["word"])
            try db.create(index: "idx_idiom_initials", on: "idiom", columns: ["initials"])

            // FTS5 全文检索虚表（规范 §4.2），属 v1 基线。独立表（非 external-content），
            // 导入时由 IdiomSearch.rebuildIdiomFts 整表重建（§4.6），不触碰用户表。
            // idiomid UNINDEXED 只用于 JOIN 回 idiom；pinyin 列存去空格连写规范化值。
            try db.execute(sql: """
                CREATE VIRTUAL TABLE idiom_fts USING fts5(
                    idiomid UNINDEXED, word, pinyin, initials, sense
                )
                """)

            try db.create(table: "exam_scope") { t in
                t.primaryKey("id", .text)
                t.column("type", .text).notNull()
                t.column("province", .text)
                t.column("enabled", .boolean).notNull()
            }

            try db.create(table: "idiom_scope") { t in
                t.column("idiomId", .text).notNull()
                t.column("scopeId", .text).notNull()
                t.primaryKey(["idiomId", "scopeId"])
            }

            try db.create(table: "example") { t in
                t.primaryKey("id", .text)
                t.column("stem", .text).notNull()
                t.column("optionA", .text).notNull()
                t.column("optionB", .text).notNull()
                t.column("optionC", .text).notNull()
                t.column("optionD", .text).notNull()
                t.column("answer", .text).notNull()
                t.column("explain", .text).notNull()
                t.column("examType", .text).notNull()
                t.column("year", .integer).notNull()
                t.column("region", .text).notNull()
                t.column("questionType", .text).notNull()
                t.column("imagePath", .text)
            }

            try db.create(table: "example_idiom") { t in
                t.column("exampleId", .text).notNull()
                t.column("idiomId", .text).notNull()
                t.primaryKey(["exampleId", "idiomId"])
            }

            try db.create(table: "mistake") { t in
                t.primaryKey("id", .text)
                t.column("imagePath", .text)
                t.column("ocrText", .text)
                t.column("stem", .text).notNull()
                t.column("optionA", .text).notNull()
                t.column("optionB", .text).notNull()
                t.column("optionC", .text).notNull()
                t.column("optionD", .text).notNull()
                t.column("myAnswer", .text)
                t.column("reason", .text)
                t.column("tagNames", .text).notNull().defaults(to: "[]")
                t.column("examType", .text)
                t.column("year", .integer)
                t.column("region", .text)
                t.column("state", .text).notNull()
                t.column("createdAt", .text).notNull()
                t.column("lastReviewedAt", .text)
                t.column("reviewCount", .integer).notNull().defaults(to: 0)
            }
            try db.create(index: "idx_mistake_state", on: "mistake", columns: ["state"])
            try db.create(index: "idx_mistake_created", on: "mistake", columns: ["createdAt"])

            try db.create(table: "mistake_idiom") { t in
                t.column("mistakeId", .text).notNull()
                t.column("idiomId", .text).notNull()
                t.primaryKey(["mistakeId", "idiomId"])
            }

            try db.create(table: "review_log") { t in
                t.primaryKey("id", .text)
                t.column("mistakeId", .text).notNull()
                t.column("result", .text).notNull()
                t.column("at", .text).notNull()
            }
            try db.create(index: "idx_review_mistake", on: "review_log", columns: ["mistakeId"])

            try db.create(table: "idiom_state") { t in
                t.primaryKey("idiomId", .text)
                t.column("status", .text).notNull()
                t.column("favorite", .boolean).notNull().defaults(to: false)
            }

            try db.create(table: "settings") { t in
                t.primaryKey("key", .text)
                t.column("value", .text).notNull()
            }
        }

        return migrator
    }
}
