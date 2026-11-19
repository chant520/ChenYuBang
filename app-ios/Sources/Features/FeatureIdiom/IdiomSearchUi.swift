import Foundation
import CangyuData

/// P04 输入分类：与 Data 层 FTS 路由（汉字/单字 vs 纯 ASCII 拼音）一一对应。
public enum SearchInputKind: Sendable {
    case empty
    case hanSingle
    case hanPhrase
    case roman
}

/// P04 结果行：成语名 / 拼音 / 释义首句（信息架构 v1.3 P04 ③）。
public struct SearchIdiom: Equatable, Identifiable, Sendable {
    public let id: String
    public let word: String
    public let pinyin: String
    public let senseFirst: String

    public init(id: String, word: String, pinyin: String, senseFirst: String) {
        self.id = id
        self.word = word
        self.pinyin = pinyin
        self.senseFirst = senseFirst
    }
}

/// P04 输入分类、结果映射与查无结果判定（与 Android IdiomSearchUi 同口径）。
public enum IdiomSearchUi {
    public static let inputHint = "完整成语 / 单字 / 全拼 / 拼音首字母都行"
    public static let noResultTitle = "没有找到这个成语，换个字或拼音试试"
    public static let noResultHint = "支持全拼与首字母，比如「bubujiu」或「bbjb」；也可以只输一个字。"

    public static func classify(_ query: String) -> SearchInputKind {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return .empty }
        if !containsHan(text) { return .roman }
        return text.count == 1 ? .hanSingle : .hanPhrase
    }

    public static func from(_ records: [IdiomRecord]) -> [SearchIdiom] {
        records.map {
            SearchIdiom(
                id: $0.id,
                word: $0.word,
                pinyin: $0.pinyin,
                senseFirst: IdiomDetail.firstSentence($0.sense)
            )
        }
    }

    /// 有输入且零结果才给空态；空输入只显示输入方式提示。
    public static func showNoResult(query: String, results: [SearchIdiom]) -> Bool {
        !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && results.isEmpty
    }

    private static func containsHan(_ text: String) -> Bool {
        text.unicodeScalars.contains { scalar in
            let value = scalar.value
            return (value >= 0x4E00 && value <= 0x9FFF) ||
                (value >= 0x3400 && value <= 0x4DBF) ||
                (value >= 0xF900 && value <= 0xFAFF)
        }
    }
}
