import Foundation

/// P20 修正表单（高保真稿 C P20 906–933 行）：答案、错因、标签、是否做错、来源。
/// 错因 / 标签仅在勾选「是否做错」时可用并落库（开发规范 v1.7 §8.1）。
public struct P20FormState: Equatable {
    public var answerText = ""
    public var reason = ""
    public var tags: [String] = []
    public var isMistake = false
    public var examType: String?
    public var yearText = ""
    public var saving = false
    public var saveError: String?

    public init(
        answerText: String = "",
        reason: String = "",
        tags: [String] = [],
        isMistake: Bool = false,
        examType: String? = nil,
        yearText: String = "",
        saving: Bool = false,
        saveError: String? = nil
    ) {
        self.answerText = answerText
        self.reason = reason
        self.tags = tags
        self.isMistake = isMistake
        self.examType = examType
        self.yearText = yearText
        self.saving = saving
        self.saveError = saveError
    }

    /// 只接受 A/B/C/D 单字母；清空输入即不填。
    public var answer: String? {
        let value = answerText.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard value.count == 1, ["A", "B", "C", "D"].contains(value) else { return nil }
        return value
    }

    public var answerInvalid: Bool { !answerText.isEmpty && answer == nil }

    public var year: Int? {
        guard yearText.count == 4, let value = Int(yearText) else { return nil }
        return value
    }

    public var yearInvalid: Bool { !yearText.isEmpty && year == nil }
}
