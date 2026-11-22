import SwiftUI
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
    /// 编辑态草稿；nil＝只读态（工作包 2.8 / C8，同页切态、不新增路由）。
    public var draft: UserExampleDraft?
    /// 保存中：编辑表单在位、置灰。
    public var saving: Bool
    /// 删除中：二次确认弹层的确认按钮转圈。
    public var deleting: Bool
    /// 归属手选浮层（复用 P04 搜索，与 P20「手动挑成语」同一浮层）。
    public var showsIdiomPicker: Bool
    /// 单条删除的二次确认弹层。
    public var confirmDelete: Bool
    /// 当前提示（校验走中性、写库失败走红），同一屏最多一条。
    public var issue: UserExampleIssue?

    public init(
        loading: Bool = true,
        failed: Bool = false,
        missing: Bool = false,
        detail: UserExampleDetail? = nil,
        revealed: Bool = false,
        rerolling: Bool = false,
        picked: Int? = nil,
        checked: Bool = false,
        imageUnreadable: Bool = false,
        draft: UserExampleDraft? = nil,
        saving: Bool = false,
        deleting: Bool = false,
        showsIdiomPicker: Bool = false,
        confirmDelete: Bool = false,
        issue: UserExampleIssue? = nil
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
        self.draft = draft
        self.saving = saving
        self.deleting = deleting
        self.showsIdiomPicker = showsIdiomPicker
        self.confirmDelete = confirmDelete
        self.issue = issue
    }

    /// ⑤⑥⑨ 的重做与错题口径：只有标错的录入题才有遮罩与重做（信息架构 v1.4 §4.3）。
    public var isMistake: Bool { detail?.isMistake == true }

    /// 是否处于编辑态：编辑态渲染 `P10UserExampleEditForm`，与只读态、重做态互斥（开发规范 §8.1.1）。
    public var editing: Bool { draft != nil }

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
    /// 未标错的普通详情无底部红动作，删除整题只走顶部「删除」（开发规范 §8.1.1）。
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
    /// 顶部「编辑」＝同页切编辑态；「删除」＝按标错／未标错起单条二次确认（开发规范 §8.1.1）。
    public let onEdit: () -> Void
    public let onDelete: () -> Void
    /// 编辑态：取消回到只读态（不落库）、保存走同事务写库、提示条关闭。
    public let onCancelEdit: () -> Void
    public let onSave: () -> Void
    public let onDismissIssue: () -> Void
    /// 归属手选：打开／关闭 P04 浮层与选中一条成语。
    public let onOpenIdiomPicker: () -> Void
    public let onDismissIdiomPicker: () -> Void
    public let onPickIdiom: (String) -> Void
    /// 二次确认弹层的「删除」；关闭走 `COverlay` 自带的「取消」。
    public let onConfirmDelete: () -> Void
    public let onDismissDelete: () -> Void
    /// 编辑态草稿的输入回调（只读态不触达）。
    public let edit: UserExampleEditActions
    /// 手选浮层内容，由 app 层注入 P04 搜索（与 P20 的 `manualPicker` 同一形态）。
    public let idiomPicker: (@escaping (String) -> Void, @escaping () -> Void) -> AnyView

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
        onDelete: @escaping () -> Void,
        onCancelEdit: @escaping () -> Void = {},
        onSave: @escaping () -> Void = {},
        onDismissIssue: @escaping () -> Void = {},
        onOpenIdiomPicker: @escaping () -> Void = {},
        onDismissIdiomPicker: @escaping () -> Void = {},
        onPickIdiom: @escaping (String) -> Void = { _ in },
        onConfirmDelete: @escaping () -> Void = {},
        onDismissDelete: @escaping () -> Void = {},
        edit: UserExampleEditActions = .inert,
        idiomPicker: (
            @escaping (String) -> Void, @escaping () -> Void
        ) -> AnyView = { _, _ in AnyView(EmptyView()) }
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
        self.onCancelEdit = onCancelEdit
        self.onSave = onSave
        self.onDismissIssue = onDismissIssue
        self.onOpenIdiomPicker = onOpenIdiomPicker
        self.onDismissIdiomPicker = onDismissIdiomPicker
        self.onPickIdiom = onPickIdiom
        self.onConfirmDelete = onConfirmDelete
        self.onDismissDelete = onDismissDelete
        self.edit = edit
        self.idiomPicker = idiomPicker
    }
}
