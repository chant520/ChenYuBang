import Foundation
import GRDB

/// P11 复习队列的一行（信息架构 v1.4 P11 ②）：题干 + 四选项 + 首次「我的答案」。
/// 正文列以 `user_example` 为事实源、`mistake` 快照兜底（开发规范第 8.2 节：
/// 历史无 `userExampleId` 的错题照常入队）；选项缺项 / 空串归 nil，`myAnswer`
/// 只保留 `A`–`D` 合法值。
public struct ReviewQueueItem: Equatable, Sendable, Identifiable {
    public let id: String
    public let stem: String
    public let options: [String?]
    public let myAnswer: String?
    public let createdAt: String

    public init(id: String, stem: String, options: [String?], myAnswer: String?, createdAt: String) {
        self.id = id
        self.stem = stem
        self.options = options
        self.myAnswer = myAnswer
        self.createdAt = createdAt
    }
}

/// 复习队列查询与结果落库（P11 / P12，开发规范第 8.2 节）。
/// 只写 `review_log` 与 `mistake.reviewCount` / `lastReviewedAt`，不改 `mistake.state`
/// （掌握状态切换属 C9 进阶，MVP 不实现）。
public enum ReviewLibrary {
    /// `review_log.result` 的两个库内字面值（第 8.2 节：不同记「对」、相同记「错」）。
    public static let resultRight = "对"
    public static let resultWrong = "错"

    /// 复习队列：全部 `state = 待复习` 的错题，按 `createdAt` 倒序（最近录入优先，假设 5）、
    /// id 次级稳定排序。`idiomID` 非空时为单成语模式：按 `mistake_idiom` 关联判定，
    /// 命中任一勾选成语即入队。
    public static func queue(_ db: Database, idiomID: String? = nil) throws -> [ReviewQueueItem] {
        var sql = """
            SELECT mistake.id,
                   COALESCE(NULLIF(user_example.stem, ''), mistake.stem) AS stem,
                   COALESCE(NULLIF(user_example.optionA, ''), mistake.optionA) AS optionA,
                   COALESCE(NULLIF(user_example.optionB, ''), mistake.optionB) AS optionB,
                   COALESCE(NULLIF(user_example.optionC, ''), mistake.optionC) AS optionC,
                   COALESCE(NULLIF(user_example.optionD, ''), mistake.optionD) AS optionD,
                   COALESCE(NULLIF(user_example.myAnswer, ''), mistake.myAnswer) AS myAnswer,
                   mistake.createdAt
            FROM mistake
            LEFT JOIN user_example ON user_example.id = mistake.userExampleId
            WHERE mistake.state = ?
            """
        var arguments: [String] = [MistakeLibrary.pendingState]
        if let idiomID {
            sql += """
                AND EXISTS (
                    SELECT 1 FROM mistake_idiom
                    WHERE mistake_idiom.mistakeId = mistake.id AND mistake_idiom.idiomId = ?
                )
                """
            arguments.append(idiomID)
        }
        sql += "\nORDER BY mistake.createdAt DESC, mistake.id"
        return try QueueHead.fetchAll(db, sql: sql, arguments: StatementArguments(arguments)).map { head in
            ReviewQueueItem(
                id: head.id,
                stem: head.stem,
                options: [normalized(head.optionA), normalized(head.optionB),
                          normalized(head.optionC), normalized(head.optionD)],
                myAnswer: normalized(head.myAnswer).flatMap { AnswerLetters.of($0) },
                createdAt: head.createdAt
            )
        }
    }

    /// 单成语待复习数（决策 H：P05「复习这个成语」无内容时置灰）。
    public static func pendingCount(_ db: Database, idiomID: String) throws -> Int {
        try Int.fetchOne(db, sql: """
            SELECT COUNT(*)
            FROM mistake
            WHERE mistake.state = ?
              AND EXISTS (
                  SELECT 1 FROM mistake_idiom
                  WHERE mistake_idiom.mistakeId = mistake.id AND mistake_idiom.idiomId = ?
              )
            """, arguments: [MistakeLibrary.pendingState, idiomID]) ?? 0
    }

