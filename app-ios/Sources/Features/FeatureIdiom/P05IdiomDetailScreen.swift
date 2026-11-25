import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P05 详情只读查询口子：页面只依赖协议，读库实现由 app 注入。
public protocol IdiomDetailReading {
    func detail(id: String) throws -> IdiomRecord?
}

extension IdiomLibraryReader: IdiomDetailReading {}

/// P05「相关题目 · 系统题库」只读查询口子（工作包 1.6）：页面只依赖协议，读库实现由 app 注入。
public protocol IdiomExamplesReading {
    func examples(idiomID: String) throws -> [ExampleRecord]
}

extension ExampleLibraryReader: IdiomExamplesReading {}

/// P05「复习这个成语」置灰判定口子（阶段 3，决策 H）：只取该成语的待复习错题数。
/// 读库实现 `ReviewLibraryService` 属 feature-review / 数据层，feature 之间不互相引用，
/// 故在本侧声明协议并由 app 层注入实例（与 `MistakeLibraryReader: MistakeBookReading` 同构）。
public protocol PendingReviewCountReading {
    func pendingCount(idiomID: String) throws -> Int
}

extension ReviewLibraryService: PendingReviewCountReading {}

/// P05 A6 自评 / A7 收藏写入口子：页面只依赖协议，写库实现由 app 注入的 IdiomStateStore 承担。
/// 与 LibraryScreen 的 IdiomStateReading 同构，读（state）也在该协议内以便写后回读刷新。
public protocol IdiomStateWriting {
    func state(idiomID: String) throws -> IdiomLearningState
    func setStatus(idiomID: String, status: IdiomLearningStatus) throws
    func setFavorite(idiomID: String, favorite: Bool) throws
}

extension IdiomStateStore: IdiomStateWriting {}

/// P05「相关题目 · 系统题库」单行展示模型（与 Android IdiomExampleView 同口径）：
/// 题干摘要 + 「年份 · 地区 · 考试类型」+ 题型，缺字段已在 `IdiomExamples.view(of:)` 归到「暂无」。
public struct IdiomExampleView: Identifiable, Equatable, Sendable {
    public let id: String
    public let stem: String
    public let sourceLine: String
    public let questionType: String

    public init(id: String, stem: String, sourceLine: String, questionType: String) {
        self.id = id
        self.stem = stem
        self.sourceLine = sourceLine
        self.questionType = questionType
    }
}

/// 系统真题行取值（纯函数，与 Android IdiomExamples 同口径）：只做字段呈现，不改写题目内容。
public enum IdiomExamples {
    public static func view(of record: ExampleRecord) -> IdiomExampleView {
        IdiomExampleView(
            id: record.id,
            stem: IdiomDetail.present(record.stem),
            sourceLine: sourceLine(record),
            questionType: IdiomDetail.present(record.questionType)
        )
    }

    /// 年份 · 地区 · 考试类型；缺项跳过，全缺归「暂无」。
    public static func sourceLine(_ record: ExampleRecord) -> String {
        let parts = [
            record.year > 0 ? String(record.year) : nil,
            presentOrNil(record.region),
            presentOrNil(record.examType)
        ].compactMap { $0 }
        return parts.isEmpty ? IdiomDetail.placeholder : parts.joined(separator: " · ")
    }

    private static func presentOrNil(_ value: String) -> String? {
        let present = IdiomDetail.present(value)
        return present == IdiomDetail.placeholder ? nil : present
    }
}

/// P05 页面状态与行为（文案与状态语义与 Android P05IdiomDetailViewModel 一致）。
@MainActor
public final class IdiomDetailViewModel: ObservableObject {
    @Published public private(set) var detail: IdiomDetailView?
    @Published public private(set) var examples: [IdiomExampleView] = []
    @Published public private(set) var loading = true
    @Published public private(set) var missing = false
    @Published public private(set) var failed = false
    /// 该成语的待复习错题数（决策 H：0 时「复习这个成语」保持置灰）。
    @Published public private(set) var pendingReviewCount = 0
    /// 点击时最新队列已空或读取失败：来源页如实提示，不静默无反应。
    @Published public private(set) var reviewMessage: String?
    /// A6 自评 + A7 收藏当前值；缺行由 Store.state 投影补未学、未收藏。
    /// idiomID 为空表示未注入状态源，顶部保持旧占位外观（保护既有快照基线）。
    @Published public private(set) var learningState = IdiomLearningState(
        idiomID: "", status: .notStarted, favorite: false
    )

