import CangyuData

/// P10 读库口子：录入题详情（页面只依赖协议，读库实现由 app 注入）。
public protocol UserExampleReading {
    /// - Parameter id: `user_example.id`。路由键沿用开发规范 §5.1 的 `mistake/{id}`，
    ///   值取录入题 id：未标错的录入题没有 `mistake` 行，只有 `user_example.id` 能同时覆盖两类。
    func userExample(id: String) throws -> UserExampleDetail?
}

extension MistakeLibraryReader: UserExampleReading {}

/// P10 页面状态（纯数据，快照测试直接喂，无需 DB / 路由 / 图片）。
public struct UserExampleState: Equatable, Sendable {
    public var loading: Bool
    public var failed: Bool
    /// 录入题已被删除（或整包替换后 id 失配），按「没有找到这道题」处理。
    public var missing: Bool
    public var detail: UserExampleDetail?
    /// ⑤ 答案遮罩：false＝遮住（默认），true＝显示 ⑨ 的「我的答案」。
    public var revealed: Bool
    /// ⑤ 重做态：同页状态切换，不新增页面（信息架构 v1.4 §4.2 第 13 条）。
    public var rerolling: Bool
    /// 重做本轮所选下标（0–3）。
    public var picked: Int?
    /// 已「对答案」：显示与首次作答的比对结果。
    public var checked: Bool
    /// `imagePath` 有值但本机文件读不出＝「原图已不在」。
    public var imageUnreadable: Bool

    public init(
        loading: Bool = true,
        failed: Bool = false,
        missing: Bool = false,
        detail: UserExampleDetail? = nil,
        revealed: Bool = false,
        rerolling: Bool = false,
        picked: Int? = nil,
        checked: Bool = false,
        imageUnreadable: Bool = false
    ) {
        self.loading = loading
        self.failed = failed
        self.missing = missing
        self.detail = detail
        self.revealed = revealed
        self.rerolling = rerolling
        self.picked = picked
        self.checked = checked
        self.imageUnreadable = imageUnreadable
    }

    /// ⑤⑥⑨ 的重做与错题口径：只有标错的录入题才有遮罩与重做（信息架构 v1.4 §4.3）。
    public var isMistake: Bool { detail?.isMistake == true }

    /// 首次「我的答案」的下标；未填或非法值时为 nil（不可比对）。
    public var firstPick: Int? {
        guard let answer = detail?.myAnswer?.trimmingCharacters(in: .whitespacesAndNewlines)
            else { return nil }
        return MistakeBrowse.letters.firstIndex(of: answer.uppercased())
    }

    /// ⑨ 的「我的答案」当前是否可见：标错的默认遮住，重做中直到对答案前一律遮住。
    public var answerHidden: Bool {
        guard isMistake else { return false }
        return rerolling ? !checked : !revealed
    }

    /// 遮罩开关只在非重做态可点：重做中途给看首次作答就没有重做意义了。
    public var canToggleMask: Bool { isMistake && !rerolling }

    /// 底部红描边「删除这道错题」只在标错的录入题上渲染（信息架构 v1.4 版内修订第 ④ 条）：
    /// 未标错的普通详情本版无底部红动作，删除入口只留顶部置灰「删除」，随 2.8（C8）定义。
    public var showsBottomDeleteRow: Bool { isMistake }
}

/// P10 回调打包。
public struct UserExampleActions {
    public let onBack: () -> Void
    public let onRetry: () -> Void
    public let onToggleAnswer: () -> Void
    public let onPickOption: (Int) -> Void
    public let onStartReroll: () -> Void
    public let onCheck: () -> Void
    public let onExitReroll: () -> Void
    public let onOpenIdiom: (String) -> Void
    /// 「编辑」「删除」属工作包 2.8，本版在位、置灰、不可点（信息架构 v1.4 版内修订第 ② 条）。
    public let onEdit: () -> Void
    public let onDelete: () -> Void

    public init(
        onBack: @escaping () -> Void,
        onRetry: @escaping () -> Void,
        onToggleAnswer: @escaping () -> Void,
        onPickOption: @escaping (Int) -> Void,
        onStartReroll: @escaping () -> Void,
        onCheck: @escaping () -> Void,
        onExitReroll: @escaping () -> Void,
        onOpenIdiom: @escaping (String) -> Void,
        onEdit: @escaping () -> Void,
        onDelete: @escaping () -> Void
    ) {
        self.onBack = onBack
        self.onRetry = onRetry
        self.onToggleAnswer = onToggleAnswer
        self.onPickOption = onPickOption
        self.onStartReroll = onStartReroll
        self.onCheck = onCheck
        self.onExitReroll = onExitReroll
        self.onOpenIdiom = onOpenIdiom
        self.onEdit = onEdit
        self.onDelete = onDelete
    }
}
