import Foundation
import GRDB

/// example 表记录（信息架构 v1.4 P07；字段对齐规范 §4.1）。imagePath 可为空。
public struct ExampleRecord: FetchableRecord, Decodable, Equatable, Sendable {
    public let id: String
    public let stem: String
    public let optionA: String
    public let optionB: String
    public let optionC: String
    public let optionD: String
    public let answer: String
    public let explain: String
    public let examType: String
    public let year: Int
    public let region: String
    public let questionType: String
    public let imagePath: String?
}

/// 成语 id + 词（P07 文本高亮扫描用）；普通 POJO，非业务表记录。
public struct IdiomWordRef: FetchableRecord, Decodable, Equatable, Sendable {
    public let id: String
    public let word: String

    public init(id: String, word: String) {
        self.id = id
        self.word = word
    }
}

/// example / example_idiom 只读查询（P07 详情与关联成语；B3 一题多成语）。
/// 只读：不改表结构、不写任何用户表。
public enum ExampleLibrary {
    /// 按 uuid 取例题完整行，缺行返回 nil。
    public static func detail(_ db: Database, id: String) throws -> ExampleRecord? {
        try ExampleRecord.fetchOne(db, sql: "SELECT * FROM example WHERE id = ?", arguments: [id])
    }

    /// 该题关联的全部成语 id，按 idiomId 升序（与 Android ExampleDao.listByExample 同口径）。
    public static func relatedIdiomIDs(_ db: Database, exampleID: String) throws -> [String] {
        try String.fetchAll(
            db,
            sql: "SELECT idiomId FROM example_idiom WHERE exampleId = ? ORDER BY idiomId",
            arguments: [exampleID]
        )
    }

    /// 全量成语词表（id + word），供题干 / 选项 / 解析的成语高亮扫描。
    public static func idiomWords(_ db: Database) throws -> [IdiomWordRef] {
        try IdiomWordRef.fetchAll(db, sql: "SELECT id, word FROM idiom")
    }
}

/// 读库入口：P07 通过协议注入，不直接持有 DatabaseQueue。
public final class ExampleLibraryReader {
    private let queue: DatabaseQueue

    public init(queue: DatabaseQueue) {
        self.queue = queue
    }

    public func detail(id: String) throws -> ExampleRecord? {
        try queue.read { db in try ExampleLibrary.detail(db, id: id) }
    }

    public func relatedIdiomIDs(exampleID: String) throws -> [String] {
        try queue.read { db in try ExampleLibrary.relatedIdiomIDs(db, exampleID: exampleID) }
    }

    public func idiomWords() throws -> [IdiomWordRef] {
        try queue.read { db in try ExampleLibrary.idiomWords(db) }
    }
}
