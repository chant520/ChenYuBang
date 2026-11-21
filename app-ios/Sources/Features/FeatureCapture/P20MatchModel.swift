import Foundation

/// P20 页面状态。归属可勾选一条或多条（至少一条才可保存）：
/// candidates 是展示窗口（初始 K=3），checked 是全部已勾选（按候选池顺序，含窗口外项），
/// 二者分开以保证「换一个」后已选归属仍可见、可取消。
public struct P20MatchState: Equatable {
    public var reviewText = ""
    public var candidates: [MatchCandidate] = []
    public var checked: [MatchCandidate] = []
    public var poolSize = 0
    public var deleteMode = false
    public var showManualPicker = false
    public var info: String?
    public var loading = true
    public var form = P20FormState()

    /// 显式写全 memberwise init（带默认值）：一旦自定义 init，编译器不再合成 memberwise init，
    /// 故必须覆盖 bootstrap / pickManual 的带参调用，同时保留 P20MatchState() 无参用法。
    public init(
        reviewText: String = "",
        candidates: [MatchCandidate] = [],
        checked: [MatchCandidate] = [],
        poolSize: Int = 0,
        deleteMode: Bool = false,
        showManualPicker: Bool = false,
        info: String? = nil,
        loading: Bool = true,
        form: P20FormState = P20FormState()
    ) {
        self.reviewText = reviewText
        self.candidates = candidates
        self.checked = checked
        self.poolSize = poolSize
        self.deleteMode = deleteMode
        self.showManualPicker = showManualPicker
        self.info = info
        self.loading = loading
        self.form = form
    }

    /// 空态仅两种来源：有文字但零命中、用户删光；加载中不算空态。OCR 无文字由 P19 失败态处理。
    public var empty: Bool { !loading && candidates.isEmpty }
    public var canCycle: Bool { poolSize > P20Matcher.windowSize }
    public var canAdd: Bool { candidates.count < P20Matcher.maxCandidates }
    public var checkedIDs: Set<String> { Set(checked.map(\.id)) }
    public var hasChecked: Bool { !checked.isEmpty }
    public var canSave: Bool {
        !loading && hasChecked && !form.saving && !form.yearInvalid && !form.answerInvalid
    }

    /// 多选或已选项不在当前窗口时，展示「已选归属」标签行，避免窗口切换后丢失可撤销入口。
    public var showsCheckedChips: Bool {
        checked.count > 1 || checked.contains { item in
            !candidates.contains { $0.id == item.id }
        }
    }
}

/// P20 状态机（纯同步、无写库），对齐 Android P20MatchViewModel。
/// 候选生成见 P20CandidatePool；页面装配全库词表后调用 bootstrap 生成候选；
/// 勾选 / 换加删 / 手选 / 表单均同步，保存由页面层异步调用 UserExampleSaver，失败留在本页。
@MainActor
public final class P20MatchModel: ObservableObject {
    @Published public internal(set) var state = P20MatchState()

    private var allIdioms: [MatchIdiom] = []
    private var pool: [MatchCandidate] = []
    private var checkedIDs: Set<String> = []
    private var bestId: String?
    private var windowStart = 0

    public init() {}

    /// 首屏装配：全库词表按 frequency DESC / word ASC 备好供「加一个」，再生成候选池与初始窗口。
    /// 默认勾最匹配一项；最匹配不在首批时替换末位，保证进页就能看到。
    public func bootstrap(text: String, idioms: [MatchIdiom]) {
        allIdioms = idioms.sorted { lhs, rhs in
            if lhs.frequency != rhs.frequency { return lhs.frequency > rhs.frequency }
            return lhs.word < rhs.word
        }
        let result = P20Matcher.buildPool(text: text, idioms: idioms)
        pool = result.candidates
        bestId = result.bestId
        windowStart = 0
        checkedIDs = []
        let window = initialWindow()
        if let first = bestId ?? window.first?.id { checkedIDs.insert(first) }
        state = P20MatchState(
            reviewText: text,
            candidates: window,
            checked: checkedCandidates(),
            poolSize: pool.count,
            loading: false
        )
    }

    /// 点候选行或「已选归属」标签：未选加入，已选取消；窗口外已选项也可在此移除。
    /// 取消最后一条后保存禁用，不自动补选。
    public func select(id: String) {
        let known = state.candidates.contains { $0.id == id }
            || state.checked.contains { $0.id == id }
        guard known else { return }
        if checkedIDs.contains(id) {
            checkedIDs.remove(id)
        } else {
            checkedIDs.insert(id)
        }
        syncChecked()
        state.info = nil
    }

