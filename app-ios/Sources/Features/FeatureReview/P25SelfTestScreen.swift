import CangyuDesignSystem
import CangyuTokens
import SwiftUI

/// P25 纯渲染层：题目、选项、即时反馈、页内小结与退出确认都由真实状态驱动。
public struct P25SelfTestContent: View {
    private let state: P25SelfTestState
    private let exitAsking: Bool
    private let actions: P25SelfTestActions
    private let colors: TokenColors

    public init(
        state: P25SelfTestState,
        exitAsking: Bool,
        actions: P25SelfTestActions,
        colors: TokenColors
    ) {
        self.state = state
        self.exitAsking = exitAsking
        self.actions = actions
        self.colors = colors
    }

    public var body: some View {
        ZStack {
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
            .accessibilityHidden(exitAsking)
            if exitAsking { exitConfirmation }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
    }

    private var header: some View {
        HStack(spacing: Tokens.Space.lt1Gap) {
            Button(action: actions.onAskExit) {
                CIcon(.close, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                    .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                    .contentShape(Rectangle())
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel("退出成语自测")
            Text("成语自测")
                .font(.system(size: Tokens.Font.n15, weight: .semibold))
                .foregroundStyle(colors.text)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch state {
        case .loading:
            HStack {
                Spacer(minLength: 0)
                CSpinner()
                Spacer(minLength: 0)
            }
        case .emptyScope:
            CEmptyState(
                title: "当前考试范围没有可自测的成语",
                description: "请先调整考试范围，再回来开始自测。",
                actionTitle: "返回我的",
                onAction: actions.onLeave
            )
            .padding(.vertical, Tokens.Space.block)
        case .insufficientCandidates:
            CEmptyState(
                title: "可用的候选项不足",
                description: "全库至少需要 4 条词与释义都唯一的成语，请更新题库后再试。",
                actionTitle: "返回我的",
                onAction: actions.onLeave
            )
            .padding(.vertical, Tokens.Space.block)
        case .question(let question):
            questionContent(question)
        case .summary(let summary):
            summaryContent(summary)
        case .error:
            VStack(spacing: Tokens.Space.block) {
                CBar(
                    kind: .error,
                    title: "自测题库读取失败",
                    subtitle: "只重新读取本机题库，不会上传学习数据。"
                )
                CButton(text: "重试", action: actions.onRetryLoad)
                CButton(text: "返回我的", variant: .ghost, action: actions.onLeave)
            }
        }
    }

    private func questionContent(_ state: P25QuestionState) -> some View {
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            HStack(spacing: 0) {
                Text(state.progressText)
                    .font(.system(size: Tokens.Font.n13, weight: .semibold))
                    .foregroundStyle(colors.text)
                Spacer(minLength: 0)
                CBadge(text: state.typeTitle, kind: .neutral)
            }
            promptCard(state.question)
            VStack(alignment: .leading, spacing: Tokens.Space.lmetaTop) {
                ForEach(Array(state.question.options.enumerated()), id: \.offset) { index, option in
                    optionRow(option, letter: P25SelfTestLetters.all[index], state: state)
                }
            }
            if state.isGraded {
                CBar(kind: state.isCorrect ? .info : .error, title: state.isCorrect ? "回答正确" : "回答错误")
            }
            CButton(text: state.nextButtonTitle, enabled: state.isGraded, action: actions.onNext)
        }
    }

    private func promptCard(_ question: SelfTestQuestion) -> some View {
        CCard {
            CLabel(question.type == .definitionToIdiom ? "请选择成语" : "请选择释义")
            Text(question.prompt)
                .font(.system(size: Tokens.Font.n15, weight: .semibold))
                .foregroundStyle(colors.text)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, Tokens.Space.lsubTop)
        }
    }

    private func optionRow(
        _ option: SelfTestOption,
        letter: String,
        state: P25QuestionState
    ) -> some View {
        let isCorrect = state.isGraded && option.idiomID == state.question.targetID
        let isSelectedWrong = state.isGraded && option.idiomID == state.selectedOptionID && !isCorrect
        let background: Color = isCorrect ? colors.tintbg : (isSelectedWrong ? colors.errbg : colors.surface)
        let border: Color = isCorrect ? colors.tintline : (isSelectedWrong ? colors.errline : colors.line)
        let bodyColor: Color = isCorrect ? colors.tintfg : (isSelectedWrong ? colors.errfg : colors.text)
        let letterColor: Color = isCorrect ? colors.tintfg : (isSelectedWrong ? colors.errfg : colors.text3)
        let mutedColor: Color = state.isGraded && !isCorrect && !isSelectedWrong ? colors.text3 : bodyColor
        let statusText = isCorrect ? "正确答案" : (isSelectedWrong ? "你的选择" : nil)

        let row = HStack(alignment: .top, spacing: Tokens.Space.lmetaGap) {
            Text(letter)
                .font(.system(size: Tokens.Font.n13, weight: .semibold))
                .foregroundStyle(letterColor)
            VStack(alignment: .leading, spacing: Tokens.Space.lsubTop) {
                Text(option.text)
                    .font(.system(size: Tokens.Font.n13))
                    .cLineHeight(1.6, size: Tokens.Font.n13)
                    .foregroundStyle(mutedColor)
                    .fixedSize(horizontal: false, vertical: true)
                if let statusText {
                    Text(statusText)
                        .font(.system(size: Tokens.Font.n11, weight: .semibold))
                        .foregroundStyle(isCorrect ? colors.tintfg : colors.errfg)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, Tokens.Space.lrowY)
        .padding(.horizontal, Tokens.Space.lrowX)
        // 复用既有 48pt 尺寸令牌，不新增视觉档；实际最小高满足设计规范 5.16。
        .frame(maxWidth: .infinity, minHeight: Tokens.Space.cropMin, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Tokens.Radius.r6).fill(background))
        .overlay(
            RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                .strokeBorder(border, lineWidth: 1)
        )
        .contentShape(Rectangle())

        return Group {
            if state.isGraded {
                row
            } else {
                Button(action: { actions.onPick(option.idiomID) }, label: { row })
                    .buttonStyle(CPressDimStyle())
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("选项 \(letter)，\(option.text)" + (statusText.map { "，\($0)" } ?? ""))
        .accessibilityValue(state.isGraded ? "已锁定" : "未选择")
    }

    private func summaryContent(_ summary: P25SummaryState) -> some View {
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            CCard {
                CLabel("本轮小结")
                HStack(alignment: .top, spacing: Tokens.Space.block) {
                    scoreColumn(value: summary.total, label: "题量", valueColor: colors.text)
                    scoreColumn(value: summary.correct, label: "正确数", valueColor: colors.tintfg)
                    scoreColumn(value: summary.wrong, label: "错误数", valueColor: colors.errfg)
                    Spacer(minLength: 0)
                }
                .padding(.top, Tokens.Space.lmetaTop)
            }
            CButton(text: "再测一轮", action: actions.onRetryRound)
            CButton(text: "返回我的", variant: .ghost, action: actions.onLeave)
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

    private var exitConfirmation: some View {
        COverlay(
            style: .dialog,
            title: "退出成语自测",
            description: "退出会丢弃本轮进度。",
            onDismiss: actions.onCancelExit,
            cancelEnabled: false
        ) {
            CButton(text: "继续答题", variant: .ghost, action: actions.onCancelExit)
            CButton(text: "退出", variant: .dangerOutline, action: actions.onConfirmExit)
                .padding(.top, Tokens.Space.block)
        }
        .accessibilityAddTraits(.isModal)
    }
}

private enum P25SelfTestLetters {
    static let all = ["A", "B", "C", "D"]
}

/// P25 全屏路由页；ViewState 跟随 route entry，退出后本轮内存状态随之销毁。
public struct P25SelfTestScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: P25SelfTestViewModel
    private let backRequest: Int

    @MainActor
    public init(
        reader: SelfTestLibraryReading,
        backRequest: Int,
        randomSource: any SelfTestRandomSource = SystemSelfTestRandomSource(),
        onExit: @escaping () -> Void
    ) {
        _model = StateObject(wrappedValue: P25SelfTestViewModel(
            reader: reader,
            randomSource: randomSource,
            onExit: onExit
        ))
        self.backRequest = backRequest
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        P25SelfTestContent(
            state: model.state,
            exitAsking: model.exitAsking,
            actions: P25SelfTestActions(
                onPick: { model.pick($0) },
                onNext: { model.next() },
                onAskExit: { model.askExit() },
                onCancelExit: { model.cancelExit() },
                onConfirmExit: { model.confirmExit() },
                onLeave: { model.leave() },
                onRetryRound: { model.retryRound() },
                onRetryLoad: { model.retryLoad() }
            ),
            colors: colors
        )
        .task { await model.load() }
        .onChange(of: backRequest) { _ in model.askExit() }
    }
}
