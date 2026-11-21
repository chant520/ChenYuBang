import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P09 读库口子：成语头部信息（①②）+ 该成语下的全部录入题（③）。
public protocol IdiomCapturedReading {
    func capturedIdiom(idiomID: String) throws -> CapturedIdiomInfo?
    func capturedExamples(idiomID: String) throws -> [IdiomCapturedExample]
}

extension MistakeLibraryReader: IdiomCapturedReading {}

/// P09 页面状态（纯数据，快照测试直接喂，无需 DB / 路由）。
public struct IdiomCapturedState: Equatable, Sendable {
    public var loading: Bool
    public var failed: Bool
    /// 成语已不在当前题库（整包替换后的数据漂移），按「没有找到这个成语」处理。
    public var missing: Bool
    public var word: String
    public var senseLine: String
    public var examples: [CapturedExampleView]

    public init(
        loading: Bool = true,
        failed: Bool = false,
        missing: Bool = false,
        word: String = "",
        senseLine: String = "",
        examples: [CapturedExampleView] = []
    ) {
        self.loading = loading
        self.failed = failed
        self.missing = missing
        self.word = word
        self.senseLine = senseLine
        self.examples = examples
    }

    public var isEmpty: Bool { examples.isEmpty }

    /// 页名取成语名；成语缺失时退回功能名，不给空标题。
    public var pageTitle: String { word.isEmpty ? "录入题" : word }
}

/// P09 回调打包。
public struct IdiomCapturedActions {
    public let onBack: () -> Void
    public let onOpenIdiom: () -> Void
    public let onBackToBook: () -> Void
    public let onRetry: () -> Void

    public init(
        onBack: @escaping () -> Void,
        onOpenIdiom: @escaping () -> Void,
        onBackToBook: @escaping () -> Void,
        onRetry: @escaping () -> Void
    ) {
        self.onBack = onBack
        self.onOpenIdiom = onOpenIdiom
        self.onBackToBook = onBackToBook
        self.onRetry = onRetry
    }
}

/// P09 纯渲染层（信息架构 v1.4 P09 ①–④ / 高保真稿 B P09）：
/// 返回 + 成语名作页名、释义一行（可点进 P05）、录入题列表、空态。
public struct P09CapturedContent: View {
    private let state: IdiomCapturedState
    private let actions: IdiomCapturedActions
    private let colors: TokenColors

