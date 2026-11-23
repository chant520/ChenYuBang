import Foundation
import Combine
import CangyuData

/// P11 页面状态（纯数据，快照测试直接喂；信息架构 v1.4 P11 ①–⑥）。
public struct P11ReviewState: Equatable, Sendable {
    public var loading: Bool
    public var failed: Bool
    public var items: [ReviewQueueItem]
    public var index: Int
    /// 本题所选（A–D）；判分后保持展示。
    public var picked: String?
    public var judged: Bool
    /// 本题成绩已处理（可比对题＝已落 `review_log`；无法比对题＝已按口径跳过）。
    public var recorded: Bool
    /// 成绩落库失败：红条可重试，「下一题」在落库成功前不可用（不丢成绩）。
    public var recordFailed: Bool
    public var correctCount: Int
    public var wrongCount: Int
    /// 可比对并已判分的题数；无法比对的题只计题量（第 8.2 节）。
    public var answeredCount: Int
    /// 退出二次确认弹层（第 8.2 节：退出中途要二次确认）。
    public var exitAsking: Bool

    public init(
        loading: Bool = true,
        failed: Bool = false,
        items: [ReviewQueueItem] = [],
        index: Int = 0,
        picked: String? = nil,
        judged: Bool = false,
        recorded: Bool = false,
        recordFailed: Bool = false,
        correctCount: Int = 0,
        wrongCount: Int = 0,
        answeredCount: Int = 0,
        exitAsking: Bool = false
    ) {
        self.loading = loading
        self.failed = failed
        self.items = items
        self.index = index
        self.picked = picked
        self.judged = judged
        self.recorded = recorded
        self.recordFailed = recordFailed
        self.correctCount = correctCount
        self.wrongCount = wrongCount
        self.answeredCount = answeredCount
        self.exitAsking = exitAsking
    }

    public var current: ReviewQueueItem? {
        items.indices.contains(index) ? items[index] : nil
    }

    /// ①：`第 N 题 / 共 M 题`。
    public var progressText: String {
        "第 \(index + 1) 题 / 共 \(items.count) 题"
    }

    public var isLast: Bool { index == items.count - 1 }

    /// 本题有可选项（缺项按「暂无」展示但不可选；全缺项的题无法作答比对）。
    public var canAnswer: Bool {
        current.map { $0.options.contains(where: { $0 != nil }) } ?? false
    }

    /// 「下一题」只在判分且成绩落库后可用。
    public var canGoNext: Bool { judged && recorded && !recordFailed }
}

/// P11 回调打包。
public struct P11ReviewActions {
    public let onPick: (String) -> Void
    public let onNext: () -> Void
    public let onAskExit: () -> Void
    public let onLeave: () -> Void
    public let onRetryLoad: () -> Void
    public let onRetryRecord: () -> Void

    public init(
        onPick: @escaping (String) -> Void,
        onNext: @escaping () -> Void,
        onAskExit: @escaping () -> Void,
        onLeave: @escaping () -> Void,
        onRetryLoad: @escaping () -> Void,
        onRetryRecord: @escaping () -> Void
    ) {
        self.onPick = onPick
        self.onNext = onNext
        self.onAskExit = onAskExit
        self.onLeave = onLeave
        self.onRetryLoad = onRetryLoad
        self.onRetryRecord = onRetryRecord
    }
}

/// P11 状态与判分落库（开发规范第 8.2 节 2026-11-23 版内修订）。
@MainActor
public final class P11ReviewViewModel: ObservableObject {
    @Published public private(set) var state = P11ReviewState()

    private let reader: ReviewQueueReading
    private let scope: ReviewScope
    private let queueIDs: [String]
    /// 最后一题「下一题」进 P12：交出本轮 题量 / 判分题数 / 答对数（无法比对的题只计题量）。
    private let onRoundFinished: (ReviewRoundOutcome) -> Void
    /// 二次确认后退出（回来源页，本轮进度丢弃）。
    private let onExit: () -> Void

