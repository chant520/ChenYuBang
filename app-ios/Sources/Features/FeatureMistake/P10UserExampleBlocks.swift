import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P10 区块 ⑤–⑩ 与底部删除：答案遮罩与重做、出处、关联成语、我的答案与错因、掌握状态预留。
///
/// 与 `P10UserExampleContent` 同一页面的渲染分段（纯数据进、回调出，快照测试直接喂）。
/// 外层用与页面相同的 `block` 间距，保证拆段后视觉节奏与不拆一致。
/// 区块 ⑥「正确答案 + 一句解析」本版无来源、不渲染（信息架构 v1.4 版内修订第 ① 条）。
public struct P10UserExampleBlocks: View {
    private let state: UserExampleState
    private let detail: UserExampleDetail
    private let actions: UserExampleActions
    private let colors: TokenColors

    public init(
        state: UserExampleState,
        detail: UserExampleDetail,
        actions: UserExampleActions,
        colors: TokenColors
    ) {
        self.state = state
        self.detail = detail
        self.actions = actions
        self.colors = colors
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            // ⑤：遮罩开关与重做入口只在标错的录入题上出现（未标错的走普通详情）。
            if state.isMistake {
                maskRow
                rerollActions
            }
            sourceCard
            idiomCard
            answerCard
            // ⑩：仅标错录入题显示手动两态切换；不新增红动作。
            if state.isMistake {
                reviewStateCard
            }
            // 底部红描边删除行仅标错的录入题渲染（信息架构 v1.4 版内修订第 ④ 条）：
            // 未标错的普通详情整行不出现，删除入口只留顶部置灰「删除」，其文案与语义随 2.8（C8）定义。
            if state.showsBottomDeleteRow {
                deleteRow
            }
        }
    }

    /// ⑤：答案遮罩行（规范 5.x `.maskrow`：底 inputbg、1px line、圆角 5、内边距 11×12）。
    /// 本版正确答案无来源，遮罩盖的是 ⑨ 的「我的答案」（信息架构 v1.4 版内修订第 ③ 条）。
    private var maskRow: some View {
        HStack(spacing: Tokens.Space.lmetaGap) {
            Text(state.answerHidden ? "答案已遮住" : "答案已显示")
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text2)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
            Button(action: actions.onToggleAnswer) {
                Text(state.answerHidden ? "显示答案" : "隐藏答案")
                    .font(.system(size: Tokens.Font.n115, weight: .bold))
                    .foregroundStyle(colors.accent)
            }
            // 重做中对答案前不给看首次作答，按钮在位、置灰。
            .disabled(!state.canToggleMask || state.updatingReviewState)
            .opacity(state.canToggleMask && !state.updatingReviewState ? 1 : 0.4)
            .accessibilityLabel(state.answerHidden ? "显示我的答案" : "隐藏我的答案")
        }
        .padding(.vertical, 11)
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: Tokens.Radius.r5).fill(colors.inputbg))
        .overlay(
            RoundedRectangle(cornerRadius: Tokens.Radius.r5)
                .strokeBorder(colors.line, lineWidth: 1)
        )
    }

    @ViewBuilder
    private var rerollActions: some View {
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            if state.rerolling {
                if state.checked {
                    resultCard
                }
                HStack(spacing: Tokens.Space.lmetaGap) {
                    CButton(text: "对答案", enabled: state.picked != nil && !state.updatingReviewState,
                            action: actions.onCheck)
                    CButton(text: "退出重做", variant: .ghost, enabled: !state.updatingReviewState,
                            action: actions.onExitReroll)
                }
            } else {
                CButton(text: "重做这道题", enabled: !state.updatingReviewState, action: actions.onStartReroll)
            }
        }
    }

    /// 重做比对结果：只与首次「我的答案」比，不伪造判分（信息架构 v1.4 版内修订第 ③ 条）。
    private var resultCard: some View {
        CCard {
            CLabel("本次作答")
            Text(MistakeBrowse.rerollResult(first: state.firstPick, picked: state.picked))
                .font(.system(size: Tokens.Font.n13, weight: .semibold))
                .foregroundStyle(colors.text)
                .padding(.top, Tokens.Space.lsubTop)
                .fixedSize(horizontal: false, vertical: true)
            Text(MistakeBrowse.rerollNote)
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
                .padding(.top, Tokens.Space.lmetaTop)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// ⑦：出处＝录入时填的年份 / 考试类型，两者皆空显示「未填」（区块不含题型与地区，IA v1.4 P10 ⑦）。
    private var sourceCard: some View {
        CCard {
            CLabel("出处")
            Text(MistakeBrowse.sourceLine(examType: detail.examType, year: detail.year))
                .font(.system(size: Tokens.Font.n13))
                .foregroundStyle(colors.text2)
                .padding(.top, Tokens.Space.lsubTop)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// ⑧：关联成语标签，每个可点进 P05；增删归属在编辑态（工作包 2.8）里做，只读态不给入口。
    private var idiomCard: some View {
        CCard {
            CLabel("关联成语")
            if detail.idioms.isEmpty {
                Text("暂未关联成语")
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lsubTop)
            } else {
                VStack(alignment: .leading, spacing: Tokens.Space.lmetaGap) {
                    // iOS 15 无流式布局 API（开发规范 §7 平台映射）：按固定每行 3 个分组。
                    ForEach(Array(MistakeBrowse.rows(of: detail.idioms, perRow: 3).enumerated()),
                            id: \.offset) { _, group in
                        HStack(spacing: Tokens.Space.lmetaGap) {
                            ForEach(group) { item in
                                Button(
                                    action: { actions.onOpenIdiom(item.id) },
                                    label: { CTag(text: item.word) }
                                )
                                .buttonStyle(CPressDimStyle())
                            }
                            Spacer(minLength: 0)
                        }
                    }
                }
                .padding(.top, Tokens.Space.lmetaTop)
            }
        }
    }

    /// ⑨：我的答案（标错的受 ⑤ 遮罩控制）、错因与自定义标签（未标错的不填这两项）。
    private var answerCard: some View {
        CCard(soft: true) {
            CLabel("我的答案与错因")
            Text("我的答案：\(answerText)")
                .font(.system(size: Tokens.Font.n13))
                .foregroundStyle(colors.text2)
                .padding(.top, Tokens.Space.lsubTop)
                .fixedSize(horizontal: false, vertical: true)
            if detail.isMistake, let reason = detail.reason, !reason.isEmpty {
                Text("错因：\(reason)")
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lmetaTop)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if detail.isMistake, !detail.tagNames.isEmpty {
                Text("标签：\(detail.tagNames.joined(separator: "、"))")
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lmetaTop)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// 遮罩生效时 ⑨ 的答案不外泄，只写「已遮住」；未填答案走「暂无」占位。
    private var answerText: String {
        if state.answerHidden {
            return "已遮住"
        }
        let answer = detail.myAnswer?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return answer.isEmpty ? MistakeBrowse.placeholder : answer
    }

    /// ⑩：已掌握仍留在错题本，但退出全局与单成语待复习队列；切回后重新入队。
    private var reviewStateCard: some View {
        CCard {
            CLabel("掌握状态")
            CSegmentedControl(
                items: MistakeReviewState.allCases.map(\.rawValue),
                selection: Binding(
                    get: {
                        MistakeReviewState.allCases.firstIndex(of: state.reviewState) ?? 0
                    },
                    set: { index in
                        guard let reviewState = MistakeReviewState.allCases.indices.contains(index)
                            ? MistakeReviewState.allCases[index] : nil else { return }
                        actions.onSetReviewState(reviewState)
                    }
                ),
                disabledIndices: state.canChangeReviewState ? [] : Set(MistakeReviewState.allCases.indices)
            )
            .padding(.top, Tokens.Space.lsubTop)
            if state.updatingReviewState {
                HStack(spacing: Tokens.Space.lmetaGap) {
                    CSpinner()
                    Text("正在更新掌握状态…")
                        .font(.system(size: Tokens.Font.n11))
                        .foregroundStyle(colors.text3)
                }
                .padding(.top, Tokens.Space.lmetaTop)
            } else {
                Text(state.reviewState == .proficient
                     ? "已掌握的题仍保留在错题本中，但不再进入待复习队列。"
                     : "切到待复习后，这道题会重新进入对应范围的复习队列。")
                    .font(.system(size: Tokens.Font.n11))
                    .foregroundStyle(colors.text3)
                    .padding(.top, Tokens.Space.lmetaTop)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// 底部删除（高保真稿 B P10：本页唯一红动作）＝把这道题移出错题本，与顶部「删除」同一入口。
    private var deleteRow: some View {
        VStack(alignment: .leading, spacing: 0) {
            CButton(text: UserExampleEditCopy.deleteMistakeRow, variant: .dangerOutline,
                    enabled: !state.deleting && !state.saving && !state.updatingReviewState, action: actions.onDelete)
            Text(UserExampleEditCopy.deleteMistakeNote)
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
                .padding(.top, Tokens.Space.lmetaTop)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
