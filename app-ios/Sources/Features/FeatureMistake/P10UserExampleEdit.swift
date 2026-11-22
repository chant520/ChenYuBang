import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P10 编辑与删除的写库口子：页面只依赖协议，实现由 app 注入（与 `UserExampleReading` 同一口径）。
public protocol UserExampleWriting {
    func saveEdit(_ input: UserExampleEditInput) throws
    func deleteMistake(userExampleID: String) throws
    func deleteUserExample(id: String) throws
}

extension UserExampleEditor: UserExampleWriting {}

/// P10 编辑与删除的界面文案（开发规范 v1.7 §8.1.1，双端逐字一致）。
///
/// 校验串凡 P20 已有的直接取 `P20Matcher` 的同名常量，不另造第二份字面值。
public enum UserExampleEditCopy {
    public static let edit = "编辑"
    public static let delete = "删除"
    public static let cancel = "取消"
    public static let save = "保存"
    public static let addIdiom = "添加归属成语"
    public static let emptyStem = "题干不能为空"
    public static let saveFailed = "保存失败，请重试"
    public static let deleteFailed = "删除失败，请重试"
    public static let atLeastOne = P20Matcher.atLeastOne
    public static let yearRule = "年份为 4 位数字"
    public static let tagRule = P20Matcher.tagRule
    public static let stemPlaceholder = "题目原文"
    public static let answerPlaceholder = "选 A / B / C / D"
    public static let examTypePlaceholder = "考试类型：国考"
    public static let yearPlaceholder = "年份：2023"
    public static let reasonPlaceholder = "如：望文生义"
    public static let tagPlaceholder = "如：易混词"
    public static let addTag = "加入"
    public static let addOneTag = "+ 加一个"
    public static let gotIt = "知道了"
    public static let deleteMistakeRow = "删除这道错题"
    public static let deleteMistakeNote = "删除只把这道题移出错题本，录入的原题仍保留在对应成语下。"
    public static let deleteMistakeTitle = "删除这道错题？"
    public static let deleteMistakeBody = "只会把这道题移出错题本，这道录入题仍保留在对应成语的「我的录入题」里。"
    public static let deleteExampleTitle = "删除这道录入题？"
    public static let deleteExampleBody = "这道题与它归属成语的关联会一并删除，本机保存的原图也会一起删除，删除后无法恢复。"

    /// 选项输入框占位：`选项 A` … `选项 D`，留空即该选项缺项。
    public static func optionPlaceholder(_ letter: String) -> String {
        "选项 \(letter)"
    }
}

/// P10 编辑态草稿（工作包 2.8 / C8）。
///
/// 只在内存里，未保存不落库。`isMistake` 决定要不要出「我的错因 / 自定义标签」两栏：
/// 这两项只存在于 `mistake`，未标错的录入题没有落库处（开发规范 §4.1）。
public struct UserExampleDraft: Equatable, Sendable {
    public var stem: String
    /// 四条选项，空串＝缺项（保存时按空串写库，详情侧显示「暂无」）。
    public var options: [String]
    public var answerText: String
    public var examType: String
    public var yearText: String
    public var reason: String
    public var tags: [String]
    /// 归属全集，顺序即 `user_example_idiom` 的写入顺序；至少一条。
    public var idioms: [LinkedIdiom]
    public let isMistake: Bool

    public init(detail: UserExampleDetail) {
        stem = detail.stem
        options = (0..<4).map { index in
            guard index < detail.options.count else { return "" }
            return detail.options[index] ?? ""
        }
        answerText = detail.myAnswer ?? ""
        examType = detail.examType ?? ""
        yearText = detail.year.map(String.init) ?? ""
        reason = detail.reason ?? ""
        tags = detail.tagNames
        idioms = detail.idioms
        isMistake = detail.isMistake
    }

