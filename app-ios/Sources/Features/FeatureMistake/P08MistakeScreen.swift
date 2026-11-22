import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P08 读库口子：页面只依赖协议，读库实现由 app 注入（不把 DatabaseQueue 带进视图）。
public protocol MistakeBookReading {
    func overview() throws -> MistakeOverview
    func idiomGroups() throws -> [MistakeIdiomGroup]
    func rows(order: MistakeRowOrder) throws -> [MistakeRow]
}

extension MistakeLibraryReader: MistakeBookReading {}

/// P08 页面状态（纯数据，快照测试直接喂，无需 DB / 路由）。
public struct MistakeBookState: Equatable, Sendable {
    public var loading: Bool
    public var failed: Bool
    public var dimension: MistakeDimension
    public var total: Int
    public var pending: Int
    public var groups: [MistakeGroupView]
    public var entries: [MistakeEntryView]

    public init(
        loading: Bool = true,
        failed: Bool = false,
        dimension: MistakeDimension = .grouped,
        total: Int = 0,
        pending: Int = 0,
        groups: [MistakeGroupView] = [],
        entries: [MistakeEntryView] = []
    ) {
        self.loading = loading
        self.failed = failed
        self.dimension = dimension
        self.total = total
        self.pending = pending
        self.groups = groups
        self.entries = entries
    }

    public var isEmpty: Bool {
        dimension == .grouped ? groups.isEmpty : entries.isEmpty
    }
}

/// P08 回调打包。
public struct MistakeBookActions {
    public let onSelectDimension: (MistakeDimension) -> Void
    public let onOpenIdiom: (String) -> Void
    public let onOpenUserExample: (String) -> Void
    public let onCapture: () -> Void
    public let onRetry: () -> Void

    public init(
        onSelectDimension: @escaping (MistakeDimension) -> Void,
        onOpenIdiom: @escaping (String) -> Void,
        onOpenUserExample: @escaping (String) -> Void,
        onCapture: @escaping () -> Void,
        onRetry: @escaping () -> Void
    ) {
        self.onSelectDimension = onSelectDimension
        self.onOpenIdiom = onOpenIdiom
        self.onOpenUserExample = onOpenUserExample
        self.onCapture = onCapture
        self.onRetry = onRetry
    }
}

/// P08 纯渲染层（信息架构 v1.4 P08 ①–⑥ / 高保真稿 B P08）：
/// 页名 + 拍照入口、总览两数、三视图切换、列表、复习按钮、空态。
/// 悬浮拍照按钮（⑦）由 `AppRouteHost` 的 `CFab` 承担，不在本页。
public struct P08MistakeContent: View {
    private let state: MistakeBookState
    private let actions: MistakeBookActions
    private let colors: TokenColors

