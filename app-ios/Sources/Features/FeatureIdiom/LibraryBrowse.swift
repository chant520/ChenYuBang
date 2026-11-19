import Foundation
import CangyuData

/// P03 浏览维度（信息架构 v1.2 P03 ③：按考试范围 / 按拼音首字母 / 按考频高低）。
public enum LibraryDimension: Int, CaseIterable, Sendable {
    case scope
    case initial
    case frequency

    public var label: String {
        switch self {
        case .scope: return "按考试范围"
        case .initial: return "按首字母"
        case .frequency: return "按考频"
        }
    }
}

/// 一个字母分组；`letter == nil` 表示不分组的整段列表（按范围 / 按考频）。
public struct LibrarySection: Equatable, Sendable, Identifiable {
    public let letter: String?
    public let items: [LibraryIdiom]

    public init(letter: String?, items: [LibraryIdiom]) {
        self.letter = letter
        self.items = items
    }

    public var id: String { letter ?? "" }
}

public struct LibraryPage: Equatable, Sendable {
    public let sections: [LibrarySection]

    public init(sections: [LibrarySection]) {
        self.sections = sections
    }

    public var items: [LibraryIdiom] { sections.flatMap(\.items) }
    public var count: Int { items.count }
    public var isEmpty: Bool { items.isEmpty }
}

/// P03 的筛选 / 排序 / 字母分组（纯函数，便于单测）。
///
/// 排序口径与 Android 一致：首字母串升序、同首字母按词面升序；
/// 按考频降序，同频仍按首字母与词面稳定排序。范围过滤已由 `idiom_scope` 在库层完成。
public enum LibraryBrowse {
    public static let otherLetter = "#"

    /// 当前题库里实际出现过的首字母，A–Z 顺序，`#` 排最后。
    public static func letters(_ items: [LibraryIdiom]) -> [String] {
        var seen = Set<String>()
        let present = items.map(\.letter).filter { seen.insert($0).inserted }
        let alphabet = present.filter { $0 != otherLetter }.sorted()
        return alphabet + present.filter { $0 == otherLetter }
    }

    public static func page(
        _ items: [LibraryIdiom],
        dimension: LibraryDimension,
        letter: String?
    ) -> LibraryPage {
        switch dimension {
        case .scope:
            return LibraryPage(sections: [LibrarySection(letter: nil, items: items.sorted(by: byPinyin))])
        case .initial:
            let filtered = letter.map { target in items.filter { $0.letter == target } } ?? items
            let grouped = Dictionary(grouping: filtered, by: \.letter)
            let keys = grouped.keys.sorted { lhs, rhs in
                if lhs == otherLetter { return false }
                if rhs == otherLetter { return true }
                return lhs < rhs
            }
            let sections = keys.map { key in
                LibrarySection(letter: key, items: (grouped[key] ?? []).sorted(by: byPinyin))
            }
            return LibraryPage(sections: sections)
        case .frequency:
            return LibraryPage(sections: [LibrarySection(letter: nil, items: items.sorted(by: byFrequency))])
        }
    }

    private static func byPinyin(_ lhs: LibraryIdiom, _ rhs: LibraryIdiom) -> Bool {
        if lhs.initials != rhs.initials { return lhs.initials < rhs.initials }
        return lhs.word < rhs.word
    }

    private static func byFrequency(_ lhs: LibraryIdiom, _ rhs: LibraryIdiom) -> Bool {
        if lhs.frequency != rhs.frequency { return lhs.frequency > rhs.frequency }
        return byPinyin(lhs, rhs)
    }
}