    public var trimmedStem: String {
        stem.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// 只接受 A/B/C/D 单字母；清空输入即不填（与 `P20FormState.answer` 同一口径）。
    public var answer: String? {
        let value = answerText.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard value.count == 1, MistakeBrowse.letters.contains(value) else { return nil }
        return value
    }

    public var answerInvalid: Bool { !answerText.isEmpty && answer == nil }

    public var year: Int? {
        guard yearText.count == 4, let value = Int(yearText) else { return nil }
        return value
    }

    public var yearInvalid: Bool { !yearText.isEmpty && year == nil }

    /// 标签逐条由编辑器把关，这里兜住重复与越界长度。
    public var tagInvalid: Bool { tags.contains { !(1...8).contains($0.count) } }

    /// 字段级非法（年份 / 答案 / 标签）就地标红并把保存置灰（P20 `canSave` 同口径）；
    /// 题干为空与归属不足一条是页面级问题，点了保存才给提示，不吞掉原因。
    public var canSave: Bool { !yearInvalid && !answerInvalid && !tagInvalid }
}

/// 编辑态回调打包（与只读态的 `UserExampleActions` 分开，避免参数继续膨胀）。
public struct UserExampleEditActions {
    public let onStem: (String) -> Void
    public let onOption: (Int, String) -> Void
    public let onAnswer: (String) -> Void
    public let onExamType: (String) -> Void
    public let onYear: (String) -> Void
    public let onReason: (String) -> Void
    public let onAddTag: (String) -> Bool
    public let onRemoveTag: (String) -> Void
    public let onRemoveIdiom: (String) -> Void
    public let onOpenIdiomPicker: () -> Void

    /// 只读态与快照测试用：编辑动作不会被触达。
    /// 计算属性而非 `static let`：本类型装的是页面回调、非 `Sendable`，静态存储会撞严格并发检查
    /// （`static property 'inert' is not concurrency-safe`），每次现装即可。
    public static var inert: UserExampleEditActions {
        UserExampleEditActions(
            onStem: { _ in },
            onOption: { _, _ in },
            onAnswer: { _ in },
            onExamType: { _ in },
            onYear: { _ in },
            onReason: { _ in },
            onAddTag: { _ in false },
            onRemoveTag: { _ in },
            onRemoveIdiom: { _ in },
            onOpenIdiomPicker: {}
        )
    }
}

/// P10 编辑态表单（信息架构 v1.4 版内修订第 ① 条）：题干、选项 A–D、我的答案、
/// 出处与错因 / 标签、归属成语。原图与「是否做错」不可改，故本页不出换图与做错开关。
public struct P10UserExampleEditForm: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var tagDraft = ""
    @State private var showsTagEditor = false

    private let draft: UserExampleDraft
    private let saving: Bool
    private let actions: UserExampleEditActions

