import Foundation
import CangyuData

/// P05 成语详情展示模型（信息架构 v1.3 P05 ①–⑦）。
/// 字段均为可直接呈现的文本，缺字段已在 `IdiomDetail.view(of:)` 里填占位，页面不做空值判断。
public struct IdiomDetailView: Equatable, Sendable {
    public let id: String
    public let word: String
    public let pinyin: String
    public let color: String
    public let sense: String
    public let origin: String
    public let pitfalls: [String]
    public let synonyms: [String]
    public let antonyms: [String]

    public init(
        id: String,
        word: String,
        pinyin: String,
        color: String,
        sense: String,
        origin: String,
        pitfalls: [String],
        synonyms: [String],
        antonyms: [String]
    ) {
        self.id = id
        self.word = word
        self.pinyin = pinyin
        self.color = color
        self.sense = sense
        self.origin = origin
        self.pitfalls = pitfalls
        self.synonyms = synonyms
        self.antonyms = antonyms
    }
}

/// P05 取值 / 缺字段判定 / JSON 数组解析（纯函数，与 Android IdiomDetail 同口径）。
public enum IdiomDetail {
    public static let placeholder = "暂无"

    private static let sentenceEnds: [Character] = ["。", "！", "？", "；"]

    public static func view(of record: IdiomRecord) -> IdiomDetailView {
        IdiomDetailView(
            id: record.id,
            word: present(record.word),
            pinyin: present(record.pinyin),
            color: colorText(record.color),
            sense: present(record.sense),
            origin: present(record.origin),
            pitfalls: parseJsonStringArray(record.pitfalls),
            synonyms: parseJsonStringArray(record.synonyms),
            antonyms: parseJsonStringArray(record.antonyms)
        )
    }

    /// 空串 / 纯空白 / nil 一律归占位。
    public static func present(_ value: String?) -> String {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? placeholder : trimmed
    }

    /// 感情色彩归一到「褒义 / 贬义 / 中性」。
    public static func colorText(_ raw: String?) -> String {
        switch raw?.trimmingCharacters(in: .whitespacesAndNewlines) {
        case "褒", "褒义": return "褒义"
        case "贬", "贬义": return "贬义"
        case "中", "中性": return "中性"
        default: return present(raw)
        }
    }

    /// 易错点 / 近义 / 反义空列表呈现：整项填占位。
    public static func presentList(_ items: [String]) -> String {
        let joined = items.filter { !$0.isEmpty }.joined(separator: "、")
        return joined.isEmpty ? placeholder : joined
    }

    /// 解析库里 JSON 字符串数组；非数组文本按单项处理，nil / 空 / `[]` 返回空列表。
    public static func parseJsonStringArray(_ raw: String?) -> [String] {
        guard let raw, !raw.isEmpty, raw != "null" else { return [] }
        if let data = raw.data(using: .utf8),
           let array = try? JSONSerialization.jsonObject(with: data) as? [String] {
            return array.filter { !$0.isEmpty && $0 != "null" }
        }
        let single = raw.trimmingCharacters(in: CharacterSet(charactersIn: "\""))
        return single.isEmpty ? [] : [single]
    }

    /// 释义首句：按第一个句读符截断并保留该符号。
    public static func firstSentence(_ sense: String?) -> String {
        let text = present(sense)
        guard text != placeholder, let index = text.firstIndex(where: { sentenceEnds.contains($0) }) else {
            return text
        }
        return String(text[...text.index(after: index)])
    }
}
