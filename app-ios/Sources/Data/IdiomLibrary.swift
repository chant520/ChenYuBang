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

/// P25 成语自测的目标行：只带判分所需的 id / 词面 / 释义，不携带全列。
public struct IdiomWordSense: Equatable, Sendable, Identifiable, FetchableRecord, Decodable {
    public let id: String
    public let word: String
    public let sense: String

    public init(id: String, word: String, sense: String) {
        self.id = id
        self.word = word
        self.sense = sense
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

    /// P25 目标成语只读投影：范围仍以 `idiom_scope` 为唯一事实源。
    public static func scopedWordSenses(_ db: Database) throws -> [IdiomWordSense] {
        try IdiomWordSense.fetchAll(db, sql: """
            SELECT idiom.id, idiom.word, idiom.sense
            FROM idiom
            JOIN (SELECT DISTINCT idiomId FROM idiom_scope) AS scope ON idiom.id = scope.idiomId
            ORDER BY idiom.id
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

    /// P25 当前考试范围内的目标成语；只读，不写学习状态或答题记录。
    public func scopedWordSenses() throws -> [IdiomWordSense] {
        try queue.read { db in try IdiomLibrary.scopedWordSenses(db) }
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

/// A6 学习状态只接受规范第 4.7 节的三个精确值。
public enum IdiomLearningStatus: String, CaseIterable, Sendable {
    case notStarted = "未学"
    case studied = "学过"
    case proficient = "已掌握"
}

/// A6 / A7 的单条读投影；无 idiom_state 行时由 Store 补默认值，不写库。
public struct IdiomLearningState: Equatable, Sendable {
    public let idiomID: String
    public let status: IdiomLearningStatus
    public let favorite: Bool

    public init(idiomID: String, status: IdiomLearningStatus, favorite: Bool) {
        self.idiomID = idiomID
        self.status = status
        self.favorite = favorite
    }
}

public enum IdiomStateError: Error, Equatable, Sendable {
    case invalidStoredStatus(String)
}

/// A6 / A7 数据层前置（规范第 4.7 节）：读不落行，两个写入方向各自单事务 upsert。
public final class IdiomStateStore: @unchecked Sendable {
    private let queue: DatabaseQueue

    public init(queue: DatabaseQueue) {
        self.queue = queue
    }

    public func state(idiomID: String) throws -> IdiomLearningState {
        try queue.read { db in
            guard let row = try Row.fetchOne(
                db, sql: "SELECT status, favorite FROM idiom_state WHERE idiomId = ?", arguments: [idiomID]
            ) else {
                return IdiomLearningState(idiomID: idiomID, status: .notStarted, favorite: false)
            }
            let rawStatus: String = row["status"]
            guard let status = IdiomLearningStatus(rawValue: rawStatus) else {
                throw IdiomStateError.invalidStoredStatus(rawStatus)
            }
            let favorite: Bool = row["favorite"]
            return IdiomLearningState(idiomID: idiomID, status: status, favorite: favorite)
        }
    }

    public func setStatus(idiomID: String, status: IdiomLearningStatus) throws {
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO idiom_state(idiomId, status, favorite)
                VALUES (?, ?, 0)
                ON CONFLICT(idiomId) DO UPDATE SET status = excluded.status
                """, arguments: [idiomID, status.rawValue])
        }
    }

    public func setFavorite(idiomID: String, favorite: Bool) throws {
        try queue.write { db in
            try db.execute(sql: """
                INSERT INTO idiom_state(idiomId, status, favorite)
                VALUES (?, ?, ?)
                ON CONFLICT(idiomId) DO UPDATE SET favorite = excluded.favorite
                """, arguments: [idiomID, IdiomLearningStatus.notStarted.rawValue, favorite])
        }
    }

    // MARK: - 阶段6包1只读聚合（只 SELECT，不建表 / 不加字段 / 不迁移）

    /// E2「已学成语数」：显式落行且状态为学过或已掌握；缺行=未学不计。
    public func countLearned() throws -> Int {
        try queue.read { db in
            try Int.fetchOne(
                db,
                sql: "SELECT COUNT(*) FROM idiom_state WHERE status IN (?, ?)",
                arguments: [IdiomLearningStatus.studied.rawValue, IdiomLearningStatus.proficient.rawValue]
            ) ?? 0
        }
    }

    /// E2「已掌握数」：仅 idiom_state 的成语自评已掌握，非错题 C9 的掌握态。
    public func countProficient() throws -> Int {
        try queue.read { db in
            try Int.fetchOne(
                db,
                sql: "SELECT COUNT(*) FROM idiom_state WHERE status = ?",
                arguments: [IdiomLearningStatus.proficient.rawValue]
            ) ?? 0
        }
    }

    /// E2「收藏数」：与 P24 列表同源过滤 favorite=1；取消收藏后 -1。
    public func countFavorite() throws -> Int {
        try queue.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM idiom_state WHERE favorite = 1") ?? 0
        }
    }

    /// P03 批量取该页状态，避免逐行 state() 的 N+1；只返回已落行，缺行由调用方投影补未学。
    public func statesForIds(_ ids: [String]) throws -> [IdiomLearningState] {
        guard !ids.isEmpty else { return [] }
        return try queue.read { db in
            let placeholders = ids.map { _ in "?" }.joined(separator: ", ")
            return try Row.fetchAll(
                db,
                sql: "SELECT idiomId, status, favorite FROM idiom_state WHERE idiomId IN (\(placeholders))",
                arguments: StatementArguments(ids)
            ).compactMap { row in
                let idiomID: String = row["idiomId"]
                let rawStatus: String = row["status"]
                guard let status = IdiomLearningStatus(rawValue: rawStatus) else { return nil }
                let favorite: Bool = row["favorite"]
                return IdiomLearningState(idiomID: idiomID, status: status, favorite: favorite)
            }
        }
    }

    /// P24 收藏列表：只取 favorite=1 且能 JOIN 到 idiom 的行，天然排除未收藏 / 缺省行。
    public func favoriteIdioms() throws -> [FavoriteIdiom] {
        try queue.read { db in
            try FavoriteIdiom.fetchAll(db, sql: """
                SELECT idiom.id AS idiomId, idiom.word, idiom.pinyin, idiom.initials, idiom.frequency,
                       idiom_state.status AS status
                FROM idiom_state
                JOIN idiom ON idiom.id = idiom_state.idiomId
                WHERE idiom_state.favorite = 1
                ORDER BY idiom.initials, idiom.word
                """)
        }
    }
}

/// P24 收藏列表一行：成语展示字段 + 该成语的 A6 状态存储串（同源 idiom_state）。
public struct FavoriteIdiom: FetchableRecord, Decodable, Equatable, Sendable, Identifiable {
    public let idiomId: String
    public let word: String
    public let pinyin: String
    public let initials: String
    public let frequency: Int
    public let status: String

    public init(idiomId: String, word: String, pinyin: String, initials: String, frequency: Int, status: String) {
        self.idiomId = idiomId
        self.word = word
        self.pinyin = pinyin
        self.initials = initials
        self.frequency = frequency
        self.status = status
    }

    public var id: String { idiomId }
}
