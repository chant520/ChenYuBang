import Foundation
import GRDB

/// 成语库浏览行（P03）：只带列表需要的字段，避免页面直接依赖 idiom 全列。
public struct LibraryIdiom: Equatable, Sendable, Identifiable, FetchableRecord, Decodable {
    public let id: String
    public let word: String
    public let pinyin: String
    public let initials: String
    public let frequency: Int

    public init(id: String, word: String, pinyin: String, initials: String, frequency: Int) {
        self.id = id
        self.word = word
        self.pinyin = pinyin
        self.initials = initials
        self.frequency = frequency
    }

    /// 字母索引归组键：拼音首字母大写；空值归到 `#`（与 Android LibraryIdiom 一致）。
    public var letter: String {
        let first = initials.prefix(1).uppercased()
        return first.isEmpty ? "#" : first
    }
}

/// 成语库只读查询：JOIN `idiom_scope` 即得「按考试范围过滤后的全量」。
///
/// 范围口径由 `pack_scope` / `PackContent.refreshScopes` 重建的 `idiom_scope` 决定，
/// 这里不重复实现过滤规则；省考勾选时省份默认全选、省考内容正常显示（信息架构 v1.4）。
/// 只读：不改表结构、不写任何用户表。
public enum IdiomLibrary {
    public static func load(_ db: Database) throws -> [LibraryIdiom] {
        try LibraryIdiom.fetchAll(db, sql: """
            SELECT idiom.id, idiom.word, idiom.pinyin, idiom.initials, idiom.frequency
            FROM idiom
            JOIN (SELECT DISTINCT idiomId FROM idiom_scope) AS scope ON idiom.id = scope.idiomId
            ORDER BY idiom.initials, idiom.word
            """)
    }
}

/// 读库入口：feature 层通过协议注入，不直接持有 DatabaseQueue。
/// DatabaseQueue 不可变且线程安全，故可在后台线程读库。
public final class IdiomLibraryReader: @unchecked Sendable {
    private let queue: DatabaseQueue

    public init(queue: DatabaseQueue) {
        self.queue = queue
    }

    public func load() throws -> [LibraryIdiom] {
        try queue.read { db in try IdiomLibrary.load(db) }
    }

    /// P20 匹配候选专用：全库成语（含 synonyms / frequency），不按考试范围过滤。
    /// 只读；synonyms 列是 JSON 字符串，解析在应用层完成，失败降级空数组。
    public func allIdiomsForMatch() throws -> [IdiomRecord] {
        try queue.read { db in
            try IdiomRecord.fetchAll(db, sql: "SELECT * FROM idiom ORDER BY word")
        }
    }

    /// P05 详情只读查询（开发规范 §4.2）：按 uuid 取完整成语行，缺行返回 nil。
    public func detail(id: String) throws -> IdiomRecord? {
        try queue.read { db in
            try IdiomRecord.fetchOne(db, sql: "SELECT * FROM idiom WHERE id = ?", arguments: [id])
        }
    }

    /// P04 搜索只读查询：复用 IdiomSearch 的四种输入 FTS 路径（完整成语 / 单字 / 全拼 / 首字母）。
    public func search(query: String, limit: Int = IdiomSearch.defaultLimit) throws -> [IdiomRecord] {
        try queue.read { db in
            try IdiomSearch.searchIdioms(db, query: query, limit: limit)
        }
    }
}
