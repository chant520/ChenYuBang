import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P11 纯渲染层（信息架构 v1.4 P11 ①–⑤ / 高保真稿 B P11）：
/// 顶部进度 + 本轮范围 + 关闭、题干与四选项、判分区、「下一题」、空态。
/// 退出二次确认弹层由屏幕层覆起；连续答对提示为 C9 预留位（本版不渲染）。
public struct P11ReviewContent: View {
    private let state: P11ReviewState
    private let scope: ReviewScope
    private let actions: P11ReviewActions
    private let colors: TokenColors

    public init(
        state: P11ReviewState,
        scope: ReviewScope,
        actions: P11ReviewActions,
        colors: TokenColors
    ) {
        self.state = state
        self.scope = scope
        self.actions = actions
        self.colors = colors
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                header
                content
                // ⑤：页脚把退出口径写死在页面上（高保真稿 B P11 `.leavetip`）。
                Text("退出会丢弃本轮进度，需二次确认")
                    .font(.system(size: Tokens.Font.n11))
                    .foregroundStyle(colors.text3)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.top, Tokens.Space.lmetaTop)
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.top, Tokens.Space.contentTop)
            .padding(.bottom, Tokens.Space.block)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
    }

    /// ①：关闭（SVG 矢量图标，退出需二次确认）+「复习中 · 退出会二次确认」；
    /// 进度与「本轮范围」一行。
    private var header: some View {
        VStack(alignment: .leading, spacing: Tokens.Space.lmetaTop) {
            HStack(spacing: Tokens.Space.lt1Gap) {
                Button(action: actions.onAskExit) {
                    CIcon(.close, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                        .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                        .contentShape(Rectangle())
                }
                .buttonStyle(CPressDimStyle())
                .accessibilityLabel("退出复习")
                Text("复习中 · 退出会二次确认")
                    .font(.system(size: Tokens.Font.n15, weight: .semibold))
                    .foregroundStyle(colors.text)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
            }
            HStack(spacing: 0) {
                Text(state.progressText)
                    .font(.system(size: Tokens.Font.n13, weight: .semibold))
                    .foregroundStyle(colors.text)
                Spacer(minLength: 0)
                CBadge(text: scope.line, kind: .info)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if state.failed {
            CBar(
                kind: .warn,
                title: "复习队列读取失败，请重试",
                subtitle: "只重新读取本机数据，不影响已记录的复习成绩。",
                actionTitle: "重试",
                onAction: actions.onRetryLoad
            )
        } else if state.loading {
            HStack {
                Spacer(minLength: 0)
                CSpinner()
                Spacer(minLength: 0)
            }
        } else if state.items.isEmpty {
            emptyState
        } else if let item = state.current {
            if state.recordFailed {
                CBar(
                    kind: .error,
                    title: "本轮成绩未能记录，请重试",
                    actionTitle: "重试",
                    onAction: actions.onRetryRecord
                )
            }
            questionCard(item)
            if state.judged, state.canAnswer {
                judgeCard
            }
            CButton(
                text: state.isLast ? "看本轮小结" : "下一题",
                enabled: state.canGoNext,
                action: actions.onNext
            )
        }
    }

    /// ②：题干与四个选项（选完立即判分；缺项按「暂无」占位、不可选）。
    private func questionCard(_ item: ReviewQueueItem) -> some View {
        CCard {
            CLabel("题干")
            Text(item.stem)
                .font(.system(size: Tokens.Font.n13, weight: .semibold))
                .foregroundStyle(colors.text)
                .padding(.top, Tokens.Space.lsubTop)
                .fixedSize(horizontal: false, vertical: true)
            ForEach(Array(ReviewBrowse.letters.enumerated()), id: \.offset) { index, letter in
                optionRow(letter, index < item.options.count ? item.options[index] : nil)
            }
            if !state.canAnswer {
                Text(ReviewBrowse.noOptionsNote)
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lmetaTop)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func optionRow(_ letter: String, _ option: String?) -> some View {
        let selectable = !state.judged && option != nil
        let selected = state.picked == letter
        let label = HStack(alignment: .top, spacing: Tokens.Space.lmetaGap) {
            Text(letter)
                .font(.system(size: Tokens.Font.n13, weight: .semibold))
                .foregroundStyle(selected ? colors.accent : colors.text3)
            Text(option ?? ReviewPlaceholder.word)
                .font(.system(size: Tokens.Font.n13))
                .foregroundStyle(colors.text2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        return Group {
            if selectable {
                Button(action: { actions.onPick(letter) }, label: { label.contentShape(Rectangle()) })
                    .buttonStyle(CPressDimStyle())
                    .accessibilityLabel("选项 \(letter)")
                    .accessibilityValue(selected ? "已选中" : "")
            } else {
                label
            }
        }
        .padding(.vertical, Tokens.Space.ghostY)
        .padding(.horizontal, Tokens.Space.lrowX)
        .background(
            RoundedRectangle(cornerRadius: Tokens.Radius.r5)
                .fill(selected ? colors.tintbg : Color.clear)
        )
        .padding(.top, Tokens.Space.lmetaTop)
    }

    /// ③：判分区（P10 重做同款文案 + 口径说明；不显示「正确答案」、不渲染解析）。
    private var judgeCard: some View {
        CCard {
            CLabel("判分")
            if let picked = state.picked,
               let outcome = ReviewBrowse.judge(first: state.current?.myAnswer, picked: picked) {
                Text(ReviewBrowse.resultText(outcome))
                    .font(.system(size: Tokens.Font.n13, weight: .semibold))
                    .foregroundStyle(colors.text)
                    .padding(.top, Tokens.Space.lsubTop)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Text(ReviewBrowse.judgeNote)
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text2)
                .padding(.top, Tokens.Space.lsubTop)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// 空态（信息架构 v1.4 第 5.4 节 P11 两行）：全局 / 单成语各自落点，按钮直接回各自栈底。
    private var emptyState: some View {
        Group {
            if scope.idiomID == nil {
                CEmptyState(
                    title: "今天没有待复习的错题",
                    description: "可以回错题本看看全部错题",
                    actionTitle: "回错题本",
                    onAction: actions.onLeave
                )
            } else {
                CEmptyState(
                    title: "这个成语下没有待复习的错题",
                    description: "可以回成语详情看它的真题",
                    actionTitle: "回成语详情",
                    onAction: actions.onLeave
                )
            }
        }
        .padding(.vertical, Tokens.Space.block)
    }
}

/// 缺字段占位（与 `MistakeBrowse.placeholder` 同字面值；feature 之间不互相依赖）。
private enum ReviewPlaceholder {
    static let word = "暂无"
}

/// P11 复习队列（全屏浮层，两种来源共用；IA 决策 E / 第 4.4 节）。
public struct P11ReviewScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: P11ReviewViewModel

    private let scope: ReviewScope
    private let onExit: () -> Void

    @MainActor
    public init(
        reader: ReviewQueueReading,
        scope: ReviewScope,
        queueIDs: [String],
        onRoundFinished: @escaping (ReviewRoundOutcome) -> Void,
        onExit: @escaping () -> Void
    ) {
        _model = StateObject(wrappedValue: P11ReviewViewModel(
            reader: reader,
            scope: scope,
            queueIDs: queueIDs,
            onRoundFinished: onRoundFinished,
            onExit: onExit
        ))
        self.scope = scope
        self.onExit = onExit
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        ZStack {
            P11ReviewContent(
                state: model.state,
                scope: scope,
                actions: P11ReviewActions(
                    onPick: { model.pick($0) },
                    onNext: { model.next() },
                    onAskExit: { model.askExit() },
                    onLeave: { model.confirmExit() },
                    onRetryLoad: { model.retryLoad() },
                    onRetryRecord: { model.retryRecord() }
                ),
                colors: colors
            )
            if model.state.exitAsking {
                exitConfirm
            }
        }
        .task { await model.load() }
    }

    /// ⑤：退出二次确认（第 8.2 节；设计规范 5.12 中部卡片，「取消」由 COverlay 自带）。
    private var exitConfirm: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        return COverlay(
            style: .dialog,
            title: "退出复习",
            onDismiss: { model.cancelExit() },
            content: {
                Text("退出会丢弃本轮进度。")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text2)
                    .fixedSize(horizontal: false, vertical: true)
                CButton(text: "退出", variant: .primary, action: { model.confirmExit() })
            }
        )
    }
}
