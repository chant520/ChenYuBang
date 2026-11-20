import Foundation
import GRDB

/// P20 保存所需的一行归属成语；ordered 为候选顺序，手动增补排末尾。
public struct SavedIdiom: Equatable, Sendable {
    public let id: String
    public let word: String

    public init(id: String, word: String) {
        self.id = id
        self.word = word
    }
}

/// P20 保存输入：题干 / 选项由识别文字在页面层切分后传入。
public struct CaptureSaveInput: Sendable {
    public let imageURL: URL
    public let ocrText: String
    public let stem: String
    public let optionA: String?
    public let optionB: String?
    public let optionC: String?
    public let optionD: String?
    public let myAnswer: String?
    public let isMistake: Bool
    public let reason: String
    public let tagNames: [String]
    public let examType: String?
    public let year: Int?
    public let idioms: [SavedIdiom]
    public let createdAt: Date

    public init(
        imageURL: URL,
        ocrText: String,
        stem: String,
        optionA: String? = nil,
        optionB: String? = nil,
        optionC: String? = nil,
        optionD: String? = nil,
        myAnswer: String? = nil,
        isMistake: Bool,
        reason: String = "",
        tagNames: [String] = [],
        examType: String? = nil,
        year: Int? = nil,
        idioms: [SavedIdiom],
        createdAt: Date = Date()
    ) {
        self.imageURL = imageURL
        self.ocrText = ocrText
        self.stem = stem
        self.optionA = optionA
        self.optionB = optionB
        self.optionC = optionC
        self.optionD = optionD
        self.myAnswer = myAnswer
        self.isMistake = isMistake
        self.reason = reason
        self.tagNames = tagNames
        self.examType = examType
        self.year = year
        self.idioms = idioms
        self.createdAt = createdAt
    }
}

/// 保存成功回执；mistakeID 仅在用户标错时存在。
public struct SavedCapture: Equatable, Sendable {
    public let userExampleID: String
    public let mistakeID: String?
    public let idioms: [SavedIdiom]

    public init(userExampleID: String, mistakeID: String?, idioms: [SavedIdiom]) {
        self.userExampleID = userExampleID
        self.mistakeID = mistakeID
        self.idioms = idioms
    }
}

public enum CaptureSaveError: Error, Equatable, Sendable {
    case noIdioms
    case emptyStem
    case invalidAnswer
    case invalidTag
    case imageCopyFailed
    case database
}

/// P20 保存事务（规范 §4.1 / §4.5 / §8.1）：
/// `user_example` 必写；`user_example_idiom` 写全部勾选（至少一条）；仅标错时另写
/// `mistake` + `mistake_idiom`。图片复制到 `images/<userExample.id>.jpg`，DB 只存相对路径；
/// DB 失败时删除已复制图片，避免孤儿文件。
public final class UserExampleSaver: @unchecked Sendable {
    private let queue: DatabaseQueue
    private let imagesDirectory: URL

    public init(queue: DatabaseQueue, imagesDirectory: URL) {
        self.queue = queue
        self.imagesDirectory = imagesDirectory
    }

    public func save(_ input: CaptureSaveInput) throws -> SavedCapture {
        let idioms = deduplicated(input.idioms)
        guard !idioms.isEmpty, idioms.allSatisfy({ !$0.id.isEmpty && !$0.word.isEmpty }) else {
            throw CaptureSaveError.noIdioms
        }
        guard !input.stem.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw CaptureSaveError.emptyStem
        }
        if let answer = input.myAnswer, !["A", "B", "C", "D"].contains(answer) {
            throw CaptureSaveError.invalidAnswer
        }
        guard input.tagNames.allSatisfy({ (1...8).contains($0.count) }) else {
            throw CaptureSaveError.invalidTag
        }

        let userExampleID = UUID().uuidString.lowercased()
        let mistakeID = input.isMistake ? UUID().uuidString.lowercased() : nil
        let relativePath = "images/\(userExampleID).jpg"
        let target = imagesDirectory.appendingPathComponent("\(userExampleID).jpg")
        try copyImage(from: input.imageURL, to: target)

        do {
            try queue.write { db in
                try insertUserExample(input, id: userExampleID, imagePath: relativePath, in: db)
                for idiom in idioms {
                    try db.execute(
                        sql: "INSERT INTO user_example_idiom(userExampleId, idiomId) VALUES (?, ?)",
                        arguments: [userExampleID, idiom.id]
                    )
                }
                if let mistakeID {
                    try insertMistake(input, id: mistakeID, userExampleID: userExampleID, idioms: idioms, in: db)
                }
            }
        } catch {
            try? FileManager.default.removeItem(at: target)
            throw CaptureSaveError.database
        }
        return SavedCapture(userExampleID: userExampleID, mistakeID: mistakeID, idioms: idioms)
    }

    private func deduplicated(_ idioms: [SavedIdiom]) -> [SavedIdiom] {
        var seen = Set<String>()
        return idioms.filter { seen.insert($0.id).inserted }
    }

    private func copyImage(from source: URL, to target: URL) throws {
        do {
            try FileManager.default.createDirectory(at: imagesDirectory, withIntermediateDirectories: true)
            if FileManager.default.fileExists(atPath: target.path) {
                try FileManager.default.removeItem(at: target)
            }
            try FileManager.default.copyItem(at: source, to: target)
        } catch {
            throw CaptureSaveError.imageCopyFailed
        }
    }

    private func insertUserExample(
        _ input: CaptureSaveInput,
        id: String,
        imagePath: String,
        in db: Database
    ) throws {
        let arguments: StatementArguments = [
            id, imagePath, input.ocrText, input.stem,
            input.optionA, input.optionB, input.optionC, input.optionD,
            input.myAnswer, input.isMistake, input.examType, input.year,
            nil, timestamp(input.createdAt)
        ]
        try db.execute(sql: """
            INSERT INTO user_example
                (id, imagePath, ocrText, stem, optionA, optionB, optionC, optionD,
                 myAnswer, isMistake, examType, year, region, createdAt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, arguments: arguments)
    }

    private func insertMistake(
        _ input: CaptureSaveInput,
        id: String,
        userExampleID: String,
        idioms: [SavedIdiom],
        in db: Database
    ) throws {
        let imagePath = "images/\(userExampleID).jpg"
        let reason = input.reason.trimmingCharacters(in: .whitespacesAndNewlines)
        let arguments: StatementArguments = [
            id, userExampleID, imagePath, input.ocrText, input.stem,
            input.optionA ?? "", input.optionB ?? "", input.optionC ?? "", input.optionD ?? "",
            input.myAnswer, reason.isEmpty ? nil : reason, tagJSON(input.tagNames),
            input.examType, input.year, nil, "待复习", timestamp(input.createdAt), nil, 0
        ]
        try db.execute(sql: """
            INSERT INTO mistake
                (id, userExampleId, imagePath, ocrText, stem, optionA, optionB, optionC, optionD,
                 myAnswer, reason, tagNames, examType, year, region, state, createdAt,
                 lastReviewedAt, reviewCount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, arguments: arguments)
        for idiom in idioms {
            try db.execute(
                sql: "INSERT INTO mistake_idiom(mistakeId, idiomId) VALUES (?, ?)",
                arguments: [id, idiom.id]
            )
        }
    }

    private func tagJSON(_ tags: [String]) -> String {
        guard let data = try? JSONEncoder().encode(tags) else { return "[]" }
        return String(data: data, encoding: .utf8) ?? "[]"
    }

    /// 与既有备份口径一致：UTC、秒级、无小数。
    private func timestamp(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter.string(from: date)
    }
}
