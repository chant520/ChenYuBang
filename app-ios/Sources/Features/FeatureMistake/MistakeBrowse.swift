import Foundation
import CangyuData

/// P08 三视图（信息架构 v1.4 P08 ③：按成语分组 / 按录入时间倒序 / 按考试类型）。
/// rawValue 与 `CSegmentedControl` 的下标一致，也是 P08 需要保留的视图状态（决策 C）。
public enum MistakeDimension: Int, CaseIterable, Sendable {
    case grouped
    case createdAt
    case examType

    public var label: String {
        switch self {
        case .grouped: return "按成语分组"
        case .createdAt: return "按录入时间"
        case .examType: return "按考试类型"
        }
    }

    /// 平铺视图的读库排序；分组视图不用（走 `idiomGroups`）。
    public var rowOrder: MistakeRowOrder {
        self == .examType ? .byExamType : .byCreatedAt
    }
}

/// P08 分组行（与 Android MistakeGroupView 同口径）：成语名 + 「N 道」，点击进 P09。
public struct MistakeGroupView: Identifiable, Equatable, Sendable {
    public let id: String
    public let word: String
    public let countText: String

    public init(id: String, word: String, countText: String) {
        self.id = id
        self.word = word
        self.countText = countText
    }
}

/// P08 平铺行（信息架构 v1.4 P08 ⑤）：题干 + 录入时间 + 关联成语。
/// P10 在工作包 2.7，本版行不可点，字段先全部呈现。
public struct MistakeEntryView: Identifiable, Equatable, Sendable {
    public let id: String
    public let stem: String
    public let dateText: String
    public let idiomsText: String

    public init(id: String, stem: String, dateText: String, idiomsText: String) {
        self.id = id
        self.stem = stem
        self.dateText = dateText
        self.idiomsText = idiomsText
    }
}

/// P09 行（信息架构 v1.4 P09 ③）：题干 + 是否标错 + 录入时间 + 掌握状态 + 错因。
public struct CapturedExampleView: Identifiable, Equatable, Sendable {
    public let id: String
    public let stem: String
    public let dateText: String
    /// 该题是否标过错（`user_example.isMistake`）；false 时按「我录入 / 未标错」中性展示。
    public let isMistake: Bool
    /// 错题掌握状态原值（`待复习` / `已掌握`）；无错题记录时为 nil。
    public let state: String?
    public let reason: String?

    public init(
        id: String,
        stem: String,
        dateText: String,
        isMistake: Bool,
        state: String?,
        reason: String?
    ) {
        self.id = id
        self.stem = stem
        self.dateText = dateText
        self.isMistake = isMistake
        self.state = state
        self.reason = reason
    }
}

/// P08 / P09 取值（纯函数，与 Android MistakeBrowse 同口径）：只做字段呈现，不改写题目内容。
public enum MistakeBrowse {
    public static let unit = "道"
    /// 缺字段占位，与 `IdiomDetail.placeholder` 同字面值；feature 之间不互相依赖，故各自持有。
    public static let placeholder = "暂无"
    /// 「已掌握」状态原值（与 `MistakeLibrary.pendingState` 相对）；该状态的标签用虚线描边样式。
    public static let learnedState = "已掌握"

    /// 「N 道」：分组行右侧计数。
    public static func countText(_ count: Int) -> String {
        "\(count) \(unit)"
    }

    /// 「2026-11-14 录入」：取 ISO8601 UTC 串的日期段，不做本地化换算
    /// （库内一律按 UTC 秒级写入，转本地时区会让跨零点录入的题显示成第二天）。
    public static func dateText(_ createdAt: String) -> String {
        let digits = createdAt.prefix { $0.isNumber || $0 == "-" }
        return "\(digits.prefix(10)) 录入"
    }

    /// 关联成语串：多个以「、」连接，无关联归「暂无」。
    public static func idiomsText(_ words: [String]) -> String {
        let present = words.filter { !$0.isEmpty }
        return present.isEmpty ? placeholder : present.joined(separator: "、")
    }

    /// 复习按钮文案（高保真稿 B P08）：`开始复习 · 7 道待复习`。
    public static func reviewTitle(pending: Int) -> String {
        "开始复习 · \(pending) \(unit)待复习"
    }

    /// P08 列表脚注，与 P03 同款式；P09 用「N 道 · 已到末尾」（高保真稿 B P09 `.listfoot`）。
    public static func bookFooter(_ count: Int) -> String {
        "共 \(count) 条 · 已到末尾"
    }

    public static func capturedFooter(_ count: Int) -> String {
        "\(count) \(unit) · 已到末尾"
    }

    public static func view(of group: MistakeIdiomGroup) -> MistakeGroupView {
        MistakeGroupView(id: group.idiomID, word: group.word, countText: countText(group.count))
    }

    public static func view(of row: MistakeRow) -> MistakeEntryView {
        MistakeEntryView(
            id: row.id,
            stem: row.stem,
            dateText: dateText(row.createdAt),
            idiomsText: idiomsText(row.idiomWords)
        )
    }

    public static func view(of example: IdiomCapturedExample) -> CapturedExampleView {
        CapturedExampleView(
            id: example.id,
            stem: example.stem,
            dateText: dateText(example.createdAt),
            // 「是否标错」取录入题自身的标记（信息架构 v1.4 P09 ③）；掌握状态与错因来自错题记录。
            isMistake: example.isMistake,
            state: trimmed(example.mistakeState),
            reason: trimmed(example.reason)
        )
    }

    /// 释义一行（P09 ②）：取 `sense` 原文，空串归「暂无」；不拼感情色彩，也不自行截句。
    public static func senseLine(_ sense: String?) -> String {
        trimmed(sense) ?? placeholder
    }

    private static func trimmed(_ value: String?) -> String? {
        let text = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return text.isEmpty ? nil : text
    }
}
