import Foundation
import GRDB

/// P08 顶部总览（信息架构 v1.4 P08 ②）：错题总数与待复习数。
public struct MistakeOverview: Equatable, Sendable {
    public let total: Int
    public let pending: Int

    public init(total: Int, pending: Int) {
        self.total = total
        self.pending = pending
    }
}

/// P08「按成语分组」的一行（信息架构 v1.4 P08 ⑤）：成语名 + 该成语下错题数。
public struct MistakeIdiomGroup: FetchableRecord, Decodable, Equatable, Sendable, Identifiable {
    public let idiomID: String
    public let word: String
    public let pinyin: String
    public let initials: String
    public let count: Int

    public init(idiomID: String, word: String, pinyin: String, initials: String, count: Int) {
        self.idiomID = idiomID
        self.word = word
        self.pinyin = pinyin
        self.initials = initials
        self.count = count
    }

    public var id: String { idiomID }
}

/// P08「按录入时间 / 按考试类型」的一行（信息架构 v1.4 P08 ⑤）：题干 + 录入时间 + 关联成语。
/// P08 列表内均为标错的录入题，故直接取 `mistake` 行；`idiomWords` 由 `mistake_idiom` 组装。
public struct MistakeRow: Equatable, Sendable, Identifiable {
    public let id: String
    public let userExampleID: String?
    public let stem: String
    public let createdAt: String
    public let examType: String?
    public let state: String
    public let idiomWords: [String]

    public init(
        id: String,
        userExampleID: String?,
        stem: String,
        createdAt: String,
        examType: String?,
        state: String,
        idiomWords: [String]
    ) {
        self.id = id
        self.userExampleID = userExampleID
        self.stem = stem
        self.createdAt = createdAt
        self.examType = examType
        self.state = state
        self.idiomWords = idiomWords
    }
}

/// 错题列表的平铺排序口径（规范 §4.2：错题列表按 `createdAt` 倒序）。
public enum MistakeRowOrder: Int, CaseIterable, Sendable {
    /// 按录入时间倒序。
    case byCreatedAt
    /// 按考试类型聚拢，类型内仍按录入时间倒序；未填（NULL）排最后。
    case byExamType
}

/// P09 顶部信息（信息架构 v1.4 P09 ①②）：页名取成语名、释义一行取 `sense`。
public struct CapturedIdiomInfo: FetchableRecord, Decodable, Equatable, Sendable {
    public let id: String
    public let word: String
    public let sense: String

    public init(id: String, word: String, sense: String) {
        self.id = id
        self.word = word
        self.sense = sense
    }
}

/// P09 的一行（信息架构 v1.4 P09 ③）：该成语下的录入题，标错的带错题状态。
/// 以 `user_example` 为左表，故未标错的录入题同样在列。
public struct IdiomCapturedExample: Equatable, Sendable, Identifiable {
    public let id: String
    public let stem: String
    public let createdAt: String
    public let isMistake: Bool
    /// 该录入题当前错题记录的掌握状态；未标错或错题记录已删除时为 nil。
    public let mistakeState: String?
    /// 标错时填的错因，可空。
    public let reason: String?

    public init(
        id: String,
        stem: String,
        createdAt: String,
        isMistake: Bool,
        mistakeState: String?,
        reason: String?
    ) {
        self.id = id
        self.stem = stem
        self.createdAt = createdAt
        self.isMistake = isMistake
        self.mistakeState = mistakeState
        self.reason = reason
    }
}

/// `mistake` / `mistake_idiom` / `user_example` / `user_example_idiom` 只读查询（P08 / P09）。
///
/// 查询口径按规范 §4.2：错题按 `mistake_idiom` 归到成语，录入题按 `user_example_idiom`
/// 归到成语，两者不可混用；列表一律 `createdAt` 倒序并带 id 次级排序保证稳定。
/// 只读：不改表结构、不写任何用户表。
public enum MistakeLibrary {
    /// 待复习状态的库内字面值（规范 §4.1，保存时一律写入该值）。
    public static let pendingState = "待复习"

