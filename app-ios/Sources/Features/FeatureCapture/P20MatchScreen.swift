import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P20 成语匹配与修正（工作包 2.4；信息架构 P20 / 高保真稿 C P20 864–935 行）。
///
/// 只做：识别文字回看（改文字回 P19）+ 候选默认勾最匹配 + 换/加/删 + 零候选空态与 P04 手动回填。
/// 下半部分（答案/错因/标签/是否做错/来源）保留版式空壳、不接收输入；保存只弹中性提示。
/// 不写 user_example / mistake，不跳 done（P21）。
/// 手选浮层在 P20 内部复用 P04 搜索内容，不动 router 栈（禁 openSearch）。
public struct P20MatchScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: P20MatchModel

    private let text: String
    private let reader: IdiomLibraryReader
    private let onBack: () -> Void

    @MainActor
    public init(text: String, reader: IdiomLibraryReader, onBack: @escaping () -> Void) {
        self.text = text
        self.reader = reader
        self.onBack = onBack
        _model = StateObject(wrappedValue: P20MatchModel())
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        let actions = P20MatchActions(
            onEdit: onBack,
            onSelect: model.select,
            onCycle: model.cycle,
            onAdd: model.add,
            onToggleDelete: model.toggleDeleteMode,
            onDelete: model.remove,
            onOpenManual: { model.setManualPicker(visible: true) },
            onDismissManual: { model.setManualPicker(visible: false) },
            onManualPick: model.pickManual,
            onSave: { model.showInfo(P20Matcher.saveInfo) },
            onDismissInfo: model.dismissInfo,
            manualPicker: { onPick, onDismiss in
                AnyView(
                    P04SearchScreen(reader: reader, onClose: onDismiss, onOpenIdiom: onPick)
                )
            }
        )
        P20MatchContent(state: model.state, actions: actions, colors: colors)
            .task {
                let idioms = (try? reader.allIdiomsForMatch()) ?? []
                let matches = idioms.map(MatchIdiom.init(record:))
                await MainActor.run { model.bootstrap(text: text, idioms: matches) }
            }
    }
}

private extension MatchIdiom {
    init(record: IdiomRecord) {
        self.init(
            id: record.id,
            word: record.word,
            synonyms: record.synonyms,
            frequency: record.frequency
        )
    }
}

/// 页面回调打包：避免渲染层参数过多；manualPicker 由 app 层注入 P04 搜索内容。
public struct P20MatchActions {
    public let onEdit: () -> Void
    public let onSelect: (String) -> Void
    public let onCycle: () -> Void
    public let onAdd: () -> Void
    public let onToggleDelete: () -> Void
    public let onDelete: (String) -> Void
    public let onOpenManual: () -> Void
    public let onDismissManual: () -> Void
    public let onManualPick: (String) -> Void
    public let onSave: () -> Void
    public let onDismissInfo: () -> Void
    public let manualPicker: ((String) -> Void, () -> Void) -> AnyView

    public init(
        onEdit: @escaping () -> Void,
        onSelect: @escaping (String) -> Void,
        onCycle: @escaping () -> Void,
        onAdd: @escaping () -> Void,
        onToggleDelete: @escaping () -> Void,
        onDelete: @escaping (String) -> Void,
        onOpenManual: @escaping () -> Void,
        onDismissManual: @escaping () -> Void,
        onManualPick: @escaping (String) -> Void,
        onSave: @escaping () -> Void,
        onDismissInfo: @escaping () -> Void,
        manualPicker: @escaping ((String) -> Void, () -> Void) -> AnyView
    ) {
        self.onEdit = onEdit
        self.onSelect = onSelect
        self.onCycle = onCycle
        self.onAdd = onAdd
        self.onToggleDelete = onToggleDelete
        self.onDelete = onDelete
        self.onOpenManual = onOpenManual
        self.onDismissManual = onDismissManual
        self.onManualPick = onManualPick
        self.onSave = onSave
        self.onDismissInfo = onDismissInfo
        self.manualPicker = manualPicker
    }
}

/// 纯渲染层：快照测试直接喂状态，无需 DB / 路由。
public struct P20MatchContent: View {
    private let state: P20MatchState
    private let actions: P20MatchActions
    private let colors: TokenColors

