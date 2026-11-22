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
            // ⑩：掌握状态切换为 C9 进阶预留位。
            reservedRow
            deleteRow
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
            .disabled(!state.canToggleMask)
            .opacity(state.canToggleMask ? 1 : 0.4)
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
                    CButton(text: "对答案", enabled: state.picked != nil, action: actions.onCheck)
                    CButton(text: "退出重做", variant: .ghost, action: actions.onExitReroll)
                }
            } else {
                CButton(text: "重做这道题", action: actions.onStartReroll)
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

    /// ⑧：关联成语标签，每个可点进 P05；「加一个」改归属属工作包 2.8，本版不渲染。
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

    /// ⑩：掌握状态切换为 C9 进阶预留位（与 P07「标记（预留）」同一形态）。
    private var reservedRow: some View {
        HStack(spacing: Tokens.Space.lmetaGap) {
            CTag(text: "掌握状态切换（预留）", style: .ghost)
            Spacer(minLength: 0)
        }
    }

    /// 底部删除（高保真稿 B P10：本页唯一红动作）；动作属工作包 2.8，本版在位、置灰。
    private var deleteRow: some View {
        VStack(alignment: .leading, spacing: 0) {
            CButton(text: "删除这道错题", variant: .dangerOutline, enabled: false, action: actions.onDelete)
            Text("删除会同时从错题本与对应成语详情里移除，需二次确认。")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
                .padding(.top, Tokens.Space.lmetaTop)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