    /// 错题总数与待复习数；空表返回两个 0。
    public static func overview(_ db: Database) throws -> MistakeOverview {
        let total = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM mistake") ?? 0
        let pending = try Int.fetchOne(
            db,
            sql: "SELECT COUNT(*) FROM mistake WHERE state = ?",
            arguments: [pendingState]
        ) ?? 0
        return MistakeOverview(total: total, pending: pending)
    }

    /// 按成语分组的错题计数（P08 ⑤）：错题数降序，同数按拼音首字母与词面升序。
    /// 未关联任何成语的错题不出现在本视图，仍计入总数。
    public static func idiomGroups(_ db: Database) throws -> [MistakeIdiomGroup] {
        try MistakeIdiomGroup.fetchAll(db, sql: """
            SELECT idiom.id AS idiomID, idiom.word, idiom.pinyin, idiom.initials,
                   COUNT(*) AS count
            FROM mistake_idiom
            JOIN idiom ON idiom.id = mistake_idiom.idiomId
            GROUP BY mistake_idiom.idiomId
            ORDER BY COUNT(*) DESC, idiom.initials, idiom.word
            """)
    }

    /// 错题平铺列表（P08 时间 / 考试类型视图），关联成语一次性批量组装。
    public static func rows(_ db: Database, order: MistakeRowOrder) throws -> [MistakeRow] {
        // 拼进 SQL 的只有下面两个常量分支，取值来自闭合枚举，无外部输入。
        let orderBy: String
        switch order {
        case .byCreatedAt:
            orderBy = "mistake.createdAt DESC, mistake.id"
        case .byExamType:
            orderBy = "mistake.examType IS NULL, mistake.examType, mistake.createdAt DESC, mistake.id"
        }
        let heads = try MistakeRowHead.fetchAll(db, sql: """
            SELECT id, userExampleId, stem, createdAt, examType, state
            FROM mistake
            ORDER BY \(orderBy)
            """)
        let words = try idiomWords(byMistake: db)
        return heads.map { head in
            MistakeRow(
                id: head.id,
                userExampleID: head.userExampleId,
                stem: head.stem,
                createdAt: head.createdAt,
                examType: head.examType,
                state: head.state,
                idiomWords: words[head.id] ?? []
            )
        }
    }

    /// P09 顶部信息（①页名 + ②释义一行）；成语已不在当前题库时返回 nil，页面按「没有找到这个成语」处理。
    public static func capturedIdiom(_ db: Database, idiomID: String) throws -> CapturedIdiomInfo? {
        try CapturedIdiomInfo.fetchOne(db, sql: """
            SELECT id, word, sense
            FROM idiom
            WHERE id = ?
            """, arguments: [idiomID])
    }

    /// 某成语下的全部录入题（P09 ③），按录入时间倒序。
    ///
    /// 一道录入题理论上可挂多条错题记录（删除错题后重新标错），此处取 `createdAt`
    /// 最新的一条作为展示状态，保证一行一题。
    public static func capturedExamples(_ db: Database, idiomID: String) throws -> [IdiomCapturedExample] {
        let heads = try CapturedHead.fetchAll(db, sql: """
            SELECT user_example.id, user_example.stem, user_example.createdAt, user_example.isMistake
            FROM user_example
            JOIN user_example_idiom ON user_example_idiom.userExampleId = user_example.id
            WHERE user_example_idiom.idiomId = ?
            ORDER BY user_example.createdAt DESC, user_example.id
            """, arguments: [idiomID])
        let mistakes = try MistakeStamp.fetchAll(db, sql: """
            SELECT mistake.userExampleId, mistake.state, mistake.reason
            FROM mistake
            JOIN user_example_idiom ON user_example_idiom.userExampleId = mistake.userExampleId
            WHERE user_example_idiom.idiomId = ? AND mistake.userExampleId IS NOT NULL
            ORDER BY mistake.createdAt DESC, mistake.id
            """, arguments: [idiomID])
        var latest: [String: MistakeStamp] = [:]
        var seen: Set<String> = []
        for stamp in mistakes where seen.insert(stamp.userExampleId).inserted {
            latest[stamp.userExampleId] = stamp
        }
        return heads.map { head in
            let stamp = latest[head.id]
            return IdiomCapturedExample(
                id: head.id,
                stem: head.stem,
                createdAt: head.createdAt,
                isMistake: head.isMistake,
                mistakeState: stamp?.state,
                reason: stamp?.reason
            )
        }
    }