    public init(state: P20MatchState, actions: P20MatchActions, colors: TokenColors) {
        self.state = state
        self.actions = actions
        self.colors = colors
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                header
                ScrollView {
                    VStack(alignment: .leading, spacing: Tokens.Space.block) {
                        reviewCard
                        candidateCard
                        placeholderCards
                        if let info = state.info {
                            CBar(kind: .info, title: info, actionTitle: "知道了", onAction: actions.onDismissInfo)
                        }
                        CButton(text: "保存到我的题库", action: actions.onSave)
                    }
                    .padding(.horizontal, Tokens.Space.pageX)
                    .padding(.top, Tokens.Space.contentTop)
                    .padding(.bottom, Tokens.Space.pageX)
                }
            }
            .background(colors.page)
            if state.showManualPicker {
                COverlay(style: .sheet, title: P20Matcher.manualAction, onDismiss: actions.onDismissManual) {
                    actions.manualPicker(actions.onManualPick, actions.onDismissManual)
                        .frame(height: Tokens.Space.imgphH * 3.5)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var header: some View {
        VStack(spacing: 0) {
            HStack(spacing: Tokens.Space.camTopGap) {
                Button(action: actions.onEdit) {
                    CIcon(.back, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                        .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                        .contentShape(Rectangle())
                }
                .buttonStyle(CPressDimStyle())
                .accessibilityLabel("返回识别结果")
                Text("匹配与修正")
                    .font(.system(size: Tokens.Font.n15, weight: .semibold))
                    .foregroundStyle(colors.head)
                Spacer(minLength: 0)
                Text("第 4 步 / 共 5 步")
                    .font(.system(size: Tokens.Font.n11))
                    .foregroundStyle(colors.text3)
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.top, Tokens.Space.cropHeadTop)
            .padding(.bottom, Tokens.Space.cropHeadBottom)
            Rectangle()
                .fill(colors.line)
                .frame(height: Tokens.Space.cropEdge)
        }
        .background(colors.page)
    }

    private var reviewCard: some View {
        CCard {
            HStack(spacing: Tokens.Space.lmetaGap) {
                CLabel("识别文字回看")
                Spacer(minLength: 0)
                CLink("改文字", action: actions.onEdit)
            }
            Text(state.reviewText.isEmpty ? "（无识别文字）" : state.reviewText)
                .font(.system(size: Tokens.Font.n13))
                .foregroundStyle(colors.text)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, Tokens.Space.lt1Gap)
        }
    }

    private var candidateCard: some View {
        CCard {
            CLabel("系统匹配的成语候选")
            if state.loading {
                Text("正在匹配…")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text3)
                    .padding(.top, Tokens.Space.lmetaTop)
            } else if state.empty {
                CEmptyState(
                    title: P20Matcher.emptyTitle,
                    description: "",
                    actionTitle: P20Matcher.manualAction,
                    onAction: actions.onOpenManual
                )
                .padding(.top, Tokens.Space.lmetaTop)
            } else {
                candidateRows
            }
        }
    }

    private var candidateRows: some View {
        VStack(spacing: 0) {
            ForEach(state.candidates) { candidate in
                candidateRow(candidate)
            }
            HStack(spacing: Tokens.Space.lmetaGap) {
                CButton(text: "换一个", variant: .ghost, enabled: state.canCycle, action: actions.onCycle)
                CButton(text: "加一个", variant: .ghost, enabled: state.canAdd, action: actions.onAdd)
                CButton(
                    text: state.deleteMode ? "完成" : "删一个",
                    variant: .ghost,
                    enabled: !state.candidates.isEmpty,
                    action: actions.onToggleDelete
                )
            }
            .padding(.top, Tokens.Space.ghostY)
            let hints = disabledHints
            if !hints.isEmpty {
                Text(hints)
                    .font(.system(size: Tokens.Font.n11))
                    .foregroundStyle(colors.text3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, Tokens.Space.ghostY)
            }
        }
        .padding(.top, Tokens.Space.lmetaTop)
    }

    @ViewBuilder
    private func candidateRow(_ candidate: MatchCandidate) -> some View {
        let isLast = candidate.id == state.candidates.last?.id
        HStack(spacing: 0) {
            CCheckRow(
                label: candidate.word,
                isOn: Binding(
                    get: { candidate.id == state.checkedId },
                    set: { _ in actions.onSelect(candidate.id) }
                ),
                enabled: !state.deleteMode,
                trailingText: candidate.tier.label,
                showsDivider: !isLast && !state.deleteMode
            )
            if state.deleteMode {
                Button(
                    action: { actions.onDelete(candidate.id) },
                    label: {
                        CIcon(.close, size: Tokens.Icon.n18, strokeWidth: 1.6, color: colors.text2)
                            .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                            .contentShape(Rectangle())
                    }
                )
                .buttonStyle(CPressDimStyle())
                .accessibilityLabel("删除\(candidate.word)")
            }
        }
    }

    private var disabledHints: String {
        var hints: [String] = []
        if !state.canCycle { hints.append(P20Matcher.noMoreCandidates) }
        if !state.canAdd { hints.append(P20Matcher.candidateLimit) }
        return hints.joined(separator: "；")
    }

    /// 下半部分：按高保真稿保留版式，空壳占位、不接收输入。
    private var placeholderCards: some View {
        VStack(spacing: Tokens.Space.block) {
            CCard {
                CLabel("我的答案")
                placeholderBox
                CLabel("我的错因")
                    .padding(.top, Tokens.Space.lmetaTop)
                placeholderBox
                CLabel("自定义标签")
                    .padding(.top, Tokens.Space.lmetaTop)
                placeholderBox
            }
            CCard {
                CLabel("是否做错（可选）")
                HStack(spacing: Tokens.Space.chkGap) {
                    RoundedRectangle(cornerRadius: Tokens.Radius.r4)
                        .fill(colors.disbg)
                        .frame(width: Tokens.Space.chkSize, height: Tokens.Space.chkSize)
                        .overlay(
                            RoundedRectangle(cornerRadius: Tokens.Radius.r4)
                                .strokeBorder(colors.disline, lineWidth: 1)
                        )
                    Text("我这道题做错了，标记为错题")
                        .font(.system(size: Tokens.Font.n13))
                        .foregroundStyle(colors.disfg)
                }
                Text("仅勾选后才进错题本；不勾只存入我的题库。")
                    .font(.system(size: Tokens.Font.n11))
                    .foregroundStyle(colors.text3)
                    .padding(.top, Tokens.Space.ghostY)
            }
            CCard {
                CLabel("来源信息（可空）")
                placeholderBox
                placeholderBox
                    .padding(.top, Tokens.Space.ghostY)
            }
        }
    }

    private var placeholderBox: some View {
        RoundedRectangle(cornerRadius: Tokens.Radius.r6)
            .fill(colors.inputbg)
            .frame(height: Tokens.Space.inputMinH)
            .overlay(
                RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                    .strokeBorder(colors.line, lineWidth: 1)
            )
            .padding(.top, Tokens.Space.ghostY)
            .disabled(true)
            .accessibilityHidden(true)
    }
}

extension MatchCandidate: Identifiable {}