    private let reader: IdiomDetailReading
    private let examplesReader: IdiomExamplesReading
    private let reviewCounter: PendingReviewCountReading?
    private let stateStore: IdiomStateWriting?
    private var currentID = ""

    public init(
        reader: IdiomDetailReading,
        examplesReader: IdiomExamplesReading,
        reviewCounter: PendingReviewCountReading? = nil,
        stateStore: IdiomStateWriting? = nil
    ) {
        self.reader = reader
        self.examplesReader = examplesReader
        self.reviewCounter = reviewCounter
        self.stateStore = stateStore
    }

    public func load(_ id: String) async {
        currentID = id
        detail = nil
        examples = []
        missing = false
        failed = false
        pendingReviewCount = 0
        reviewMessage = nil
        learningState = IdiomLearningState(idiomID: "", status: .notStarted, favorite: false)
        loading = true
        do {
            if let record = try reader.detail(id: id) {
                detail = IdiomDetail.view(of: record)
                examples = try examplesReader.examples(idiomID: id).map(IdiomExamples.view(of:))
                pendingReviewCount = try reviewCounter?.pendingCount(idiomID: id) ?? 0
                learningState = try stateStore?.state(idiomID: id)
                    ?? IdiomLearningState(idiomID: "", status: .notStarted, favorite: false)
            } else {
                missing = true
            }
            loading = false
        } catch {
            loading = false
            failed = true
        }
    }
    public func showUnavailableReview() {
        reviewMessage = "待复习队列已变化，请刷新后再试"
    }

    public func dismissReviewMessage() {
        reviewMessage = nil
    }

    /// A6 自评：写入后回读刷新，保证缺行/并发写入后的值一致。
    public func updateStatus(_ status: IdiomLearningStatus) {
        guard !currentID.isEmpty, let stateStore else { return }
        do {
            try stateStore.setStatus(idiomID: currentID, status: status)
            learningState = try stateStore.state(idiomID: currentID)
        } catch {
            // 写后回读失败保留当前展示值；E2 概览区不额外加错误态。
        }
    }

    /// A7 收藏：写入后回读刷新，收藏标签与 P24/P13 计数走同一 Store。
    public func updateFavorite(_ favorite: Bool) {
        guard !currentID.isEmpty, let stateStore else { return }
        do {
            try stateStore.setFavorite(idiomID: currentID, favorite: favorite)
            learningState = try stateStore.state(idiomID: currentID)
        } catch {
            // 同上，写失败不改展示值。
        }
    }
}

/// P05 顶部 A6 自评入口 + A7 收藏两态（注入 StateStore 时展示，供快照直接渲染）。
public struct P05StateHeader: View {
    @Environment(\.colorScheme) private var colorScheme

    private let state: IdiomLearningState
    private let onSelfAssess: () -> Void
    private let onToggleFavorite: () -> Void

    public init(
        state: IdiomLearningState,
        onSelfAssess: @escaping () -> Void,
        onToggleFavorite: @escaping () -> Void
    ) {
        self.state = state
        self.onSelfAssess = onSelfAssess
        self.onToggleFavorite = onToggleFavorite
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        HStack(spacing: Tokens.Space.lmetaGap) {
            Button(action: onSelfAssess) {
                CTag(text: "学习状态：\(statusTag(for: state.status).text)", style: .ghost)
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel("学习状态：\(statusTag(for: state.status).text)，点按自评")
            Button(action: onToggleFavorite) {
                CTag(text: state.favorite ? "已收藏" : "收藏", style: state.favorite ? .solid : .ghost)
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel(state.favorite ? "已收藏，点按取消" : "收藏")
        }
    }
}

/// P05 成语详情长页（信息架构 v1.3 P05 ①–⑪）：五项齐全 + 相关区 / 录入题占位。
public struct P05IdiomDetailScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: IdiomDetailViewModel

    private let idiomID: String
    private let onBack: () -> Void
    private let onOpenMistakes: () -> Void
    private let onOpenExample: (String) -> Void
    /// 单成语复习入口（决策 H）：返回 false 表示点击时最新队列已空或读取失败。
    private let onReviewIdiom: (String) -> Bool
    /// P10 写成功后重载待复习数；常驻路由栈里的 `.task` 不会自行重跑。
    private let contentRevision: UUID
    /// 写状态/收藏后通知外层（P13 概览计数联动），默认空操作。
    private let onStateChanged: () -> Void
    @State private var statusPickerPresented = false

    @MainActor
    public init(
        id: String,
        reader: IdiomDetailReading,
        examplesReader: IdiomExamplesReading,
        reviewCounter: PendingReviewCountReading? = nil,
        contentRevision: UUID = UUID(),
        stateStore: IdiomStateWriting? = nil,
        onBack: @escaping () -> Void,
        onOpenMistakes: @escaping () -> Void,
        onOpenExample: @escaping (String) -> Void,
        onReviewIdiom: @escaping (String) -> Bool = { _ in false },
        onStateChanged: @escaping () -> Void = {}
    ) {
        _model = StateObject(wrappedValue: IdiomDetailViewModel(
            reader: reader,
            examplesReader: examplesReader,
            reviewCounter: reviewCounter,
            stateStore: stateStore
        ))
        self.idiomID = id
        self.onBack = onBack
        self.onOpenMistakes = onOpenMistakes
        self.onOpenExample = onOpenExample
        self.onReviewIdiom = onReviewIdiom
        self.contentRevision = contentRevision
        self.onStateChanged = onStateChanged
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                header(colors)
                content(colors)
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.contentTop)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
        .task { await model.load(idiomID) }
        .onChange(of: contentRevision) { _ in
            Task { await model.load(idiomID) }
        }
        .overlay {
            if statusPickerPresented {
                statusPicker(colors)
            }
        }
    }

