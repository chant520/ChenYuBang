import SwiftUI
import CangyuDesignSystem
import CangyuTokens

/// 表单回调打包：与候选回调分开，避免 P20MatchActions 参数继续膨胀。
public struct P20FormActions {
    public let onAnswer: (String) -> Void
    public let onReason: (String) -> Void
    public let onAddTag: (String) -> Bool
    public let onRemoveTag: (String) -> Void
    public let onToggleMistake: () -> Void
    public let onExamType: (String) -> Void
    public let onYear: (String) -> Void
    public let onDismissSaveError: () -> Void

    public init(
        onAnswer: @escaping (String) -> Void,
        onReason: @escaping (String) -> Void,
        onAddTag: @escaping (String) -> Bool,
        onRemoveTag: @escaping (String) -> Void,
        onToggleMistake: @escaping () -> Void,
        onExamType: @escaping (String) -> Void,
        onYear: @escaping (String) -> Void,
        onDismissSaveError: @escaping () -> Void
    ) {
        self.onAnswer = onAnswer
        self.onReason = onReason
        self.onAddTag = onAddTag
        self.onRemoveTag = onRemoveTag
        self.onToggleMistake = onToggleMistake
        self.onExamType = onExamType
        self.onYear = onYear
        self.onDismissSaveError = onDismissSaveError
    }
}

/// P20 修正表单（高保真稿 C P20 906–933 行）。
/// 错因 / 标签仅勾选「是否做错」后可用；来源两项可空，年份为 4 位数字。
public struct P20FormSection: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var tagDraft = ""
    @State private var showsTagEditor = false

    private let form: P20FormState
    private let actions: P20FormActions

    public init(form: P20FormState, actions: P20FormActions) {
        self.form = form
        self.actions = actions
    }

    public var body: some View {
        VStack(spacing: Tokens.Space.block) {
            answerCard
            mistakeCard
            sourceCard
        }
        .onChange(of: form.isMistake) { isMistake in
            if !isMistake {
                tagDraft = ""
                showsTagEditor = false
            }
        }
    }

    private var answerCard: some View {
        CCard {
            CLabel("我的答案")
            CInput(
                text: Binding(get: { form.answerText }, set: actions.onAnswer),
                state: form.saving ? .disabled : .default,
                placeholder: "选 A / B / C / D"
            )
            .padding(.top, Tokens.Space.lt1Gap)

            CLabel("我的错因")
                .padding(.top, Tokens.Space.block)
            CInput(
                text: Binding(get: { form.reason }, set: actions.onReason),
                state: form.isMistake && !form.saving ? .default : .disabled,
                placeholder: form.isMistake ? "如：望文生义" : "勾选「是否做错」后可填"
            )
            .padding(.top, Tokens.Space.lt1Gap)

            CLabel("自定义标签")
                .padding(.top, Tokens.Space.block)
            tagEditor
                .padding(.top, Tokens.Space.lt1Gap)
            Text("标签 1–8 个字；仅在标错时保存。")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
                .padding(.top, Tokens.Space.lmetaTop)
        }
    }

    private var mistakeCard: some View {
        CCard {
            CLabel("是否做错（可选）")
            CCheckRow(
                label: "我这道题做错了，标记为错题",
                isOn: Binding(get: { form.isMistake }, set: { _ in actions.onToggleMistake() }),
                enabled: !form.saving,
                showsDivider: false
            )
            .padding(.top, Tokens.Space.lmetaTop)
            Text("仅勾选后才进错题本；不勾只存入我的题库。")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
                .padding(.top, Tokens.Space.ghostY)
        }
    }

    private var sourceCard: some View {
        CCard {
            CLabel("来源信息（可空）")
            CInput(
                text: Binding(get: { form.examType ?? "" }, set: actions.onExamType),
                state: form.saving ? .disabled : .default,
                placeholder: "考试类型：国考"
            )
            .padding(.top, Tokens.Space.ghostY)
            CInput(
                text: Binding(get: { form.yearText }, set: actions.onYear),
                state: form.saving ? .disabled : (form.yearInvalid ? .error : .default),
                placeholder: "年份：2023",
                errorHint: form.yearInvalid ? "年份为 4 位数字" : nil
            )
            .padding(.top, Tokens.Space.ghostY)
        }
    }

    private var tagEditor: some View {
        VStack(alignment: .leading, spacing: Tokens.Space.ghostY) {
            if !form.tags.isEmpty {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 64), spacing: Tokens.Space.lmetaGap)],
                    alignment: .leading,
                    spacing: Tokens.Space.lmetaGap
                ) {
                    ForEach(form.tags, id: \.self) { tag in
                        tagChip(tag)
                    }
                }
            }
            if form.isMistake && !form.saving {
                if showsTagEditor {
                    HStack(spacing: Tokens.Space.lmetaGap) {
                        CInput(
                            text: $tagDraft,
                            placeholder: "如：易混词"
                        )
                        .frame(maxWidth: .infinity)
                        Button(action: addTag) {
                            CTag(text: "加入", style: tagDraft.isEmpty ? .disabled : .solid)
                        }
                        .buttonStyle(CPressDimStyle())
                        .disabled(tagDraft.isEmpty)
                        .accessibilityLabel("加入标签")
                    }
                } else {
                    Button {
                        showsTagEditor = true
                    } label: {
                        CTag(text: "+ 加一个", style: .ghost)
                    }
                    .buttonStyle(CPressDimStyle())
                    .accessibilityLabel("加一个标签")
                }
            } else {
                CTag(text: "+ 加一个", style: .disabled)
            }
        }
    }

    private func tagChip(_ tag: String) -> some View {
        HStack(spacing: 4) {
            Text(tag)
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.tintfg)
            if form.isMistake && !form.saving {
                Button {
                    actions.onRemoveTag(tag)
                } label: {
                    CIcon(.close, size: 11, strokeWidth: 1.8, color: colors.tintfg)
                }
                .buttonStyle(CPressDimStyle())
                .accessibilityLabel("移除标签\(tag)")
            }
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

    private func addTag() {
        if actions.onAddTag(tagDraft) {
            tagDraft = ""
        }
    }

    private var colors: TokenColors {
        CangyuTheme.colors(for: colorScheme)
    }
}
