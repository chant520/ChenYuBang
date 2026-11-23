import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P12 纯渲染层（信息架构 v1.4 P12 ①–⑥ / 高保真稿 B P12）：
/// 页名与本轮结束、本轮范围、成绩卡、涉及成语、进阶占位行、回栈底与再复习一轮。
/// 成绩数字来自本轮内存小结（`review_log` 无轮次概念，不按库重算，见第 8.2 节）。
public struct P12SummaryContent: View {
    private let state: P12SummaryState
    private let actions: P12SummaryActions
    private let colors: TokenColors

    public init(
        state: P12SummaryState,
        actions: P12SummaryActions,
        colors: TokenColors
    ) {
        self.state = state
        self.actions = actions
        self.colors = colors
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                header
                scopeRow
                scoreCard
                idiomCard
                advancedRow
                CButton(
                    text: state.scope.idiomID == nil ? "回错题本" : "回成语详情",
                    enabled: true,
                    action: actions.onHome
                )
                CButton(text: "再复习一轮", variant: .ghost, enabled: true, action: actions.onRetryRound)
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.top, Tokens.Space.contentTop)
            .padding(.bottom, Tokens.Space.block)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
    }

    /// ①：页名「复习小结」+「本轮结束」。
    private var header: some View {
        HStack(spacing: 0) {
            Text("复习小结")
                .font(.system(size: Tokens.Font.n20, weight: .semibold))
                .foregroundStyle(colors.text)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            Text("本轮结束")
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text3)
        }
    }

    /// ②：本轮范围——范围标签 + 「本轮范围」说明（不承载跳转）。
    private var scopeRow: some View {
        HStack(spacing: 0) {
            CTag(text: scopeLine, style: .solid)
            Spacer(minLength: 0)
            Text("本轮范围")
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text3)
        }
    }

    private var scopeLine: String {
        state.scope.line
    }

    /// ③：成绩卡——本轮题量 / 答对 / 答错（答错＝与首次作答相同，第 8.2 节）。
    private var scoreCard: some View {
        CCard {
            HStack(alignment: .top, spacing: Tokens.Space.block) {
                scoreColumn(value: state.total, label: "本轮题量", valueColor: colors.text)
                scoreColumn(value: state.correct, label: "答对", valueColor: colors.accent)
                scoreColumn(value: state.wrong, label: "答错", valueColor: colors.text3)
                Spacer(minLength: 0)
            }
        }
    }

    private func scoreColumn(value: Int, label: String, valueColor: Color) -> some View {
        VStack(alignment: .leading, spacing: Tokens.Space.lsubTop) {
            Text("\(value)")
                .font(.system(size: Tokens.Font.n30, weight: .semibold))
                .foregroundStyle(valueColor)
            Text(label)
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text2)
        }
        .accessibilityElement(children: .combine)
    }

    /// ④：本轮碰到的成语（去重、按词面升序）；点击成语进 P05 看详情与真题。
    private var idiomCard: some View {
        CCard {
            CLabel("本轮碰到的成语")
            if state.failed {
                Text("涉及成语读取失败，请重试")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lsubTop)
            } else if state.loading {
                HStack {
                    Spacer(minLength: 0)
                    CSpinner()
                    Spacer(minLength: 0)
                }
                .padding(.top, Tokens.Space.lsubTop)
            } else if state.idioms.isEmpty {
                Text("本轮错题没有可跳转的成语")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lsubTop)
            } else {
                VStack(alignment: .leading, spacing: Tokens.Space.lmetaTop) {
                    ForEach(state.idioms) { idiom in
                        Button(action: { actions.onOpenIdiom(idiom.id) }, label: {
                            CTag(text: idiom.word, style: .solid)
                                .contentShape(Rectangle())
                        })
                        .buttonStyle(CPressDimStyle())
                        .accessibilityLabel("成语 \(idiom.word)")
                    }
                }
                .padding(.top, Tokens.Space.lsubTop)
                Text("点击成语看详情与真题")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text3)
                    .padding(.top, Tokens.Space.lmetaTop)
            }
            if state.failed {
                CButton(text: "重试", variant: .ghost, enabled: true, action: actions.onRetryLoad)
            }
        }
    }

    /// ⑤：进阶占位行（高保真稿 B P12）：D5 收尾反馈本版不做，只留占位。
    private var advancedRow: some View {
        CCard {
            HStack(spacing: 0) {
                Text("更完整的收尾反馈（趋势、薄弱点）")
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(colors.text2)
                Spacer(minLength: 0)
                CTag(text: "进阶", style: .disabled)
            }
        }
    }
}

/// P12 复习小结（全屏浮层，由 P11 最后一题「看本轮小结」进入；IA 决策 E / 第 4.4 节）。
/// 「回错题本 / 回成语详情」与「再复习一轮」都落在复习来源的栈底：
/// 前者关闭整个复习浮层，后者用同一队列再开一轮。
public struct P12SummaryScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: P12SummaryViewModel

    private let scope: ReviewScope
    private let onHome: () -> Void
    private let onRetryRound: () -> Void
    private let onOpenIdiom: (String) -> Void

    @MainActor
    public init(
        reader: ReviewQueueReading,
        scope: ReviewScope,
        summary: ReviewRoundOutcome,
        queueIDs: [String],
        onHome: @escaping () -> Void,
        onRetryRound: @escaping () -> Void,
        onOpenIdiom: @escaping (String) -> Void
    ) {
        _model = StateObject(wrappedValue: P12SummaryViewModel(
            reader: reader,
            scope: scope,
            summary: summary,
            queueIDs: queueIDs
        ))
        self.scope = scope
        self.onHome = onHome
        self.onRetryRound = onRetryRound
        self.onOpenIdiom = onOpenIdiom
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        P12SummaryContent(
            state: model.state,
            actions: P12SummaryActions(
                onOpenIdiom: onOpenIdiom,
                onHome: onHome,
                onRetryRound: onRetryRound,
                onRetryLoad: { model.retryLoad() }
            ),
            colors: colors
        )
        .task { await model.load() }
    }
}
