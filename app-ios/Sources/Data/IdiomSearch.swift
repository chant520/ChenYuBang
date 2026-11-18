import Foundation
import GRDB

/// 成语 FTS 检索与重建（规范 §4.2 / §4.6）。接受 GRDB Database，供搜索 DAO 与导入引擎调用。
///
/// 四种输入：完整成语 / 单字 / 全拼 / 首字母。含汉字走中文路径，纯 ASCII 字母走拼音路径。
/// 单字走「前缀 LIKE + FTS 组合」，不做分词库。pinyin 列存去空格连写规范化值，查询时全拼也去空格。
/// 结果 JOIN 回 idiom，ORDER BY word，分页 50（§4.2）；查无结果返回空数组，文案由上层给。
/// MATCH 串里的用户输入一律用双引号包裹成 FTS phrase（内部双引号翻倍），防注入。
public enum IdiomSearch {
    public static let defaultLimit = 50

    /// 整表重建 idiom_fts（供 0.5 导入引擎调用）。只重建内容表 FTS，绝不触碰用户表（§4.6）。
    public static func rebuildIdiomFts(_ db: Database) throws {
        try db.execute(sql: "DELETE FROM idiom_fts")
        try db.execute(sql: """
            INSERT INTO idiom_fts(idiomid, word, pinyin, initials, sense)
            SELECT id, word, replace(pinyin, ' ', ''), initials, sense FROM idiom
            """)
    }

    public static func searchIdioms(
        _ db: Database,
        query: String,
        limit: Int = defaultLimit
    ) throws -> [IdiomRecord] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.contains("\u{0000}") { return [] }
        if containsHan(trimmed) {
            let phrase = ftsPhrase(trimmed)
            if trimmed.count == 1 {
                // 单字：前缀 LIKE + FTS(word 前缀 OR sense 精确) 组合，按 id 去重后合并
                let byPrefix = try IdiomRecord.fetchAll(
                    db,
                    sql: """
                        SELECT idiom.* FROM idiom JOIN idiom_fts ON idiom.id = idiom_fts.idiomid
                        WHERE idiom.word LIKE ? ORDER BY idiom.word LIMIT ?
                        """,
                    arguments: [trimmed + "%", limit]
                )
                let byFts = try ftsMatch(db, match: "word:\(phrase)* OR sense:\(phrase)", limit: limit)
                return mergeById(byPrefix, byFts, limit: limit)
            }
            // 完整成语（≥2 字）：FTS MATCH word/sense
            return try ftsMatch(db, match: "word:\(phrase) OR sense:\(phrase)", limit: limit)
        }
        // 纯 ASCII：全拼去空格连写命中 pinyin，首字母命中 initials，前缀支持部分输入
        let normalized = trimmed.replacingOccurrences(of: " ", with: "").lowercased()
        let phrase = ftsPhrase(normalized)
        return try ftsMatch(db, match: "pinyin:\(phrase)* OR initials:\(phrase)*", limit: limit)
    }

    private static func ftsMatch(_ db: Database, match: String, limit: Int) throws -> [IdiomRecord] {
        return try IdiomRecord.fetchAll(
            db,
            sql: """
                SELECT idiom.* FROM idiom
                JOIN idiom_fts ON idiom.id = idiom_fts.idiomid
                WHERE idiom_fts MATCH ?
                ORDER BY idiom.word LIMIT ?
                """,
            arguments: [match, limit]
        )
    }

    private static func containsHan(_ text: String) -> Bool {
        return text.unicodeScalars.contains { scalar in
            let value = scalar.value
            return (value >= 0x4E00 && value <= 0x9FFF) ||
                (value >= 0x3400 && value <= 0x4DBF) ||
                (value >= 0xF900 && value <= 0xFAFF)
        }
    }

    private static func ftsPhrase(_ token: String) -> String {
        let escaped = token.replacingOccurrences(of: "\"", with: "\"\"")
        return "\"\(escaped)\""
    }

    private static func mergeById(
        _ lhs: [IdiomRecord],
        _ rhs: [IdiomRecord],
        limit: Int
    ) -> [IdiomRecord] {
        var seen = Set<String>()
        var merged: [IdiomRecord] = []
        for record in lhs + rhs where seen.insert(record.id).inserted {
            merged.append(record)
        }
        merged.sort { $0.word < $1.word }
        return Array(merged.prefix(limit))
    }
}
