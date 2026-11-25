import Combine
import CangyuData
import Foundation

/// P25 只读库接口；后台加载只传递 Sendable 投影，不持有 DatabaseQueue 或 SwiftUI 状态。
public protocol SelfTestLibraryReading: Sendable {
    func scopedWordSenses() throws -> [IdiomWordSense]
    func allIdiomsForMatch() throws -> [IdiomRecord]
}

extension IdiomLibraryReader: SelfTestLibraryReading {}

public enum P25SelfTestState: Equatable, Sendable {
    case loading
    case emptyScope
    case insufficientCandidates
    case question(P25QuestionState)
    case summary(P25SummaryState)
    case error
}

public struct P25QuestionState: Equatable, Sendable {
    public let question: SelfTestQuestion
    public let number: Int
    public let total: Int
    public let selectedOptionID: String?
    public let correctCount: Int

    public init(
        question: SelfTestQuestion,
        number: Int,
        total: Int,
        selectedOptionID: String?,
        correctCount: Int
    ) {
        self.question = question
        self.number = number
        self.total = total
        self.selectedOptionID = selectedOptionID
        self.correctCount = correctCount
    }

    public var isGraded: Bool { selectedOptionID != nil }
    public var isCorrect: Bool { selectedOptionID == question.targetID }
    public var isLast: Bool { number == total }
    public var progressText: String { "第 \(number) 题 / 共 \(total) 题" }
    public var typeTitle: String {
        question.type == .definitionToIdiom ? "给释义选成语" : "给成语选释义"
    }
    public var nextButtonTitle: String { isLast ? "查看结果" : "下一题" }
}

public struct P25SummaryState: Equatable, Sendable {
    public let total: Int
    public let correct: Int
    public let wrong: Int

    public init(total: Int, correct: Int, wrong: Int) {
        self.total = total
        self.correct = correct
        self.wrong = wrong
    }
}

public struct P25SelfTestActions {
    public let onPick: (String) -> Void
    public let onNext: () -> Void
    public let onAskExit: () -> Void
    public let onCancelExit: () -> Void
    public let onConfirmExit: () -> Void
    public let onLeave: () -> Void
    public let onRetryRound: () -> Void
    public let onRetryLoad: () -> Void

    public init(
        onPick: @escaping (String) -> Void,
        onNext: @escaping () -> Void,
        onAskExit: @escaping () -> Void,
        onCancelExit: @escaping () -> Void,
        onConfirmExit: @escaping () -> Void,
        onLeave: @escaping () -> Void,
        onRetryRound: @escaping () -> Void,
        onRetryLoad: @escaping () -> Void
    ) {
        self.onPick = onPick
        self.onNext = onNext
        self.onAskExit = onAskExit
        self.onCancelExit = onCancelExit
        self.onConfirmExit = onConfirmExit
        self.onLeave = onLeave
        self.onRetryRound = onRetryRound
        self.onRetryLoad = onRetryLoad
    }
}

@MainActor
public final class P25SelfTestViewModel: ObservableObject {
    @Published public private(set) var state: P25SelfTestState = .loading
    @Published public private(set) var exitAsking = false

    private let reader: SelfTestLibraryReading
    private let onExit: () -> Void
    private var randomSource: any SelfTestRandomSource
    private var scopedCandidates: [SelfTestCandidate] = []
    private var libraryCandidates: [SelfTestCandidate] = []
    private var session: SelfTestSession?
    private var loadGeneration = UUID()

    public init(
        reader: SelfTestLibraryReading,
        randomSource: any SelfTestRandomSource = SystemSelfTestRandomSource(),
        onExit: @escaping () -> Void
    ) {
        self.reader = reader
        self.randomSource = randomSource
        self.onExit = onExit
    }

    /// 两次只读查询放到后台；generation 保证迟到结果不会覆盖重试或退出后的状态。
    public func load() async {
        let generation = UUID()
        loadGeneration = generation
        session = nil
        exitAsking = false
        state = .loading
        let reader = self.reader

        do {
            let loaded = try await Task.detached(priority: .userInitiated) { () throws -> LoadedLibrary in
                let scoped = try reader.scopedWordSenses()
                let library = try reader.allIdiomsForMatch()
                return LoadedLibrary(
                    scoped: scoped.map { SelfTestCandidate(id: $0.id, word: $0.word, sense: $0.sense) },
                    library: library.map { SelfTestCandidate(id: $0.id, word: $0.word, sense: $0.sense) }
                )
            }.value
            guard generation == loadGeneration else { return }
            scopedCandidates = loaded.scoped
            libraryCandidates = loaded.library
            startRound()
        } catch {
            guard generation == loadGeneration else { return }
            scopedCandidates = []
            libraryCandidates = []
            state = .error
        }
    }

    public func retryLoad() {
        Task { await load() }
    }

    public func pick(_ optionID: String) {
        guard var session else { return }
        guard session.select(optionID) else { return }
        self.session = session
        state = .question(questionState(for: session))
    }

    public func next() {
        guard var session else { return }
        guard session.next() else { return }
        self.session = session
        if let summary = session.summary {
            state = .summary(P25SummaryState(total: summary.total, correct: summary.correct, wrong: summary.wrong))
        } else if let question = session.current {
            state = .question(questionState(for: session))
        }
    }

    /// 再测一轮只重建内存 session，不重读数据库、不写任何用户表。
    public func retryRound() {
        guard session?.isComplete == true else { return }
        startRound()
    }

    public func askExit() {
        if case .question = state {
            exitAsking = true
        } else {
            leave()
        }
    }

    public func cancelExit() {
        exitAsking = false
    }

    public func confirmExit() {
        leave()
    }

    /// 小结、空态、错误或加载态的退出没有未完成进度，不需要二次确认。
    public func leave() {
        loadGeneration = UUID()
        session = nil
        scopedCandidates = []
        libraryCandidates = []
        exitAsking = false
        state = .loading
        onExit()
    }

    private func startRound() {
        let result = SelfTestRoundBuilder.make(
            scoped: scopedCandidates,
            library: libraryCandidates,
            randomSource: &randomSource
        )
        switch result {
        case .emptyScope:
            session = nil
            state = .emptyScope
        case .insufficientCandidates:
            session = nil
            state = .insufficientCandidates
        case .ready(let session):
            self.session = session
            state = .question(questionState(for: session))
        }
    }

    private func questionState(for session: SelfTestSession) -> P25QuestionState {
        P25QuestionState(
            question: session.current ?? session.questions[session.index],
            number: session.index + 1,
            total: session.questions.count,
            selectedOptionID: session.selectedOptionID,
            correctCount: session.correctCount
        )
    }
}

private struct LoadedLibrary: Sendable {
    let scoped: [SelfTestCandidate]
    let library: [SelfTestCandidate]
}
