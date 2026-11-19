import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P14 与 P01 共用的同一控件，只差标题区与按钮文案（与 Android ExamScopeMode 一致）。
public enum ExamScopeMode {
    case onboarding
    case settings
}

public enum ExamScopeType {
    case national
    case provincial
    case institution
}

/// P14 / P01 共用的范围草稿（未保存态），语义与 Android ExamScopeDraft 一致：
/// 省考勾选时省份默认全选、省考内容正常显示，省份仅为可增减的筛选维度；
/// 关闭省考时保留已选省份。
public struct ExamScopeDraft: Equatable {
    public var national: Bool
    public var provincial: Bool
    public var institution: Bool
    public var provinces: Set<String>

    public init(
        national: Bool = true,
        provincial: Bool = true,
        institution: Bool = true,
        provinces: Set<String> = []
    ) {
        self.national = national
        self.provincial = provincial
        self.institution = institution
        self.provinces = provinces
    }

    public init(selection: ExamScopeSelection) {
        self.init(
            national: selection.national,
            provincial: selection.provincial,
            institution: selection.institution,
            provinces: selection.provinces
        )
    }

    /// 三类全不选：允许进入，但成语库 / 例题库会是空的（IA 5.4 末行）。
    public var isEmpty: Bool { !national && !provincial && !institution }

    /// 切换勾选态。[availableProvinces] 为当前题库下的可选省份：
    /// 开启省考且此前未选过省份时，默认全选所有可选省份（省份仅为筛选维度，可逐个取消）。
    public func toggled(_ type: ExamScopeType, availableProvinces: [String] = []) -> ExamScopeDraft {
        var copy = self
        switch type {
        case .national: copy.national.toggle()
        case .provincial:
            copy.provincial.toggle()
            if copy.provincial && copy.provinces.isEmpty && !availableProvinces.isEmpty {
                copy.provinces = Set(availableProvinces)
            }
        case .institution: copy.institution.toggle()
        }
        return copy
    }

    public func togglingProvince(_ province: String) -> ExamScopeDraft {
        var copy = self
        if copy.provinces.contains(province) {
            copy.provinces.remove(province)
        } else {
            copy.provinces.insert(province)
        }
        return copy
    }

    public func toSelection() -> ExamScopeSelection {
        ExamScopeSelection(
            national: national,
            provincial: provincial,
            institution: institution,
            provinces: provinces
        )
    }

    /// 省考行右侧的说明文字：省份覆盖全部可选省份时写「省份全选」，
    /// 收窄后逐个列出；全省份取消（筛选维度允许）或题库暂无省份数据时只写「已选」。
    public func provincialTrailing(availableProvinces: [String]) -> String {
        if !provincial { return "未选" }
        if provinces.isEmpty { return "已选" }
        if !availableProvinces.isEmpty && provinces == Set(availableProvinces) { return "已选 · 省份全选" }
        return "已选 · " + provinces.sorted().joined(separator: "、")
    }
}

/// P14 考试范围设置页（信息架构 v1.4 P14 ②–⑧）。
///
/// - 国考 / 省考（可选省份）/ 事业编勾选；教师编禁用并写「题库到位后开放」。
/// - 省考勾选时省份默认全选、省考内容正常显示，省份仅为筛选维度，无「未选省份」异常态。
/// - 保存通过 `onSave(selection, complete)` 交给 app 层写 `exam_scope` 并重建可见范围。
public struct ExamScopeScreen: View {
    @Environment(\.colorScheme) private var colorScheme

    @State private var draft: ExamScopeDraft
    @State private var saving = false
    @State private var failed = false

    private let mode: ExamScopeMode
    private let availableProvinces: [String]
    private let onSave: (ExamScopeSelection, Bool) throws -> Void
    private let onCancel: (() -> Void)?

    @MainActor
    public init(
        mode: ExamScopeMode,
        initial: ExamScopeSelection,
        availableProvinces: [String],
        onSave: @escaping (ExamScopeSelection, Bool) throws -> Void,
        onCancel: (() -> Void)? = nil
    ) {
        _draft = State(initialValue: ExamScopeDraft(selection: initial))
        self.mode = mode
        self.availableProvinces = availableProvinces
        self.onSave = onSave
        self.onCancel = onCancel
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                if mode == .onboarding {
                    brandBlock(colors)
                } else {
                    Text("考试范围设置")
                        .font(.system(size: Tokens.Font.n20, weight: .semibold))
                        .foregroundStyle(colors.text)
                        .accessibilityAddTraits(.isHeader)
                }
                scopeCard(colors)
                effectiveScopeCard(colors)
                if draft.isEmpty {
                    Text("不选范围时成语库和例题库会是空的，可以稍后在『我的』里改。")
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(colors.text3)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if failed {
                    Text("范围保存失败，请重试；当前选择已保留。")
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(colors.warnfg)
                        .fixedSize(horizontal: false, vertical: true)
                }
                CButton(
                    text: mode == .onboarding ? "进入" : "保存",
                    loading: saving,
                    action: save
                )
                if mode == .onboarding {
                    CButton(text: "跳过，沿用默认", variant: .ghost, enabled: !saving, action: save)
                } else if let onCancel {
                    CButton(text: "取消", variant: .ghost, enabled: !saving, action: onCancel)
                }
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.contentTop)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
    }

