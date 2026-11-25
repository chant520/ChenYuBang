import Foundation

/// P25 可注入随机源。实现必须是值类型或自行保证 Sendable；测试用固定序列钉住洗牌性质。
public protocol SelfTestRandomSource: Sendable {
    mutating func next(upperBound: Int) -> Int
}

public struct SystemSelfTestRandomSource: SelfTestRandomSource {
    public init() {}

    public mutating func next(upperBound: Int) -> Int {
        precondition(upperBound > 0)
        return Int.random(in: 0..<upperBound)
    }
}

/// 引擎只依赖 id / word / sense，不依赖 SwiftUI、GRDB 或持久化模型。
public struct SelfTestCandidate: Equatable, Sendable, Identifiable {
    public let id: String
    public let word: String
    public let sense: String

    public init(id: String, word: String, sense: String) {
        self.id = id
        self.word = word
        self.sense = sense
    }

    fileprivate var normalized: SelfTestCandidate? {
        let id = id.trimmingCharacters(in: .whitespacesAndNewlines)
        let word = word.trimmingCharacters(in: .whitespacesAndNewlines)
        let sense = sense.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty, !word.isEmpty, !sense.isEmpty else { return nil }
        return SelfTestCandidate(id: id, word: word, sense: sense)
    }
}

public enum SelfTestQuestionType: Equatable, Sendable {
    case definitionToIdiom
    case idiomToDefinition
}

public struct SelfTestOption: Equatable, Sendable, Identifiable {
    /// 成语 id 即选项身份；判分不比较展示文本或选项位置。
    public let idiomID: String
    public let text: String

    public init(idiomID: String, text: String) {
        self.idiomID = idiomID
        self.text = text
    }

    public var id: String { idiomID }
}

public struct SelfTestQuestion: Equatable, Sendable {
    public let targetID: String
    public let type: SelfTestQuestionType
    public let prompt: String
    public let options: [SelfTestOption]

    public var correctOptionID: String { targetID }
}

public struct SelfTestSummary: Equatable, Sendable {
    public let total: Int
    public let correct: Int
    public let wrong: Int
}

/// 一轮自测的纯内存状态机：选题与选项在构建时固定，交互只改变当前题选择与计数。
public struct SelfTestSession: Sendable {
    public let questions: [SelfTestQuestion]
    public private(set) var index = 0
    public private(set) var selectedOptionID: String?
    public private(set) var correctCount = 0
    public private(set) var isComplete = false

    public var current: SelfTestQuestion? {
        isComplete ? nil : questions.indices.contains(index) ? questions[index] : nil
    }

    public var isGraded: Bool { selectedOptionID != nil }
    public var isLast: Bool { index == questions.count - 1 }
    public var progressText: String { "第 \(min(index + 1, questions.count)) 题 / 共 \(questions.count) 题" }

    public var summary: SelfTestSummary? {
        guard isComplete else { return nil }
        return SelfTestSummary(total: questions.count, correct: correctCount, wrong: questions.count - correctCount)
    }

    /// 选择后立即按目标成语 id 判分；重复点击、未知 id 和已完成轮次都被忽略。
    @discardableResult
    public mutating func select(_ optionID: String) -> Bool {
        guard !isComplete, !isGraded,
              let question = current,
              question.options.contains(where: { $0.idiomID == optionID }) else { return false }
        selectedOptionID = optionID
        if optionID == question.targetID { correctCount += 1 }
        return true
    }

    /// 未判分不能推进；第 10 题手动点「查看结果」后才形成小结。
    @discardableResult
    public mutating func next() -> Bool {
        guard !isComplete, isGraded, current != nil else { return false }
        if isLast {
            isComplete = true
        } else {
            index += 1
            selectedOptionID = nil
        }
        return true
    }
}

public enum SelfTestRoundStart: Sendable {
    case emptyScope
    case insufficientCandidates
    case ready(SelfTestSession)
}

/// P25 组题纯引擎：目标只来自当前范围，干扰项只来自全库同一资格集合。
public enum SelfTestRoundBuilder {
    public static let questionCount = 10

    /// id、词面与释义按去空白后的值全局计数；任一重复即排除，包括范围外的重复记录。
    public static func eligibleCandidates(from library: [SelfTestCandidate]) -> [SelfTestCandidate] {
        let normalized = library.compactMap(\.normalized)
        var wordCounts: [String: Int] = [:]
        var senseCounts: [String: Int] = [:]
        var idCounts: [String: Int] = [:]
        for candidate in normalized {
            wordCounts[candidate.word, default: 0] += 1
            senseCounts[candidate.sense, default: 0] += 1
            idCounts[candidate.id, default: 0] += 1
        }
        return normalized.filter {
            wordCounts[$0.word] == 1 && senseCounts[$0.sense] == 1 && idCounts[$0.id] == 1
        }.sorted { $0.id < $1.id }
    }

    public static func make(
        scoped: [SelfTestCandidate],
        library: [SelfTestCandidate],
        randomSource: inout any SelfTestRandomSource
    ) -> SelfTestRoundStart {
        let eligibleLibrary = eligibleCandidates(from: library)
        let scopedIDs = Set(scoped.map { $0.id.trimmingCharacters(in: .whitespacesAndNewlines) })
        let targets = eligibleLibrary.filter { scopedIDs.contains($0.id) }
        guard !targets.isEmpty else { return .emptyScope }
        guard eligibleLibrary.count >= 4 else { return .insufficientCandidates }

        let shuffledTargets = shuffled(targets, randomSource: &randomSource)
        let questions = (0..<questionCount).map { index in
            let target = shuffledTargets[index % shuffledTargets.count]
            let distractors = shuffled(
                eligibleLibrary.filter { $0.id != target.id },
                randomSource: &randomSource
            ).prefix(3)
            let type: SelfTestQuestionType = index.isMultiple(of: 2) ? .definitionToIdiom : .idiomToDefinition
            let options = shuffled([target] + Array(distractors), randomSource: &randomSource).map { candidate in
                SelfTestOption(
                    idiomID: candidate.id,
                    text: type == .definitionToIdiom ? candidate.word : candidate.sense
                )
            }
            return SelfTestQuestion(
                targetID: target.id,
                type: type,
                prompt: type == .definitionToIdiom ? target.sense : target.word,
                options: options
            )
        }
        return .ready(SelfTestSession(questions: questions))
    }

    private static func shuffled(
        _ candidates: [SelfTestCandidate],
        randomSource: inout any SelfTestRandomSource
    ) -> [SelfTestCandidate] {
        var result = candidates
        for index in stride(from: result.count - 1, through: 1, by: -1) {
            let other = randomSource.next(upperBound: index + 1)
            result.swapAt(index, other)
        }
        return result
    }
}
