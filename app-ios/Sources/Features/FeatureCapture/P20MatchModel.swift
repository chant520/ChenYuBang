import Foundation

/// P20 候选档位。三档互斥、一词一档，优先级：最匹配 > 同题出现 > 语义相近。
/// - manual：空态 P04 回填专用新文案标签，沿用 CCheckRow 的 trailing 样式，不新增 token。
/// - added：「加一个」从全库补入的词，不属于命中档位，不显示标签。
public enum MatchTier: Equatable, Sendable {
    case best
    case sameQuestion
    case semantic
    case manual
    case added

    public var label: String? {
        switch self {
        case .best: return "最匹配"
        case .sameQuestion: return "同题出现"
        case .semantic: return "语义相近"
        case .manual: return "手动选择"
        case .added: return nil
        }
    }
}

/// 一行候选。frequency 只用于「最匹配」并列判定，不写库。
public struct MatchCandidate: Equatable, Sendable {
    public let id: String
    public let word: String
    public let frequency: Int
    public let tier: MatchTier

    public init(id: String, word: String, frequency: Int, tier: MatchTier) {
        self.id = id
        self.word = word
        self.frequency = frequency
        self.tier = tier
    }
}

/// 候选生成的最小输入，由成语库记录投影而来（synonyms 保持库中的 JSON 字符串原样）。
/// 不依赖数据层类型，纯函数即可单测。
public struct MatchIdiom: Equatable, Sendable {
    public let id: String
    public let word: String
    public let synonyms: String
    public let frequency: Int

    public init(id: String, word: String, synonyms: String = "[]", frequency: Int) {
        self.id = id
        self.word = word
        self.synonyms = synonyms
        self.frequency = frequency
    }
}

/// P20 页面状态。勾选数恒为 1（checkedId），换/加/删只改候选列表：
/// candidates 是展示窗口（初始 K=3），poolSize 是完整候选池大小（决定「换一个」是否可用）。
/// 所有状态只在 Model 内，不进 RouterState、不落 DB。
public struct P20MatchState: Equatable {
    public var reviewText = ""
    public var candidates: [MatchCandidate] = []
    public var checkedId: String?
    public var poolSize = 0
    public var deleteMode = false
    public var showManualPicker = false
    public var info: String?
    public var loading = true

    /// 显式写全 memberwise init（带默认值）：一旦自定义 init，编译器不再合成 memberwise init，
    /// 故必须覆盖 bootstrap / pickManual 的带参调用，同时保留 P20MatchState() 无参用法。
    public init(
        reviewText: String = "",
        candidates: [MatchCandidate] = [],
        checkedId: String? = nil,
        poolSize: Int = 0,
        deleteMode: Bool = false,
        showManualPicker: Bool = false,
        info: String? = nil,
        loading: Bool = true
    ) {
        self.reviewText = reviewText
        self.candidates = candidates
        self.checkedId = checkedId
        self.poolSize = poolSize
        self.deleteMode = deleteMode
        self.showManualPicker = showManualPicker
        self.info = info
        self.loading = loading
    }

    /// 空态仅两种来源：有文字但零命中、用户删光；加载中不算空态。OCR 无文字由 P19 失败态处理。
    public var empty: Bool { !loading && candidates.isEmpty }
    public var canCycle: Bool { poolSize > P20Matcher.windowSize }
    public var canAdd: Bool { candidates.count < P20Matcher.maxCandidates }
}

public struct P20PoolResult: Equatable {
    public let candidates: [MatchCandidate]
    public let bestId: String?

    public init(candidates: [MatchCandidate], bestId: String?) {
        self.candidates = candidates
        self.bestId = bestId
    }
}

/// P20 候选生成的纯函数集合：全库词表扫描命中 + 三档判定 + JSON 降级。
///
/// 命中口径与 Android P20Matcher 完全一致：word 原样子串全词命中，synonyms 近似扩展补候选；
/// 不做 OCR 错字模糊容错（错字在 P19 补字兜底）。候选范围＝全库，不按考试范围过滤。
/// 中文题干下该口径与 idiom_fts 的 word 全词命中等价，且 synonyms 列不在 FTS 索引需应用层解析。
public enum P20Matcher {
    /// 展示窗口批长：高保真稿 C P20 一屏三条。
    public static let windowSize = 3
    public static let maxCandidates = 50