    public init(state: IdiomCapturedState, actions: IdiomCapturedActions, colors: TokenColors) {
        self.state = state
        self.actions = actions
        self.colors = colors
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                header
                content
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.top, Tokens.Space.contentTop)
            .padding(.bottom, Tokens.Space.block)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
    }

    /// ①：返回 + 以成语名作页名（二级页页名 15pt，与 P05 同款）。
    private var header: some View {
        HStack(spacing: Tokens.Space.lmetaGap) {
            Button(action: actions.onBack) {
                CIcon(.back, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                    .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                    .contentShape(Rectangle())
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel("返回")
            Text(state.pageTitle)
                .font(.system(size: Tokens.Font.n15, weight: .semibold))
                .foregroundStyle(colors.head)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private var content: some View {
        if state.failed {
            CBar(
                kind: .warn,
                title: "录入题读取失败，请重试",
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
        } else if state.missing {
            CEmptyState(
                title: "没有找到这个成语",
                description: "该词条已不在当前题库中，可返回错题本换一个查看。"
            )
        } else {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                senseCard
                if state.isEmpty {
                    emptyState
                } else {
                    list
                    footer
                }
            }
        }
    }

    /// ②：释义一行，整行可点进 P05（页面 `.hint` + `.ic.sm` 右箭头）。
    private var senseCard: some View {
        CCard {
            Button(action: actions.onOpenIdiom) {
                HStack(alignment: .center, spacing: Tokens.Space.ghostY) {
                    Text(state.senseLine)
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(colors.text3)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                    CIcon(.chevron, size: Tokens.Icon.n15, strokeWidth: 1.6, color: colors.accent)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel("查看成语详情")
        }
    }

    private var list: some View {
        VStack(alignment: .leading, spacing: 0) {
            CLabel("我录入的题（标错的按错题展示）")
                .padding(.bottom, 7)
            CList {
                ForEach(Array(state.examples.enumerated()), id: \.element.id) { index, example in
                    // P10 录入题详情在工作包 2.7，本版行先只展示，不做假导航。
                    CListRow(
                        stem: example.stem,
                        showsDivider: index != state.examples.count - 1,
                        meta: { rowMeta(example) }
                    )
                }
            }
        }
    }

    /// ③：标错的挂「错题」徽标 + 掌握状态 + 错因；未标错的中性展示（高保真稿 B P09 `.lmeta`）。
    @ViewBuilder
    private func rowMeta(_ example: CapturedExampleView) -> some View {
        if example.isMistake {
            CBadge(text: "错题", kind: .error)
            Text(example.dateText)
            if let state = example.state {
                // 待复习实心、已掌握虚线描边（`.tag` / `.tag.ghost`）。
                CTag(text: state, style: state == MistakeBrowse.learnedState ? .ghost : .solid)
            }
            if let reason = example.reason {
                Text(reason)
            }
        } else {
            CTag(text: "我录入")
            Text(example.dateText)
            Text("未标错")
        }
    }

    /// ④：空态文案取信息架构 v1.4 §5.4（P09 该成语下没有录入题）。
    private var emptyState: some View {
        CEmptyState(
            title: "这个成语下还没有录入题",
            description: "之前录的题已经删掉或已归到别的成语下。",
            actionTitle: "返回错题本",
            onAction: actions.onBackToBook
        )
        .padding(.vertical, Tokens.Space.block)
    }

    private var footer: some View {
        Text(MistakeBrowse.capturedFooter(state.examples.count))
            .font(.system(size: Tokens.Font.n11))
            .foregroundStyle(colors.text3)
            .frame(maxWidth: .infinity, alignment: .center)
    }
}

/// P09 页面状态与行为（读库失败＝橙条可重试，与 P08 同一口径）。
@MainActor
public final class IdiomCapturedViewModel: ObservableObject {
    @Published public private(set) var state = IdiomCapturedState()

    private let idiomID: String
    private let reader: IdiomCapturedReading

    public init(idiomID: String, reader: IdiomCapturedReading) {
        self.idiomID = idiomID
        self.reader = reader
    }

    public func load() async {
        state.loading = true
        state.failed = false
        do {
            if let info = try reader.capturedIdiom(idiomID: idiomID) {
                state.missing = false
                state.word = info.word
                state.senseLine = MistakeBrowse.senseLine(info.sense)
                state.examples = try reader.capturedExamples(idiomID: idiomID).map(MistakeBrowse.view(of:))
            } else {
                state.missing = true
                state.word = ""
                state.senseLine = ""
                state.examples = []
            }
            state.loading = false
        } catch {
            state.examples = []
            state.loading = false
            state.failed = true
        }
    }
}

/// P09 某成语下的录入题（二级页，从 P08 分组视图点入；P05 数字入口在工作包 2.10 接）。
public struct P09IdiomCapturedScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: IdiomCapturedViewModel

    private let onBack: () -> Void
    private let onOpenIdiom: () -> Void
    private let onBackToBook: () -> Void

    @MainActor
    public init(
        idiomID: String,
        reader: IdiomCapturedReading,
        onBack: @escaping () -> Void,
        onOpenIdiom: @escaping () -> Void,
        onBackToBook: @escaping () -> Void
    ) {
        _model = StateObject(wrappedValue: IdiomCapturedViewModel(idiomID: idiomID, reader: reader))
        self.onBack = onBack
        self.onOpenIdiom = onOpenIdiom
        self.onBackToBook = onBackToBook
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        return P09CapturedContent(
            state: model.state,
            actions: IdiomCapturedActions(
                onBack: onBack,
                onOpenIdiom: onOpenIdiom,
                onBackToBook: onBackToBook,
                onRetry: { Task { await model.load() } }
            ),
            colors: colors
        )
        .task { await model.load() }
    }
}