    public init(state: MistakeBookState, actions: MistakeBookActions, colors: TokenColors) {
        self.state = state
        self.actions = actions
        self.colors = colors
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                header
                overviewCard
                CSegmentedControl(
                    items: MistakeDimension.allCases.map(\.label),
                    selection: Binding(
                        get: { state.dimension.rawValue },
                        set: { actions.onSelectDimension(MistakeDimension(rawValue: $0) ?? .grouped) }
                    )
                )
                content
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.top, Tokens.Space.contentTop)
            .padding(.bottom, Tokens.Space.block)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
    }

    /// ①：页名「错题」+ 右上「拍照录入题库」（文案取产品 v1.6 C1 / 信息架构 v1.4 P08 ①）。
    private var header: some View {
        HStack(spacing: 0) {
            Text("错题")
                .font(.system(size: Tokens.Font.n20, weight: .semibold))
                .foregroundStyle(colors.text)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            CButton(text: "拍照录入题库", variant: .top, icon: .camera, action: actions.onCapture)
        }
    }

    /// ②：错题总数与待复习数；待复习用 `--hot`（高保真稿 B P08 `.numv.hot`）。
    private var overviewCard: some View {
        CCard {
            // `.nums` gap 30px（页面无对应令牌，取页面落地值）。
            HStack(alignment: .top, spacing: 30) {
                number(value: state.total, label: "错题总数", hot: false)
                number(value: state.pending, label: "待复习", hot: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func number(value: Int, label: String, hot: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("\(value)")
                .font(.system(size: Tokens.Font.n30, weight: .bold))
                // letter-spacing .01em、行高 1（规范 3.1 `.numv`）。
                .tracking(Tokens.Font.n30 * 0.01)
                .lineSpacing(0)
                .foregroundStyle(hot ? colors.hot : colors.head)
            Text(label)
                .font(.system(size: Tokens.Font.n11))
                // letter-spacing .06em、上距 9px（页面 `.numl`）。
                .tracking(Tokens.Font.n11 * 0.06)
                .foregroundStyle(colors.text2)
                .padding(.top, 9)
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var content: some View {
        if state.failed {
            CBar(
                kind: .warn,
                title: "错题本读取失败，请重试",
                subtitle: "只重新读取本机数据，不影响已录入的题目。",
                actionTitle: "重试",
                onAction: actions.onRetry
            )
        } else if state.loading {
            HStack {
                Spacer(minLength: 0)
                CSpinner()
                Spacer(minLength: 0)
            }
        } else if state.isEmpty {
            emptyState
        } else {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                list
                // ④：按钮在位、置灰，接 P11 全局复习留到工作包 3.1（开发排期 v1.2）。
                CButton(text: MistakeBrowse.reviewTitle(pending: state.pending), enabled: false) {}
            }
        }
    }

    @ViewBuilder
    private var list: some View {
        if state.dimension == .grouped {
            CList {
                ForEach(Array(state.groups.enumerated()), id: \.element.id) { index, group in
                    // 分组行只有主词与右侧计数，元信息留空（`CListRow` 的 meta 无默认值）。
                    CListRow(
                        name: group.word,
                        action: { actions.onOpenIdiom(group.id) },
                        showsDivider: index != state.groups.count - 1,
                        meta: { EmptyView() },
                        trailing: { Text(group.countText) }
                    )
                }
            }
        } else {
            CList {
                ForEach(Array(state.entries.enumerated()), id: \.element.id) { index, entry in
                    // 平铺行点入 P10 录入题详情（§4.2 第 18 条）；v2 迁移前无录入题来源的行不给导航。
                    CListRow(
                        stem: entry.stem,
                        action: entry.userExampleID.map { id in { actions.onOpenUserExample(id) } },
                        showsDivider: index != state.entries.count - 1,
                        meta: {
                            Text(entry.dateText)
                            Text(entry.idiomsText)
                        }
                    )
                }
            }
        }
    }

    /// ⑥：空态文案与插画位取信息架构 v1.4 §5.4（P08 一条错题都没有：「插画位（此处用 SVG 矢量图标）」）；
    /// 图形与高保真稿 D P08 空态帧 `.eic` 一致（相机）。
    private var emptyState: some View {
        CEmptyState(
            title: "还没有错题",
            description: "拍一张卷子上的题录进题库，标记为做错后就会归到对应成语下",
            icon: .camera,
            actionTitle: "拍照录入第一道题",
            onAction: actions.onCapture
        )
        .padding(.vertical, Tokens.Space.block)
    }
}

/// P08 页面状态与行为（读库失败＝橙条可重试，与 P03 同一口径）。
@MainActor
public final class MistakeBookViewModel: ObservableObject {
    @Published public private(set) var state = MistakeBookState()

    private let reader: MistakeBookReading

    public init(reader: MistakeBookReading) {
        self.reader = reader
    }

    public func load(dimension: MistakeDimension = .grouped) async {
        state.loading = true
        state.failed = false
        state.dimension = dimension
        do {
            let overview = try reader.overview()
            state.total = overview.total
            state.pending = overview.pending
            try loadList(dimension)
            state.loading = false
        } catch {
            state.groups = []
            state.entries = []
            state.loading = false
            state.failed = true
        }
    }

    /// ③：切视图只重读当前视图需要的数据，总览数保持不变。
    public func select(_ dimension: MistakeDimension) async {
        guard dimension != state.dimension else { return }
        state.dimension = dimension
        do {
            try loadList(dimension)
        } catch {
            state.groups = []
            state.entries = []
            state.failed = true
        }
    }

    private func loadList(_ dimension: MistakeDimension) throws {
        if dimension == .grouped {
            state.groups = try reader.idiomGroups().map(MistakeBrowse.view(of:))
            state.entries = []
        } else {
            state.entries = try reader.rows(order: dimension.rowOrder).map(MistakeBrowse.view(of:))
            state.groups = []
        }
    }
}

/// P08 错题本（一级页，四主标签之一）。
public struct P08MistakeScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: MistakeBookViewModel

    private let onOpenIdiom: (String) -> Void
    private let onOpenUserExample: (String) -> Void
    private let onCapture: () -> Void
    /// 用户内容写入令牌（开发规范 §8.1.1「来源页列表刷新」）：本页在标签栈里常驻挂载，
    /// 从 P10 返回时 `.task` 不会重跑，靠该令牌变化重新读库。
    private let contentRevision: UUID

    @MainActor
    public init(
        reader: MistakeBookReading,
        contentRevision: UUID = UUID(),
        onOpenIdiom: @escaping (String) -> Void,
        onOpenUserExample: @escaping (String) -> Void,
        onCapture: @escaping () -> Void
    ) {
        _model = StateObject(wrappedValue: MistakeBookViewModel(reader: reader))
        self.onOpenIdiom = onOpenIdiom
        self.onOpenUserExample = onOpenUserExample
        self.onCapture = onCapture
        self.contentRevision = contentRevision
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        return P08MistakeContent(
            state: model.state,
            actions: MistakeBookActions(
                onSelectDimension: { dimension in Task { await model.select(dimension) } },
                onOpenIdiom: onOpenIdiom,
                onOpenUserExample: onOpenUserExample,
                onCapture: onCapture,
                onRetry: { Task { await model.load(dimension: model.state.dimension) } }
            ),
            colors: colors
        )
        .task { await model.load() }
        .onChange(of: contentRevision) { _ in
            Task { await model.load(dimension: model.state.dimension) }
        }
    }
}
