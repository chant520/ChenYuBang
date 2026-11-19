import CangyuData

/// P07 例题详情展示模型（信息架构 v1.4 P07 ①–⑧）。
///
/// 与 Android ExampleDetail / ExampleDetailView 同口径：字段均为可直接呈现的文本，
/// 缺字段已在 `ExampleDetail.view(of:)` 里填占位，页面不做空值判断。
public struct ExampleDetailView: Equatable, Sendable {
    public let id: String
    public let stem: String
    public let optionA: String
    public let optionB: String
    public let optionC: String
    public let optionD: String
    public let answer: String
    public let explain: String
    /// 出处行：「年份 · 地区 · 考试类型 · 题型」。
    public let sourceLine: String

    public init(
        id: String,
        stem: String,
        optionA: String,
        optionB: String,
        optionC: String,
        optionD: String,
        answer: String,
        explain: String,
        sourceLine: String
    ) {
        self.id = id
        self.stem = stem
        self.optionA = optionA
        self.optionB = optionB
        self.optionC = optionC
        self.optionD = optionD
        self.answer = answer
        self.explain = explain
        self.sourceLine = sourceLine
    }
}

/// P07 取值 / 缺字段判定（纯函数，与 Android ExampleDetail 同口径）。
public enum ExampleDetail {
    /// 缺字段统一占位（开发规范 §5「暂无」）；feature 之间不互相依赖，故本模块自带常量。
    public static let placeholder = "暂无"

    public static func view(of record: ExampleRecord) -> ExampleDetailView {
        ExampleDetailView(
            id: record.id,
            stem: present(record.stem),
            optionA: present(record.optionA),
            optionB: present(record.optionB),
            optionC: present(record.optionC),
            optionD: present(record.optionD),
            answer: present(record.answer),
            explain: present(record.explain),
            sourceLine: sourceLine(record)
        )
    }

    /// 空串 / 纯空白 / nil 一律归占位。
    public static func present(_ value: String?) -> String {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? placeholder : trimmed
    }

    /// 出处行逐项有值才拼接，缺项跳过；全部缺失时为占位。
    public static func sourceLine(_ record: ExampleRecord) -> String {
        let raw = [
            record.year > 0 ? String(record.year) : nil,
            present(record.region),
            present(record.examType),
            present(record.questionType)
        ]
        let parts = raw.compactMap { $0 }.filter { $0 != placeholder }
        return parts.isEmpty ? placeholder : parts.joined(separator: " · ")
    }
}

/// 成语命中：字符偏移以 `Array(text)` 下标计，`text[start..<end] == word`。
public struct IdiomHighlightHit: Equatable, Sendable {
    public let idiomID: String
    public let word: String
    public let start: Int
    public let end: Int

    public init(idiomID: String, word: String, start: Int, end: Int) {
        self.idiomID = idiomID
        self.word = word
        self.start = start
        self.end = end
    }
}

/// 扫描候选命中（内部用；避免三元组直接在函数体里流转）。
private struct IdiomMatchCandidate {
    let idiomID: String
    let word: [Character]
    let start: Int
}

/// 例题文本里的成语高亮匹配（工作包 1.5，验收 5/7），与 Android IdiomHighlight 同口径：
/// 词表按字长降序扫描，长词优先；同一位置只归属一次命中，结果按位置升序且互不重叠。
public enum IdiomHighlight {
    public static func match(_ text: String, wordIndex: [String: String]) -> [IdiomHighlightHit] {
        guard !text.isEmpty, !wordIndex.isEmpty else { return [] }
        let characters = Array(text)
        let sorted = wordIndex
            .map { (idiomID: $0.key, word: Array($0.value)) }
            .filter { !$0.word.isEmpty }
            .sorted { $0.word.count > $1.word.count }
        var hits: [IdiomHighlightHit] = []
        var cursor = 0
        while cursor < characters.count {
            var best: IdiomMatchCandidate?
            for entry in sorted {
                guard let index = firstIndex(of: entry.word, in: characters, from: cursor) else { continue }
                if let current = best {
                    let earlier = index < current.start
                    let longerAtSameStart = index == current.start && entry.word.count > current.word.count
                    if earlier || longerAtSameStart {
                        best = IdiomMatchCandidate(idiomID: entry.idiomID, word: entry.word, start: index)
                    }
                } else {
                    best = IdiomMatchCandidate(idiomID: entry.idiomID, word: entry.word, start: index)
                }
            }
            guard let hit = best else { break }
            hits.append(
                IdiomHighlightHit(
                    idiomID: hit.idiomID,
                    word: String(hit.word),
                    start: hit.start,
                    end: hit.start + hit.word.count
                )
            )
            cursor = hit.start + hit.word.count
        }
        return hits
    }

    private static func firstIndex(
        of word: [Character],
        in characters: [Character],
        from start: Int
    ) -> Int? {
        guard !word.isEmpty, characters.count >= word.count else { return nil }
        let last = characters.count - word.count
        var index = start
        while index <= last {
            if Array(characters[index..<(index + word.count)]) == word { return index }
            index += 1
        }
        return nil
    }
}
