import Foundation
import Combine
import CangyuData

/// P12 页面状态（纯数据，快照测试直接喂；信息架构 v1.4 P12 ①–⑥）。
public struct P12SummaryState: Equatable, Sendable {
    public var loading: Bool
    public var failed: Bool
    public var scope: ReviewScope
    /// 本轮题量（含无法比对的题）。
    public var total: Int
    /// 答对（与首次作答不同）。
    public var correct: Int
    /// 答错（与首次作答相同）。
    public var wrong: Int
    /// 涉及成语列表（去重、按词面升序，每个可点进 P05）。
    public var idioms: [LinkedIdiom]
    /// 再复习点击时正在重查当前待复习队列。
    public var retrying: Bool
    /// 最新队列为空/读取失败/路由已失效的中性提示。
    public var retryIssue: String?

    public init(
        loading: Bool = true,
        failed: Bool = false,
        scope: ReviewScope = .global(),
        total: Int = 0,
        correct: Int = 0,
        wrong: Int = 0,
        idioms: [LinkedIdiom] = [],
        retrying: Bool = false,
        retryIssue: String? = nil
    ) {
        self.loading = loading
        self.failed = failed
        self.scope = scope
        self.total = total
        self.correct = correct
        self.wrong = wrong
        self.idioms = idioms
        self.retrying = retrying
        self.retryIssue = retryIssue
    }
}

/// P12 回调打包。
public struct P12SummaryActions {
    public let onOpenIdiom: (String) -> Void
    public let onHome: () -> Void
    public let onRetryRound: () -> Void
    public let onRetryLoad: () -> Void

    public init(
        onOpenIdiom: @escaping (String) -> Void,
        onHome: @escaping () -> Void,
        onRetryRound: @escaping () -> Void,
        onRetryLoad: @escaping () -> Void
    ) {
        self.onOpenIdiom = onOpenIdiom
        self.onHome = onHome
        self.onRetryRound = onRetryRound
        self.onRetryLoad = onRetryLoad
    }
}

/// P12 状态：数字来自本轮内存小结（`review_log` 无轮次概念，不按库重算），
/// 涉及成语按本轮队列的错题实时查库（第 8.2 节）。
@MainActor
public final class P12SummaryViewModel: ObservableObject {
    @Published public private(set) var state: P12SummaryState

    private let reader: ReviewQueueReading
    private let queueIDs: [String]

    public init(
        reader: ReviewQueueReading,
        scope: ReviewScope,
        summary: ReviewRoundOutcome,
        queueIDs: [String]
    ) {
        self.reader = reader
        self.queueIDs = queueIDs
        state = P12SummaryState(
            scope: scope,
            total: summary.total,
            correct: summary.correct,
            // 答错只算可比对的题；无法比对的题只进题量（第 8.2 节）。
            wrong: summary.wrong
        )
    }

    public func load() async {
        state.loading = true
        state.failed = false
        do {
            state.idioms = try reader.idiomsInRound(mistakeIDs: queueIDs)
            state.loading = false
        } catch {
            state.idioms = []
            state.loading = false
            state.failed = true
        }
    }

    public func retryLoad() {
        Task { await load() }
    }

    /// C9：再复习只按当前 scope 重查待复习队列；已掌握题不会因旧 queueIDs 复活。
    public func retryRound(onStart: ([String]) -> Bool) async {
        guard !state.retrying else { return }
        state.retrying = true
        state.retryIssue = nil
        defer { state.retrying = false }
        do {
            let ids = try reader.queue(idiomID: state.scope.idiomID).map(\.id)
            guard !ids.isEmpty else {
                state.retryIssue = "当前范围已没有待复习的错题"
                return
            }
            if !onStart(ids) {
                state.retryIssue = "本轮复习已失效，请回来源页重试"
            }
        } catch {
            state.retryIssue = "待复习队列读取失败，请重试"
        }
    }
}