    /// 本轮涉及成语（P12 ④）：队列内错题命中的成语去重，按词面升序，每个可点进 P05。
    public static func idiomsInRound(_ db: Database, mistakeIDs: [String]) throws -> [LinkedIdiom] {
        guard !mistakeIDs.isEmpty else { return [] }
        let placeholders = mistakeIDs.map { _ in "?" }.joined(separator: ", ")
        return try LinkedIdiom.fetchAll(db, sql: """
            SELECT DISTINCT idiom.id, idiom.word
            FROM mistake_idiom
            JOIN idiom ON idiom.id = mistake_idiom.idiomId
            WHERE mistake_idiom.mistakeId IN (\(placeholders))
            ORDER BY idiom.word
            """, arguments: StatementArguments(mistakeIDs))
    }

    /// 复习结果落库（第 8.2 节版内修订）：一行 `review_log`，并在同一事务内
    /// `mistake.reviewCount` 加一、`lastReviewedAt` 刷新；错题已不在本机时抛错，
    /// 不落孤儿 `review_log` 行。`reviewedAt` 为 ISO8601 UTC 秒级串（与 `UserExampleSaver` 同口径）。
    public static func record(_ db: Database, mistakeID: String, result: String, reviewedAt: String) throws {
        try db.inTransaction {
            let exists = try Bool.fetchOne(
                db, sql: "SELECT 1 FROM mistake WHERE id = ?", arguments: [mistakeID]
            ) ?? false
            guard exists else { throw ReviewError.mistakeNotFound(mistakeID) }
            try db.execute(
                sql: "INSERT INTO review_log (id, mistakeId, result, at) VALUES (?, ?, ?, ?)",
                arguments: [UUID().uuidString, mistakeID, result, reviewedAt]
            )
            try db.execute(
                sql: "UPDATE mistake SET reviewCount = reviewCount + 1, lastReviewedAt = ? WHERE id = ?",
                arguments: [reviewedAt, mistakeID]
            )
            return .commit
        }
    }

    public enum ReviewError: Error, Equatable {
        case mistakeNotFound(String)
    }

    /// 空串归 nil；`mistake.optionA` 等列非空约束但保存时缺项写空串。
    private static func normalized(_ value: String?) -> String? {
        guard let value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return value
    }
}

/// `myAnswer` 字面值校验（合法取值即 A–D，与 `UserExampleSaver` 保存校验同口径）。
enum AnswerLetters {
    static let letters = ["A", "B", "C", "D"]

    static func of(_ value: String) -> String? {
        letters.contains(value) ? value : nil
    }
}

/// 复习读库与落库入口（P11 / P12）：feature 层通过实例注入，不直接持有 DatabaseQueue。
public final class ReviewLibraryService: @unchecked Sendable {
    private let queue: DatabaseQueue

    public init(queue: DatabaseQueue) {
        self.queue = queue
    }

    public func queue(idiomID: String? = nil) throws -> [ReviewQueueItem] {
        try queue.read { db in try ReviewLibrary.queue(db, idiomID: idiomID) }
    }

    public func pendingCount(idiomID: String) throws -> Int {
        try queue.read { db in try ReviewLibrary.pendingCount(db, idiomID: idiomID) }
    }

    public func idiomsInRound(mistakeIDs: [String]) throws -> [LinkedIdiom] {
        try queue.read { db in try ReviewLibrary.idiomsInRound(db, mistakeIDs: mistakeIDs) }
    }

    public func record(mistakeID: String, result: String, reviewedAt: String) throws {
        try queue.write { db in
            try ReviewLibrary.record(db, mistakeID: mistakeID, result: result, reviewedAt: reviewedAt)
        }
    }
}