    private func brandBlock(_ colors: TokenColors) -> some View {
        VStack(spacing: Tokens.Space.lsubTop) {
            CIcon(.book, size: Tokens.Icon.n26, strokeWidth: 1.7, color: colors.accent)
            Text("成语记忆")
                .font(.system(size: Tokens.Font.n20, weight: .bold))
                .foregroundStyle(colors.head)
                .padding(.top, Tokens.Space.lsubTop)
            Text("把考公考编的成语错题，记成自己的题库")
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text3)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Tokens.Space.block)
    }

    private func scopeCard(_ colors: TokenColors) -> some View {
        CCard {
            CLabel(mode == .onboarding ? "首次打开 · 先定考试范围" : "勾选要考的类别")
            Color.clear.frame(height: Tokens.Space.lsubTop)
            CCheckRow(
                label: "国考",
                isOn: binding(for: .national),
                enabled: !saving,
                trailingText: draft.national ? "已选" : "未选"
            )
            CCheckRow(
                label: "省考",
                isOn: binding(for: .provincial),
                enabled: !saving,
                trailingText: draft.provincialTrailing(availableProvinces: availableProvinces),
                showsDivider: draft.provincial
            )
            if draft.provincial {
                provinceGroup(colors)
            }
            Color.clear.frame(height: Tokens.Space.lsubTop)
            CCheckRow(
                label: "事业编",
                isOn: binding(for: .institution),
                enabled: !saving,
                trailingText: draft.institution ? "已选" : "未选",
                showsDivider: false
            )
            CCheckRow(
                label: "教师编",
                isOn: .constant(false),
                enabled: false,
                trailingText: "题库到位后开放",
                showsDivider: false
            )
        }
    }

    private func provinceGroup(_ colors: TokenColors) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            CLabel("省考省份（可多选）")
            Text("省份用于筛选省考内容，可自行增减")
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text3)
                .padding(.top, Tokens.Space.lsubTop)
                .fixedSize(horizontal: false, vertical: true)
            if availableProvinces.isEmpty {
                Text("当前题库没有省考省份数据，选好题库版本后再来。")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text3)
                    .padding(.top, Tokens.Space.lsubTop)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                ForEach(availableProvinces, id: \.self) { province in
                    CCheckRow(
                        label: province,
                        isOn: Binding(
                            get: { draft.provinces.contains(province) },
                            set: { isOn in
                                if isOn != draft.provinces.contains(province) {
                                    draft = draft.togglingProvince(province)
                                }
                            }
                        ),
                        enabled: !saving,
                        showsDivider: province != availableProvinces.last
                    )
                }
            }
        }
        .padding(.top, Tokens.Space.lmetaTop)
    }

    private func effectiveScopeCard(_ colors: TokenColors) -> some View {
        CCard(soft: true) {
            CLabel("生效范围")
            Color.clear.frame(height: Tokens.Space.lsubTop)
            Text("范围决定成语库与例题库里能看到哪些内容；省份用于筛选省考内容，不改变页面的其他功能。")
                .font(.system(size: Tokens.Font.n13))
                .foregroundStyle(colors.text2)
                .fixedSize(horizontal: false, vertical: true)
            Text("改范围不会影响已录的错题、收藏与学习状态；P01 引导用的是同一份数据，两边同步。")
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text3)
                .padding(.top, Tokens.Space.lsubTop)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func binding(for type: ExamScopeType) -> Binding<Bool> {
        Binding(
            get: {
                switch type {
                case .national: return draft.national
                case .provincial: return draft.provincial
                case .institution: return draft.institution
                }
            },
            set: { isOn in
                if isOn != currentValue(of: type) {
                    draft = draft.toggled(type, availableProvinces: availableProvinces)
                }
            }
        )
    }

    private func currentValue(of type: ExamScopeType) -> Bool {
        switch type {
        case .national: return draft.national
        case .provincial: return draft.provincial
        case .institution: return draft.institution
        }
    }

    private func save() {
        guard !saving else { return }
        saving = true
        failed = false
        defer { saving = false }
        do {
            try onSave(draft.toSelection(), mode == .onboarding)
        } catch {
            failed = true
        }
    }
}