    public init(draft: UserExampleDraft, saving: Bool, actions: UserExampleEditActions) {
        self.draft = draft
        self.saving = saving
        self.actions = actions
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            contentCard
            answerCard
            idiomCard
        }
    }

    private var colors: TokenColors {
        CangyuTheme.colors(for: colorScheme)
    }

    private var fieldState: CInputState {
        saving ? .disabled : .default
    }

    /// ③ 题干 + ④ 选项 A–D：缺项留空，不改写题面。
    private var contentCard: some View {
        CCard {
            CLabel("题干")
            CInput(text: binding(draft.stem, actions.onStem), state: fieldState,
                   placeholder: UserExampleEditCopy.stemPlaceholder)
                .padding(.top, Tokens.Space.lt1Gap)
            CLabel("选项 A–D")
                .padding(.top, Tokens.Space.block)
            ForEach(Array(MistakeBrowse.letters.enumerated()), id: \.offset) { index, letter in
                CInput(
                    text: binding(option(at: index)) { actions.onOption(index, $0) },
                    state: fieldState,
                    placeholder: UserExampleEditCopy.optionPlaceholder(letter)
                )
                .padding(.top, Tokens.Space.ghostY)
            }
        }
    }

    /// ⑦ 出处 + ⑨ 我的答案与错因 / 标签：后两项仅标错的录入题可填。
    private var answerCard: some View {
        CCard(soft: true) {
            CLabel("我的答案")
            CInput(text: binding(draft.answerText, actions.onAnswer),
                   state: draft.answerInvalid ? .error : fieldState,
                   placeholder: UserExampleEditCopy.answerPlaceholder)
                .padding(.top, Tokens.Space.lt1Gap)
            if draft.isMistake {
                CLabel("我的错因")
                    .padding(.top, Tokens.Space.block)
                CInput(text: binding(draft.reason, actions.onReason), state: fieldState,
                       placeholder: UserExampleEditCopy.reasonPlaceholder)
                    .padding(.top, Tokens.Space.lt1Gap)
                CLabel("自定义标签")
                    .padding(.top, Tokens.Space.block)
                tagEditor
                    .padding(.top, Tokens.Space.lt1Gap)
            }
            CLabel("来源信息（可空）")
                .padding(.top, Tokens.Space.block)
            CInput(text: binding(draft.examType, actions.onExamType), state: fieldState,
                   placeholder: UserExampleEditCopy.examTypePlaceholder)
                .padding(.top, Tokens.Space.ghostY)
            CInput(text: binding(draft.yearText, actions.onYear),
                   state: draft.yearInvalid ? .error : fieldState,
                   placeholder: UserExampleEditCopy.yearPlaceholder,
                   errorHint: draft.yearInvalid ? UserExampleEditCopy.yearRule : nil)
                .padding(.top, Tokens.Space.ghostY)
        }
    }

    /// ⑧ 归属成语：已选以可移除标签呈现，增补走 P04 手选浮层（至少保留一条，删到只剩一条时给提示）。
    private var idiomCard: some View {
        CCard {
            CLabel("关联成语")
            VStack(alignment: .leading, spacing: Tokens.Space.lmetaGap) {
                ForEach(Array(MistakeBrowse.rows(of: draft.idioms, perRow: 3).enumerated()),
                        id: \.offset) { _, group in
                    HStack(spacing: Tokens.Space.lmetaGap) {
                        ForEach(group) { item in
                            P10RemovableChip(text: item.word, accessibilityPrefix: "移除归属",
                                             removable: !saving) {
                                actions.onRemoveIdiom(item.id)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                }
                if !saving {
                    Button(action: actions.onOpenIdiomPicker) {
                        CTag(text: UserExampleEditCopy.addIdiom, style: .ghost)
                    }
                    .buttonStyle(CPressDimStyle())
                    .accessibilityLabel(UserExampleEditCopy.addIdiom)
                }
            }
            .padding(.top, Tokens.Space.lmetaTop)
        }
    }

    private var tagEditor: some View {
        VStack(alignment: .leading, spacing: Tokens.Space.ghostY) {
            if !draft.tags.isEmpty {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 64), spacing: Tokens.Space.lmetaGap)],
                    alignment: .leading,
                    spacing: Tokens.Space.lmetaGap
                ) {
                    ForEach(draft.tags, id: \.self) { tag in
                        P10RemovableChip(text: tag, accessibilityPrefix: "移除标签",
                                         removable: !saving) {
                            actions.onRemoveTag(tag)
                        }
                    }
                }
            }
            if !saving {
                if showsTagEditor {
                    HStack(spacing: Tokens.Space.lmetaGap) {
                        CInput(text: $tagDraft, placeholder: UserExampleEditCopy.tagPlaceholder)
                            .frame(maxWidth: .infinity)
                        Button(action: addTag) {
                            CTag(text: UserExampleEditCopy.addTag,
                                 style: tagDraft.isEmpty ? .disabled : .solid)
                        }
                        .buttonStyle(CPressDimStyle())
                        .disabled(tagDraft.isEmpty)
                        .accessibilityLabel(UserExampleEditCopy.addTag)
                    }
                } else {
                    Button {
                        showsTagEditor = true
                    } label: {
                        CTag(text: UserExampleEditCopy.addOneTag, style: .ghost)
                    }
                    .buttonStyle(CPressDimStyle())
                    .accessibilityLabel("加一个标签")
                }
            }
        }
    }

    private func addTag() {
        guard actions.onAddTag(tagDraft) else { return }
        tagDraft = ""
    }

    private func option(at index: Int) -> String {
        draft.options.indices.contains(index) ? draft.options[index] : ""
    }

    /// 草稿是值类型：输入框写回时经回调交给视图模型，渲染层不持有状态。
    private func binding(_ value: String, _ set: @escaping (String) -> Void) -> Binding<String> {
        Binding(get: { value }, set: set)
    }
}

/// 编辑态与只读态共用的可移除标签（归属成语与自定义标签同一形态）。
struct P10RemovableChip: View {
    @Environment(\.colorScheme) private var colorScheme
    private let text: String
    private let accessibilityPrefix: String
    private let onRemove: () -> Void
    private let removable: Bool

    init(text: String, accessibilityPrefix: String, removable: Bool, onRemove: @escaping () -> Void) {
        self.text = text
        self.accessibilityPrefix = accessibilityPrefix
        self.removable = removable
        self.onRemove = onRemove
    }

    var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        HStack(spacing: 4) {
            Text(text)
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.tintfg)
            if removable {
                Button(action: onRemove) {
                    CIcon(.close, size: 11, strokeWidth: 1.8, color: colors.tintfg)
                }
                .buttonStyle(CPressDimStyle())
                .accessibilityLabel("\(accessibilityPrefix)\(text)")
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
}