    public static let emptyTitle = "没匹配到成语，可手动选一个"
    public static let manualAction = "手动挑成语"
    public static let noMoreCandidates = "没有更多候选"
    public static let candidateLimit = "候选最多 50 条"
    public static let saveInfo = "保存功能下一步开放"

    /// synonyms 是 JSON 字符串列，解析失败 / 缺失 / 非法一律降级空数组，不抛到 UI。
    public static func synonyms(of raw: String) -> [String] {
        guard let data = raw.data(using: .utf8),
              let array = try? JSONDecoder().decode([String].self, from: data)
        else { return [] }
        return array
    }

    /// 命中 → 去重 → 按 word 升序取前 50；bestId 在 word 命中词里 frequency 高优先、同频 word 升序取第一条。
    /// 一个词同时命中 word 与 synonyms 时只按最高档归位。
    public static func buildPool(text: String, idioms: [MatchIdiom]) -> P20PoolResult {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !idioms.isEmpty else {
            return P20PoolResult(candidates: [], bestId: nil)
        }
        var hits: [String: (idiom: MatchIdiom, wordHit: Bool)] = [:]
        var order: [String] = []
        for idiom in idioms {
            let wordHit = !idiom.word.isEmpty && text.contains(idiom.word)
            let synonymHit = !wordHit && synonyms(of: idiom.synonyms).contains {
                !$0.isEmpty && text.contains($0)
            }
            if (wordHit || synonymHit) && hits[idiom.id] == nil {
                hits[idiom.id] = (idiom, wordHit)
                order.append(idiom.id)
            }
        }
        let ordered: [(idiom: MatchIdiom, wordHit: Bool)] = order
            .compactMap { hits[$0] }
            .sorted { $0.idiom.word < $1.idiom.word }
        let pool = Array(ordered.prefix(maxCandidates))
        let bestId = pool
            .filter { $0.wordHit }
            .sorted { lhs, rhs in
                if lhs.idiom.frequency != rhs.idiom.frequency {
                    return lhs.idiom.frequency > rhs.idiom.frequency
                }
                return lhs.idiom.word < rhs.idiom.word
            }
            .first?
            .idiom.id
        let candidates = pool.map { entry -> MatchCandidate in
            let tier: MatchTier
            if entry.idiom.id == bestId {
                tier = .best
            } else if entry.wordHit {
                tier = .sameQuestion
            } else {
                tier = .semantic
            }
            return MatchCandidate(
                id: entry.idiom.id,
                word: entry.idiom.word,
                frequency: entry.idiom.frequency,
                tier: tier
            )
        }
        return P20PoolResult(candidates: candidates, bestId: bestId)
    }

    /// 「加一个」从全库未展示词中取一条附加候选；无档位标签。
    public static func addedCandidate(_ idiom: MatchIdiom) -> MatchCandidate {
        MatchCandidate(id: idiom.id, word: idiom.word, frequency: idiom.frequency, tier: .added)
    }

    /// 空态手动回填：覆盖候选区、默认勾选、标「手动选择」。
    public static func manualCandidate(_ idiom: MatchIdiom) -> MatchCandidate {
        MatchCandidate(id: idiom.id, word: idiom.word, frequency: idiom.frequency, tier: .manual)
    }
}

/// P20 状态机（纯同步、无写库），对齐 Android P20MatchViewModel。
/// 页面装配全库词表后调用 bootstrap 生成候选；其余操作均同步，便于单测覆盖三档 / 勾选 / 换加删 / 空态。
@MainActor
public final class P20MatchModel: ObservableObject {
    @Published public private(set) var state = P20MatchState()

    private var allIdioms: [MatchIdiom] = []
    private var pool: [MatchCandidate] = []
    private var bestId: String?
    private var windowStart = 0

    public init() {}