    /// 换一个：游标前进一批（K=3）取下一批替换展示，到池尾回卷池首；池 ≤K 禁用。
    /// 已勾选集合不随窗口变化，窗口外已选项由「已选归属」标签行保留撤销入口。
    public func cycle() {
        guard pool.count > P20Matcher.windowSize else {
            state.info = P20Matcher.noMoreCandidates
            return
        }
        windowStart = (windowStart + P20Matcher.windowSize) % pool.count
        state.candidates = Array(pool.dropFirst(windowStart).prefix(P20Matcher.windowSize))
        state.info = nil
    }

    /// 加一个：全库排除候选池内已有词，frequency DESC / word ASC 取一条同时进池与展示末尾；池满 50 提示。
    public func add() {
        guard pool.count < P20Matcher.maxCandidates else {
            state.info = P20Matcher.candidateLimit
            return
        }
        let inPool = Set(pool.map(\.id))
        guard let pick = allIdioms.first(where: { !inPool.contains($0.id) }) else {
            state.info = P20Matcher.noMoreCandidates
            return
        }
        let candidate = P20Matcher.addedCandidate(pick)
        pool.append(candidate)
        state.candidates.append(candidate)
        state.poolSize = pool.count
        state.info = nil
    }

    public func toggleDeleteMode() {
        state.deleteMode.toggle()
        state.info = nil
    }

    /// 删一个：从展示区和池内一并移除；若删的是已选项，同时从已选集合移除。
    /// 档位按池内剩余词命中重算：删掉最匹配后次优词命中升为最匹配。
    public func remove(id: String) {
        let before = state
        pool.removeAll { $0.id == id }
        checkedIDs.remove(id)
        if windowStart >= pool.count { windowStart = 0 }
        bestId = pool
            .filter { $0.tier == .best || $0.tier == .sameQuestion }
            .sorted { lhs, rhs in
                if lhs.frequency != rhs.frequency { return lhs.frequency > rhs.frequency }
                return lhs.word < rhs.word
            }
            .first?
            .id
        pool = pool.map { $0.relabeled(bestId) }
        state.candidates = before.candidates
            .filter { $0.id != id }
            .map { $0.relabeled(bestId) }
        syncChecked()
        state.poolSize = pool.count
        state.deleteMode = !state.candidates.isEmpty && before.deleteMode
        state.info = nil
    }

    /// 打开 / 关闭手选浮层；关闭时其余状态不变。
    public func setManualPicker(visible: Bool) {
        state.showManualPicker = visible
    }

    /// 手动增补：加入候选池并勾选，保留原有已选；已在池内则只补勾选。
    public func pickManual(id: String) {
        defer { state.showManualPicker = false }
        guard let idiom = allIdioms.first(where: { $0.id == id }) else { return }
        let candidate = P20Matcher.manualCandidate(idiom)
        if let index = pool.firstIndex(where: { $0.id == id }) {
            pool[index] = candidate
        } else {
            pool.append(candidate)
        }
        if let index = state.candidates.firstIndex(where: { $0.id == id }) {
            state.candidates[index] = candidate
        } else {
            state.candidates.append(candidate)
        }
        checkedIDs.insert(id)
        state.poolSize = pool.count
        syncChecked()
        state.info = nil
    }

    public func showInfo(_ message: String) {
        state.info = message
    }

    public func dismissInfo() {
        state.info = nil
    }

    private func initialWindow() -> [MatchCandidate] {
        var window = Array(pool.prefix(P20Matcher.windowSize))
        guard let bestId, let best = pool.first(where: { $0.id == bestId }),
              !window.contains(where: { $0.id == bestId })
        else { return window }
        if window.count >= P20Matcher.windowSize {
            window[window.count - 1] = best
        } else {
            window.append(best)
        }
        return window
    }

    private func checkedCandidates() -> [MatchCandidate] {
        pool.filter { checkedIDs.contains($0.id) }
    }

    private func syncChecked() {
        state.checked = checkedCandidates()
    }
}

/// 删后重打档位：词命中的候选按新 bestId 归位，语义相近与加/手选的标签原样保留。
private extension MatchCandidate {
    func relabeled(_ bestId: String?) -> MatchCandidate {
        switch tier {
        case .semantic, .added, .manual:
            return self
        case .best, .sameQuestion:
            return MatchCandidate(
                id: id,
                word: word,
                frequency: frequency,
                tier: id == bestId ? .best : .sameQuestion
            )
        }
    }
}