    /// 单条录入题详情（P10）；id 不存在返回 nil，页面按「这道题已不在本机」处理。
    ///
    /// 一道题可挂多条错题记录（删除后重新标错），状态取 `createdAt` 最新的一条，与 P09 同一口径。
    public static func userExample(_ db: Database, id: String) throws -> UserExampleDetail? {
        guard let head = try ExampleHead.fetchOne(db, sql: """
            SELECT id, stem, optionA, optionB, optionC, optionD, myAnswer, isMistake,
                   examType, year, imagePath, createdAt
            FROM user_example
            WHERE id = ?
            """, arguments: [id]) else { return nil }
        let idioms = try LinkedIdiom.fetchAll(db, sql: """
            SELECT idiom.id, idiom.word
            FROM user_example_idiom
            JOIN idiom ON idiom.id = user_example_idiom.idiomId
            WHERE user_example_idiom.userExampleId = ?
            ORDER BY user_example_idiom.rowid, idiom.word
            """, arguments: [id])
        let stamp = try ExampleMistakeStamp.fetchOne(db, sql: """
            SELECT state, reason, tagNames
            FROM mistake
            WHERE userExampleId = ?
            ORDER BY createdAt DESC, id
            LIMIT 1
            """, arguments: [id])
        return UserExampleDetail(
            id: head.id,
            stem: head.stem,
            options: [head.optionA, head.optionB, head.optionC, head.optionD],
            myAnswer: head.myAnswer,
            isMistake: head.isMistake,
            examType: head.examType,
            year: head.year,
            imagePath: head.imagePath,
            createdAt: head.createdAt,
            idioms: idioms,
            mistakeState: stamp?.state,
            reason: stamp?.reason,
            tagNames: decodeTagNames(stamp?.tagNames)
        )
    }

    /// `mistake.tagNames` 是 JSON 数组文本；解不出当没有标签，不让整页读失败。
    private static func decodeTagNames(_ raw: String?) -> [String] {
        guard let raw, let data = raw.data(using: .utf8) else { return [] }
        return (try? JSONDecoder().decode([String].self, from: data)) ?? []
    }

    private struct ExampleHead: FetchableRecord, Decodable {
        let id: String
        let stem: String
        let optionA: String?
        let optionB: String?
        let optionC: String?
        let optionD: String?
        let myAnswer: String?
        let isMistake: Bool
        let examType: String?
        let year: Int?
        let imagePath: String?
        let createdAt: String
    }

    private struct ExampleMistakeStamp: FetchableRecord, Decodable {
        let state: String
        let reason: String?
        let tagNames: String
    }

    private static func idiomWords(byMistake db: Database) throws -> [String: [String]] {
        let pairs: [IdiomWordPair] = try IdiomWordPair.fetchAll(db, sql: """
            SELECT mistake_idiom.mistakeId AS mistakeID, idiom.word
            FROM mistake_idiom
            JOIN idiom ON idiom.id = mistake_idiom.idiomId
            ORDER BY mistake_idiom.mistakeId, idiom.word
            """)
        var grouped: [String: [String]] = [:]
        for pair in pairs {
            grouped[pair.mistakeID, default: []].append(pair.word)
        }
        return grouped
    }