    /// 首屏装配：全库词表按 frequency DESC / word ASC 备好供「加一个」，再生成候选池与初始窗口。
    public func bootstrap(text: String, idioms: [MatchIdiom]) {
        allIdioms = idioms.sorted { lhs, rhs in
            if lhs.frequency != rhs.frequency { return lhs.frequency > rhs.frequency }
            return lhs.word < rhs.word
        }
        let result = P20Matcher.buildPool(text: text, idioms: idioms)
        pool = result.candidates
        bestId = result.bestId
        windowStart = 0
        let window = Array(pool.prefix(P20Matcher.windowSize))
        state = P20MatchState(
            reviewText: text,
            candidates: window,
            checkedId: Self.firstCheck(window, bestId),
            poolSize: pool.count,
            loading: false
        )
    }

    /// 用户点选某一行：勾选恒 1 条，点已勾选项不取消。
    public func select(id: String) {
        guard state.candidates.contains(where: { $0.id == id }) else { return }
        state.checkedId = id
    }

    /// 换一个：游标前进一批（K=3）取下一批替换展示，到池尾回卷池首；池 ≤K 禁用。
    public func cycle() {
        guard pool.count > P20Matcher.windowSize else {
            state.info = P20Matcher.noMoreCandidates
            return
        }
        windowStart = (windowStart + P20Matcher.windowSize) % pool.count
        let next = Array(pool.dropFirst(windowStart).prefix(P20Matcher.windowSize))
        state.candidates = next
        state.checkedId = Self.firstCheck(next, bestId)
        state.info = nil
    }

    /// 加一个：全库排除当前展示词，frequency DESC / word ASC 取一条追加末尾；满 50 禁用。
    public func add() {
        let current = state.candidates
        guard current.count < P20Matcher.maxCandidates else {
            state.info = P20Matcher.candidateLimit
            return
        }
        let shown = Set(current.map { $0.id })
        guard let pick = allIdioms.first(where: { !shown.contains($0.id) }) else {
            state.info = P20Matcher.noMoreCandidates
            return
        }
        state.candidates = current + [P20Matcher.addedCandidate(pick)]
        state.info = nil
    }

    public func toggleDeleteMode() {
        state.deleteMode.toggle()
        state.info = nil
    }

    /// 删一个：移除该条（同时从池移除，避免换一个又带回来）；删光进空态并退出删除态。
    /// 删掉当前勾选项时，在剩余 word 命中词里按「最匹配」规则重选。
    public func remove(id: String) {
        let next = state.candidates.filter { $0.id != id }
        pool.removeAll { $0.id == id }
        if windowStart >= pool.count { windowStart = 0 }
        bestId = next
            .filter { $0.tier == .best || $0.tier == .sameQuestion }
            .sorted { lhs, rhs in
                if lhs.frequency != rhs.frequency { return lhs.frequency > rhs.frequency }
                return lhs.word < rhs.word
            }
            .first?
            .id
        state.candidates = next
        state.checkedId = Self.firstCheck(next, bestId)
        state.poolSize = pool.count
        state.deleteMode = !next.isEmpty && state.deleteMode
        state.info = nil
    }

    /// 打开 / 关闭手选浮层；关闭时其余状态不变。
    public func setManualPicker(visible: Bool) {
        state.showManualPicker = visible
    }

    /// 选中回填：覆盖候选区、默认勾该条、标「手动选择」，关闭浮层。
    public func pickManual(id: String) {
        guard let idiom = allIdioms.first(where: { $0.id == id }) else {
            state.showManualPicker = false
            return
        }
        let candidate = P20Matcher.manualCandidate(idiom)
        pool = [candidate]
        bestId = nil
        windowStart = 0
        state = P20MatchState(
            reviewText: state.reviewText,
            candidates: [candidate],
            checkedId: candidate.id,
            poolSize: 1,
            loading: false
        )
    }

    public func showInfo(_ message: String) {
        state.info = message
    }

    public func dismissInfo() {
        state.info = nil
    }

    /// 勾选跟着最匹配走；最匹配不在本批时退到本批首条，始终保证恰好一条。
    private static func firstCheck(_ display: [MatchCandidate], _ bestId: String?) -> String? {
        guard !display.isEmpty else { return nil }
        if let bestId, display.contains(where: { $0.id == bestId }) { return bestId }
        return display.first?.id
    }
}
