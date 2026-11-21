import SwiftUI
import CangyuDesignSystem
import CangyuTokens

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
    public let form: P20FormActions
    public let manualPicker: (@escaping (String) -> Void, @escaping () -> Void) -> AnyView

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
        form: P20FormActions,
        manualPicker: @escaping (@escaping (String) -> Void, @escaping () -> Void) -> AnyView
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
        self.form = form
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
                        P20FormSection(form: state.form, actions: actions.form)
                        if let error = state.form.saveError {
                            CBar(
                                kind: .error,
                                title: error,
                                actionTitle: "知道了",
                                onAction: actions.form.onDismissSaveError
                            )
                        }
                        if let info = state.info {
                            CBar(kind: .info, title: info, actionTitle: "知道了", onAction: actions.onDismissInfo)
                        }
                        CButton(
                            text: "保存到我的题库",
                            enabled: state.canSave,
                            loading: state.form.saving,
                            action: actions.onSave
                        )
                    }
                    .padding(.horizontal, Tokens.Space.pageX)
                    .padding(.top, Tokens.Space.contentTop)
                    .padding(.bottom, Tokens.Space.pageX)
                }
            }
            .background(colors.page)
            if state.showManualPicker {
                // 浮层套用 P04 搜索（信息架构 v1.4 跳转 45）：整屏复用 P04 自带头部，不另加标题与高度。
                actions.manualPicker(actions.onManualPick, actions.onDismissManual)
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
            Text(state.reviewText.isEmpty ? P20Matcher.noText : state.reviewText)
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
            if state.showsCheckedChips {
                checkedChips
                    .padding(.top, Tokens.Space.ghostY)
            }
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

    /// 多选或窗口外已选项的撤销入口：标签行始终跟着已选集合，不随「换一个」丢失。
    private var checkedChips: some View {
        HStack(alignment: .top, spacing: Tokens.Space.lmetaGap) {
            Text("已选归属")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
                .padding(.top, Tokens.Space.tagY)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Tokens.Space.lmetaGap) {
                    ForEach(state.checked) { item in
                        Button {
                            actions.onSelect(item.id)
                        } label: {
                            HStack(spacing: 4) {
                                Text(item.word)
                                    .font(.system(size: Tokens.Font.n11))
                                    .foregroundStyle(colors.tintfg)
                                CIcon(.close, size: 11, strokeWidth: 1.8, color: colors.tintfg)
                            }
                            .padding(.vertical, Tokens.Space.tagY)
                            .padding(.leading, Tokens.Space.tagX)
                            .padding(.trailing, 5)
                            .background(RoundedRectangle(cornerRadius: Tokens.Radius.r4).fill(colors.tintbg))
                            .overlay(
                                RoundedRectangle(cornerRadius: Tokens.Radius.r4)
                                    .strokeBorder(colors.tintline, lineWidth: 1)
                            )
                        }
                        .buttonStyle(CPressDimStyle())
                        .accessibilityLabel("取消归属\(item.word)")
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func candidateRow(_ candidate: MatchCandidate) -> some View {
        let isLast = candidate.id == state.candidates.last?.id
        HStack(spacing: 0) {
            CCheckRow(
                label: candidate.word,
                isOn: Binding(
                    get: { state.checkedIDs.contains(candidate.id) },
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
        if !state.hasChecked { hints.append(P20Matcher.atLeastOne) }
        if !state.canCycle { hints.append(P20Matcher.noMoreCandidates) }
        if !state.canAdd { hints.append(P20Matcher.candidateLimit) }
        return hints.joined(separator: "；")
    }
}

extension MatchCandidate: Identifiable {}