    private struct MistakeRowHead: FetchableRecord, Decodable {
        let id: String
        let userExampleId: String?
        let stem: String
        let createdAt: String
        let examType: String?
        let state: String
    }

    private struct CapturedHead: FetchableRecord, Decodable {
        let id: String
        let stem: String
        let createdAt: String
        let isMistake: Bool
    }

    private struct MistakeStamp: FetchableRecord, Decodable {
        let userExampleId: String
        let state: String
        let reason: String?
    }

    private struct IdiomWordPair: FetchableRecord, Decodable {
        let mistakeID: String
        let word: String
    }
}

/// P10 ⑧：录入题关联的成语（`user_example_idiom` 关联顺序即写入顺序，表无 order 列）。
public struct LinkedIdiom: FetchableRecord, Decodable, Equatable, Sendable, Identifiable {
    public let id: String
    public let word: String

    public init(id: String, word: String) {
        self.id = id
        self.word = word
    }
}

/// P10 录入题详情（信息架构 v1.4 P10 ②–⑨）。
///
/// 区块 ⑥「正确答案 + 一句解析」本版不做：`user_example` / `mistake` 无答案与解析列，
/// `answer` / `explain` 只存在于系统 `example`（2026-11-22 用户拍板，见信息架构 v1.4 版内修订）。
public struct UserExampleDetail: Equatable, Sendable {
    public let id: String
    public let stem: String
    /// A–D 四个选项，OCR 缺项为 nil。
    public let options: [String?]
    public let myAnswer: String?
    public let isMistake: Bool
    public let examType: String?
    public let year: Int?
    /// 相对路径 `images/<id>.jpg`；未存或导入备份后置空为 nil。
    public let imagePath: String?
    public let createdAt: String
    public let idioms: [LinkedIdiom]
    /// 当前错题记录的掌握状态；删除错题后为 nil。
    public let mistakeState: String?
    public let reason: String?
    public let tagNames: [String]

    public init(
        id: String,
        stem: String,
        options: [String?],
        myAnswer: String?,
        isMistake: Bool,
        examType: String?,
        year: Int?,
        imagePath: String?,
        createdAt: String,
        idioms: [LinkedIdiom],
        mistakeState: String?,
        reason: String?,
        tagNames: [String]
    ) {
        self.id = id
        self.stem = stem
        self.options = options
        self.myAnswer = myAnswer
        self.isMistake = isMistake
        self.examType = examType
        self.year = year
        self.imagePath = imagePath
        self.createdAt = createdAt
        self.idioms = idioms
        self.mistakeState = mistakeState
        self.reason = reason
        self.tagNames = tagNames
    }
}

/// 读库入口：feature 层通过协议注入，不直接持有 DatabaseQueue。
/// DatabaseQueue 不可变且线程安全，故可在后台线程读库。
public final class MistakeLibraryReader: @unchecked Sendable {
    private let queue: DatabaseQueue

    public init(queue: DatabaseQueue) {
        self.queue = queue
    }

    public func overview() throws -> MistakeOverview {
        try queue.read { db in try MistakeLibrary.overview(db) }
    }

    public func idiomGroups() throws -> [MistakeIdiomGroup] {
        try queue.read { db in try MistakeLibrary.idiomGroups(db) }
    }

    public func rows(order: MistakeRowOrder) throws -> [MistakeRow] {
        try queue.read { db in try MistakeLibrary.rows(db, order: order) }
    }

    public func capturedIdiom(idiomID: String) throws -> CapturedIdiomInfo? {
        try queue.read { db in try MistakeLibrary.capturedIdiom(db, idiomID: idiomID) }
    }

    public func capturedExamples(idiomID: String) throws -> [IdiomCapturedExample] {
        try queue.read { db in try MistakeLibrary.capturedExamples(db, idiomID: idiomID) }
    }

    public func userExample(id: String) throws -> UserExampleDetail? {
        try queue.read { db in try MistakeLibrary.userExample(db, id: id) }
    }
}