    public init(
        reader: ReviewQueueReading,
        scope: ReviewScope,
        queueIDs: [String],
        onRoundFinished: @escaping (ReviewRoundOutcome) -> Void,
        onExit: @escaping () -> Void
    ) {
        self.reader = reader
        self.scope = scope
        self.queueIDs = queueIDs
        self.onRoundFinished = onRoundFinished
        self.onExit = onExit
    }

    /// 队列按 `beginReview` 携带的错题 id 收窄（顺序保持第 8.2 节的 createdAt 倒序）。
    public func load() async {
        state.loading = true
        state.failed = false
        do {
            let queue = try reader.queue(idiomID: scope.idiomID)
            state.items = queue.filter { queueIDs.contains($0.id) }
            state.index = 0
            state.loading = false
            prepareCurrent()
        } catch {
            state.items = []
            state.loading = false
            state.failed = true
        }
    }

    public func retryLoad() {
        Task { await load() }
    }

    /// ②③：选完立即判分（信息架构 v1.4 P11 ③）；可比对题同事务落 `review_log`
    /// 并刷新 `reviewCount` / `lastReviewedAt`（第 8.2 节），失败给红条可重试。
    public func pick(_ letter: String) {
        guard !state.judged, state.canAnswer, ReviewBrowse.letters.contains(letter) else { return }
        state.picked = letter
        state.judged = true
        state.recorded = false
        state.recordFailed = false
        switch ReviewBrowse.judge(first: state.current?.myAnswer, picked: letter) {
        case .different:
            state.answeredCount += 1
            state.correctCount += 1
            recordCurrent(result: ReviewLibrary.resultRight)
        case .same:
            state.answeredCount += 1
            state.wrongCount += 1
            recordCurrent(result: ReviewLibrary.resultWrong)
        case .unknownFirst:
            // 首次「我的答案」未记录：无法比对，不落 `review_log`、不计入正确数。
            state.recorded = true
        case nil:
            break
        }
    }

    public func retryRecord() {
        guard state.recordFailed, let item = state.current else { return }
        // 答对 / 答错计数已在 pick 时记下，重试只补落库。
        switch ReviewBrowse.judge(first: item.myAnswer, picked: state.picked) {
        case .different:
            recordCurrent(result: ReviewLibrary.resultRight)
        case .same:
            recordCurrent(result: ReviewLibrary.resultWrong)
        default:
            break
        }
    }

    /// ④：「下一题」；最后一题进 P12（IA 跳转表 20 的「自动进入」由该按钮承接）。
    public func next() {
        guard state.canGoNext else { return }
        if state.isLast {
            onRoundFinished(ReviewRoundOutcome(
                total: state.items.count,
                answered: state.answeredCount,
                correct: state.correctCount
            ))
        } else {
            state.index += 1
            state.picked = nil
            state.judged = false
            state.recorded = false
            state.recordFailed = false
            prepareCurrent()
        }
    }

    /// ⑤：退出确认弹层；确认后回来源页并丢弃本轮进度（IA 跳转表 19 / 23）。
    public func askExit() { state.exitAsking = true }
    public func cancelExit() { state.exitAsking = false }
    public func confirmExit() {
        state.exitAsking = false
        onExit()
    }

    /// 无选项的题按口径跳过作答（如实说明），不挡队列推进。
    private func prepareCurrent() {
        if let item = state.current, !item.options.contains(where: { $0 != nil }) {
            state.judged = true
            state.recorded = true
        }
    }

    private func recordCurrent(result: String) {
        guard let item = state.current else { return }
        do {
            try reader.record(mistakeID: item.id, result: result, reviewedAt: ReviewTimestamp.now())
            state.recorded = true
            state.recordFailed = false
        } catch {
            state.recordFailed = true
        }
    }
}