    private func header(_ colors: TokenColors) -> some View {
        HStack(spacing: Tokens.Space.lmetaGap) {
            Button(action: onBack) {
                CIcon(.back, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                    .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                    .contentShape(Rectangle())
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel("返回")
            Text("成语详情")
                .font(.system(size: Tokens.Font.n15, weight: .semibold))
                .foregroundStyle(colors.head)
            Spacer(minLength: 0)
            headerActions(colors)
        }
    }

    /// 顶部右侧行为：注入 StateStore 时展示 A6 自评入口 + A7 收藏两态；
    /// idiomID 为空（未注入）时保留旧「收藏（预留）」外观，保护既有快照基线。
    @ViewBuilder
    private func headerActions(_ colors: TokenColors) -> some View {
        if model.learningState.idiomID.isEmpty {
            CTag(text: "收藏（预留）", style: .ghost)
        } else {
            P05StateHeader(
                state: model.learningState,
                onSelfAssess: { statusPickerPresented = true },
                onToggleFavorite: toggleFavorite
            )
        }
    }

    private func toggleFavorite() {
        model.updateFavorite(!model.learningState.favorite)
        onStateChanged()
    }

    /// A6 三选一自评弹层：复用 COverlay + CCheckRow 单选语义，点选即生效并回写顶部标签。
    private func statusPicker(_ colors: TokenColors) -> some View {
        COverlay(
            style: .dialog,
            title: "学习状态",
            onDismiss: { statusPickerPresented = false },
            content: {
                VStack(spacing: 0) {
                    ForEach(Array(IdiomLearningStatus.allCases.enumerated()), id: \.element) { index, status in
                        CCheckRow(
                            label: statusTag(for: status).text,
                            isOn: Binding(
                                get: { model.learningState.status == status },
                                set: { if $0 { selectStatus(status) } }
                            ),
                            showsDivider: index != IdiomLearningStatus.allCases.count - 1
                        )
                    }
                }
            }
        )
    }

    private func selectStatus(_ status: IdiomLearningStatus) {
        model.updateStatus(status)
        onStateChanged()
        statusPickerPresented = false
    }

    @ViewBuilder
    private func content(_ colors: TokenColors) -> some View {
        if model.loading {
            CBar(kind: .info, title: "正在读取成语详情…", subtitle: "内容来自随安装包内置的题库。")
        } else if model.missing {
            CEmptyState(
                title: "没有找到这个成语",
                description: "该词条不在当前题库中，可返回成语库换一个查看。"
            )
        } else if model.failed {
            CBar(
                kind: .warn,
                title: "详情读取失败，请重试",
                subtitle: "只重新读取本机数据，不影响已录入的内容。"
            )
        } else if let detail = model.detail {
            P05DetailBody(
                detail: detail,
                colors: colors,
                pendingReviewCount: model.pendingReviewCount,
                examples: model.examples,
                onOpenMistakes: onOpenMistakes,
                onOpenExample: onOpenExample,
                onReviewIdiom: { word in
                    if !onReviewIdiom(word) { model.showUnavailableReview() }
                }
            )
            if let message = model.reviewMessage {
                CBar(
                    kind: .info,
                    title: message,
                    actionTitle: "知道了",
                    onAction: { model.dismissReviewMessage() }
                )
            }
        }
    }
}
