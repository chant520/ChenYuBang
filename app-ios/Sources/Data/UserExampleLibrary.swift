import Foundation
import GRDB

/// P10 录入题详情（信息架构 v1.4 P10 ②–⑨）。
///
/// 区块 ⑥「正确答案 + 一句解析」本版不做：`user_example` / `mistake` 无答案与解析列，
/// `answer` / `explain` 只存在于系统 `example`（2026-11-22 用户拍板，见信息架构 v1.4 版内修订第 ① 条）。
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
    /// 当前错题记录 id；P10 手动切换按此 id 精确写入，不按录入题 id 猜记录。
    public let mistakeID: String?
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
        mistakeID: String? = nil,
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
        self.mistakeID = mistakeID
        self.reason = reason
        self.tagNames = tagNames
    }
}

/// `user_example` / `user_example_idiom` 单题读取（P10），与 `MistakeLibrary` 同一口径的分文件扩展。
///
/// 只读：不改表结构、不写任何用户表。
extension MistakeLibrary {
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
            SELECT id, state, reason, tagNames
            FROM mistake
            WHERE userExampleId = ?
            ORDER BY createdAt DESC, id DESC
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
            mistakeID: stamp?.id,
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
        let id: String
        let state: String
        let reason: String?
        let tagNames: String
    }
}
