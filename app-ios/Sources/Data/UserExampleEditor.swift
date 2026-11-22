import Foundation
import GRDB

/// P10 编辑态（工作包 2.8 / C8）的保存输入。
///
/// 只含可改字段：原图与「是否做错」不可改（信息架构 v1.4 版内修订第 ② 条），
/// `ocrText` 是识别原文、不随编辑改写；`region` 本版无录入入口，保持既有值。
public struct UserExampleEditInput: Equatable, Sendable {
    /// `user_example.id`。
    public let id: String
    public let stem: String
    /// A–D 四个选项，缺项为 nil（与 `UserExampleDetail.options` 同一形状）。
    public let options: [String?]
    /// `A` / `B` / `C` / `D`，未填为 nil。
    public let myAnswer: String?
    public let examType: String?
    public let year: Int?
    /// 错因只落在 `mistake`；未标错的录入题无错题行，编辑态不出该栏。
    public let reason: String?
    public let tagNames: [String]
    /// 归属全集，按页面顺序整批重写（`user_example_idiom` 无 order 列，rowid 即展示顺序）。
    public let idioms: [LinkedIdiom]

    public init(
        id: String,
        stem: String,
        options: [String?],
        myAnswer: String?,
        examType: String?,
        year: Int?,
        reason: String? = nil,
        tagNames: [String] = [],
        idioms: [LinkedIdiom]
    ) {
        self.id = id
        self.stem = stem
        self.options = options
        self.myAnswer = myAnswer
        self.examType = examType
        self.year = year
        self.reason = reason
        self.tagNames = tagNames
        self.idioms = idioms
    }
}

/// 编辑与删除的失败原因；校验口径与 `CaptureSaveError` 一致，删除不存在的题单列。
public enum UserExampleEditError: Error, Equatable, Sendable {
    case emptyStem
    case noIdioms
    case invalidAnswer
    case invalidTag
    case notFound
}

/// P10 的编辑与删除落库（工作包 2.8，写库口径见开发规范 v1.7 §4.1 与 §8.1）。
///
/// `user_example` 是正文事实源，`mistake` 的同名列是快照：编辑在同一事务内刷新快照，
/// 否则 P08 列表（读 `mistake`）与 P09 / P10（读 `user_example`）会在编辑后漂移。
/// 删除分两支：标错的只移出错题本、未标错的删整题连本机原图。
public final class UserExampleEditor: @unchecked Sendable {
    private let queue: DatabaseQueue
    /// 本机数据根目录（`images/<id>.jpg` 相对路径的解析基准，与 P10 读图同一口径）。
    private let dataDirectory: URL

    public init(queue: DatabaseQueue, dataDirectory: URL) {
        self.queue = queue
        self.dataDirectory = dataDirectory
    }

    /// 一个事务：写 `user_example` 正文列 + 刷新该题下全部 `mistake` 快照正文 +
    /// 整批重写 `user_example_idiom` 与 `mistake_idiom`（至少一条）+ 直写 `mistake.reason` / `tagNames`。
    /// `mistake.state` / `createdAt` / `lastReviewedAt` / `reviewCount` 不因编辑改动。
    public func saveEdit(_ input: UserExampleEditInput) throws {
        let idioms = deduplicated(input.idioms)
        guard !idioms.isEmpty, idioms.allSatisfy({ !$0.id.isEmpty }) else {
            throw UserExampleEditError.noIdioms
        }
        let stem = input.stem.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !stem.isEmpty else {
            throw UserExampleEditError.emptyStem
        }
        if let answer = input.myAnswer, !["A", "B", "C", "D"].contains(answer) {
            throw UserExampleEditError.invalidAnswer
        }
        guard input.tagNames.allSatisfy({ (1...8).contains($0.count) }) else {
            throw UserExampleEditError.invalidTag
        }
        let options = padded(input.options)
        try queue.write { db in
            try requireUserExample(db, id: input.id)
            try db.execute(sql: """
                UPDATE user_example
                SET stem = ?, optionA = ?, optionB = ?, optionC = ?, optionD = ?,
                    myAnswer = ?, examType = ?, year = ?
                WHERE id = ?
                """, arguments: [
                stem, options[0], options[1], options[2], options[3],
                input.myAnswer, input.examType, input.year, input.id
            ])
            try db.execute(
                sql: "DELETE FROM user_example_idiom WHERE userExampleId = ?",
                arguments: [input.id]
            )
            for idiom in idioms {
                try db.execute(
                    sql: "INSERT INTO user_example_idiom(userExampleId, idiomId) VALUES (?, ?)",
                    arguments: [input.id, idiom.id]
                )
            }
            // 快照正文与 reason / tagNames 一并刷新；未标错的题无错题行，两条语句影响 0 行。
            // `mistake` 的四个选项列是 NOT NULL（v1 基线），缺项按 `UserExampleSaver` 同一口径写空串。
            try db.execute(sql: """
                UPDATE mistake
                SET stem = ?, optionA = ?, optionB = ?, optionC = ?, optionD = ?,
                    myAnswer = ?, examType = ?, year = ?, reason = ?, tagNames = ?
                WHERE userExampleId = ?
                """, arguments: [
                stem, options[0] ?? "", options[1] ?? "", options[2] ?? "", options[3] ?? "",
                input.myAnswer, input.examType, input.year, trimmed(input.reason),
                tagJSON(input.tagNames), input.id
            ])
            try db.execute(sql: """
                DELETE FROM mistake_idiom WHERE mistakeId IN
                    (SELECT id FROM mistake WHERE userExampleId = ?)
                """, arguments: [input.id])
            for idiom in idioms {
                try db.execute(sql: """
                    INSERT INTO mistake_idiom(mistakeId, idiomId)
                    SELECT mistake.id, ? FROM mistake WHERE mistake.userExampleId = ?
                    """, arguments: [idiom.id, input.id])
            }
        }
    }

