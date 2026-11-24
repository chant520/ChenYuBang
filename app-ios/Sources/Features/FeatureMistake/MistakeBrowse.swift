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
/// 行可点进 P10（§4.2 第 18 条）；`userExampleID` 为 nil 的历史错题行（v2 迁移前写入、
/// 没有对应录入题）不给导航，只在页面上展示。
public struct MistakeEntryView: Identifiable, Equatable, Sendable {
    public let id: String
    public let userExampleID: String?
    public let stem: String
    public let dateText: String
    public let idiomsText: String

    public init(
        id: String,
        userExampleID: String? = nil,
        stem: String,
        dateText: String,
        idiomsText: String
    ) {
        self.id = id
        self.userExampleID = userExampleID
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

    /// 关联成语串：多个以「、」连接，导入态无关联时明确提示。
    public static func idiomsText(_ words: [String]) -> String {
        let present = words.filter { !$0.isEmpty }
        return present.isEmpty ? "暂未关联成语" : present.joined(separator: "、")
    }

    /// 复习按钮文案（高保真稿 B P08）：`开始复习 · 7 道待复习`。
    public static func reviewTitle(pending: Int) -> String {
        "开始复习 · \(pending) \(unit)待复习"
    }

    /// 「开始复习」点下时队列已空的如实提示（与第 5.4 节 P11 全局空态标题同字面值）。
    public static let emptyReviewHint = "今天没有待复习的错题"
    /// 中性提示条的确认动作（与 P10 / P20 同字面值，feature 之间不互相引用故各自持有）。
    public static let gotIt = "知道了"

    /// P09 列表脚注（高保真稿 B P09 `.listfoot`）；P08 无脚注（信息架构 v1.4 P08 区块 ①–⑦ 不含脚注，高保真 P08 帧亦无）。
    public static func capturedFooter(_ count: Int) -> String {
        "\(count) \(unit) · 已到末尾"
    }

    public static func view(of group: MistakeIdiomGroup) -> MistakeGroupView {
        MistakeGroupView(id: group.idiomID, word: group.word, countText: countText(group.count))
    }

    public static func view(of row: MistakeRow) -> MistakeEntryView {
        MistakeEntryView(
            id: row.id,
            userExampleID: row.userExampleID,
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

    /// 选项标号（P10 ④ / ⑨）：`user_example.myAnswer` 的合法取值即这四项（`UserExampleSaver` 校验）。
    public static let letters = ["A", "B", "C", "D"]

    /// P10 ⑦ 出处：年份 · 考试类型；两者都缺显示「未填」，不猜值、不补地区与题型。
    public static func sourceLine(examType: String?, year: Int?) -> String {
        let parts = [year.map(String.init), trimmed(examType)].compactMap { $0 }
        return parts.isEmpty ? "未填" : parts.joined(separator: " · ")
    }

    /// P10 重做结果（信息架构 v1.4 版内修订第 ③ 条）：本版无正确答案来源，
    /// 只把本次所选与首次记录的「我的答案」比对；首次未填时明说无法比对，不伪造判分。
    public static func rerollResult(first: Int?, picked: Int?) -> String {
        guard let picked, letters.indices.contains(picked) else { return "尚未选择" }
        guard let first, letters.indices.contains(first) else {
            return "本题未记录你的首次作答，无法比对（本次选了 \(letters[picked])）"
        }
        return first == picked
            ? "与首次作答一致（\(letters[picked])）"
            : "与首次作答不同：首次 \(letters[first])，本次 \(letters[picked])"
    }

    /// P10 重做结果下的一行说明：把「没有正确答案」的口径写在页面上，不暗示已判分。
    public static let rerollNote = "录入题只存你填过的内容，本版未记录正确答案，这里只与你的首次作答比对。"

    /// iOS 15 无流式布局 API（开发规范 §7 平台映射）：标签按固定每行个数分组。
    public static func rows<T>(of items: [T], perRow: Int) -> [[T]] {
        guard perRow > 0 else { return [items] }
        return stride(from: 0, to: items.count, by: perRow).map {
            Array(items[$0..<min($0 + perRow, items.count)])
        }
    }

    private static func trimmed(_ value: String?) -> String? {
        let text = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return text.isEmpty ? nil : text
    }
}