    /// 删除错题（标错的录入题）：删该题下全部 `mistake` 行及其 `mistake_idiom` / `review_log`，
    /// 并把 `user_example.isMistake` 归位 false——错题即 `isMistake=true` 的子集（§4.1），
    /// 只删标记行不归位会让本页继续显示「错题」徽标与删除错题入口。
    /// 录入题本身与本机原图都不动，题仍可在对应成语的「我的录入题」里看到。
    public func deleteMistake(userExampleID: String) throws {
        try queue.write { db in
            try requireUserExample(db, id: userExampleID)
            try db.execute(sql: """
                DELETE FROM mistake_idiom WHERE mistakeId IN
                    (SELECT id FROM mistake WHERE userExampleId = ?)
                """, arguments: [userExampleID])
            try db.execute(sql: """
                DELETE FROM review_log WHERE mistakeId IN
                    (SELECT id FROM mistake WHERE userExampleId = ?)
                """, arguments: [userExampleID])
            try db.execute(
                sql: "DELETE FROM mistake WHERE userExampleId = ?",
                arguments: [userExampleID]
            )
            try db.execute(
                sql: "UPDATE user_example SET isMistake = 0 WHERE id = ?",
                arguments: [userExampleID]
            )
        }
    }

    /// 删除未标错的录入题：删 `user_example` 与 `user_example_idiom`，再按行内 `imagePath`
    /// 删本机原图（开发规范 §8.1：该分支无错题行可删）。库内先提交，图片删不掉只留孤儿文件，
    /// 不回滚已提交的删除。
    public func deleteUserExample(id: String) throws {
        let imagePath: String? = try queue.write { db in
            let head = try HeadRow.fetchOne(
                db, sql: "SELECT imagePath FROM user_example WHERE id = ?", arguments: [id]
            )
            guard let head else { throw UserExampleEditError.notFound }
            try db.execute(
                sql: "DELETE FROM user_example WHERE id = ?",
                arguments: [id]
            )
            try db.execute(
                sql: "DELETE FROM user_example_idiom WHERE userExampleId = ?",
                arguments: [id]
            )
            return head.imagePath
        }
        guard let imagePath else { return }
        try? FileManager.default.removeItem(
            at: dataDirectory.appendingPathComponent(imagePath)
        )
    }

    /// 归属去重，保留页面顺序（重复 id 会撞 `mistake_idiom` 复合主键）。
    private func deduplicated(_ idioms: [LinkedIdiom]) -> [LinkedIdiom] {
        var seen = Set<String>()
        return idioms.filter { seen.insert($0.id).inserted }
    }

    /// 选项一律补齐到四条：编辑态删空的选项写 nil，与 `UserExampleSaver` 的缺项同一口径
    /// （读回 nil 才能在 P10 只读态落到「暂无」占位）。
    private func padded(_ options: [String?]) -> [String?] {
        (0..<4).map { index in
            guard index < options.count else { return nil }
            return trimmed(options[index])
        }
    }

    private func trimmed(_ value: String?) -> String? {
        let text = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return text.isEmpty ? nil : text
    }

    /// 与 `UserExampleSaver` 写入口径一致：`mistake.tagNames` 是 JSON 数组文本。
    private func tagJSON(_ tags: [String]) -> String {
        guard let data = try? JSONEncoder().encode(tags) else { return "[]" }
        return String(data: data, encoding: .utf8) ?? "[]"
    }

    /// 事务开头把关录入题是否存在：不在库即抛 `notFound`，不留下改了一半的正文与关联。
    private func requireUserExample(_ db: Database, id: String) throws {
        let row = try CountRow.fetchOne(
            db,
            sql: "SELECT COUNT(*) AS count FROM user_example WHERE id = ?",
            arguments: [id]
        )
        guard let row, row.count > 0 else { throw UserExampleEditError.notFound }
    }

    /// 删除前取一次 `imagePath`：可空列，用记录解，不用标量 fetch（NULL 会解成 nil）。
    private struct HeadRow: FetchableRecord, Decodable {
        let imagePath: String?
    }

    /// 存在性用记录解，和 `HeadRow` 同一口径。
    private struct CountRow: FetchableRecord, Decodable {
        let count: Int
    }
}
