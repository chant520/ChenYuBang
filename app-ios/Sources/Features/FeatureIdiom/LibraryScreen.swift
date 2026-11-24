import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// 读库口子：页面只依赖协议，读库实现由 app 注入（不把 DatabaseQueue 带进视图）。
public protocol IdiomLibraryReading {
    func load() throws -> [LibraryIdiom]
}

extension IdiomLibraryReader: IdiomLibraryReading {}

/// 进页拉取触发点。阶段 1 只留接口、不联网；阶段 5 接入题库获取
/// （两段式 latest.json + 整包校验 + 原子切换），失败回落内置包、不阻断浏览。
public protocol LibraryRefreshTrigger: Sendable {
    func onEnterLibrary() async
}

public struct NoLibraryRefreshTrigger: LibraryRefreshTrigger, Sendable {
    public init() {}
    public func onEnterLibrary() async {}
}

/// P03 页面状态与行为（文案与状态语义与 Android P03LibraryViewModel 一致）。
@MainActor
public final class LibraryViewModel: ObservableObject {
    @Published public private(set) var scope: ExamScopeSelection
    @Published public private(set) var dimension: LibraryDimension = .scope
    @Published public private(set) var selectedLetter: String?
    @Published public private(set) var letters: [String] = []
    @Published public private(set) var page = LibraryPage(sections: [])
    @Published public private(set) var loading = true
    @Published public private(set) var failed = false
    @Published public private(set) var updateFailure: String?

    public var scopeEmpty: Bool { scope.isEmpty }

    private let reader: IdiomLibraryReading
    private let refreshTrigger: LibraryRefreshTrigger
    private let refreshFailure: (() -> String?)?
    private var items: [LibraryIdiom] = []

    public init(
        scope: ExamScopeSelection,
        reader: IdiomLibraryReading,
        refreshTrigger: LibraryRefreshTrigger = NoLibraryRefreshTrigger(),
        refreshFailure: (() -> String?)? = nil
    ) {
        self.scope = scope
        self.reader = reader
        self.refreshTrigger = refreshTrigger
        self.refreshFailure = refreshFailure
    }

    /// 先发布本机内容，再异步检查更新；网络等待不阻塞浏览。
    public func load(_ scope: ExamScopeSelection) async {
        self.scope = scope
        loading = true
        failed = false
        updateFailure = nil
        do {
            try publishLocalContent()
        } catch {
            showLocalFailure()
        }
        await refreshTrigger.onEnterLibrary()
        if refreshFailure?() == nil {
            do {
                try publishLocalContent()
            } catch {
                showLocalFailure()
            }
        }
        updateFailure = refreshFailure?()
    }

    public func selectDimension(_ dimension: LibraryDimension) {
        self.dimension = dimension
        applyPage()
    }

    /// 点索引字母：重复点同一字母取消该字母条件。
    public func selectLetter(_ letter: String) {
        selectedLetter = selectedLetter == letter ? nil : letter
        applyPage()
    }

    /// 空态的「清除条件，看全部」。
    public func clearConditions() {
        dimension = .scope
        selectedLetter = nil
        applyPage()
    }

    public func retry() async {
        await load(scope)
    }

    private func publishLocalContent() throws {
        items = try reader.load()
        letters = LibraryBrowse.letters(items)
        page = LibraryBrowse.page(items, dimension: dimension, letter: selectedLetter)
        loading = false
        failed = false
    }

    private func showLocalFailure() {
        items = []
        page = LibraryPage(sections: [])
        loading = false
        failed = true
    }

    private func applyPage() {
        page = LibraryBrowse.page(items, dimension: dimension, letter: selectedLetter)
    }
}

/// P03 成语库页（信息架构 v1.2 P03 ②–⑦）：范围卡 + 维度切换 + 列表 + 字母索引 + 空态。
public struct LibraryScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: LibraryViewModel

    private let onOpenScope: () -> Void
    private let onOpenIdiom: (String) -> Void
    private let onSearch: () -> Void
    private let onBack: (() -> Void)?
    private let isActive: Bool

    @MainActor
    public init(
        scope: ExamScopeSelection,
        reader: IdiomLibraryReading,
        onOpenScope: @escaping () -> Void,
        onOpenIdiom: @escaping (String) -> Void,
        onSearch: @escaping () -> Void,
        onBack: (() -> Void)? = nil,
        refreshTrigger: LibraryRefreshTrigger = NoLibraryRefreshTrigger(),
        refreshFailure: (() -> String?)? = nil,
        isActive: Bool = true
    ) {
        _model = StateObject(wrappedValue: LibraryViewModel(
            scope: scope,
            reader: reader,
            refreshTrigger: refreshTrigger,
            refreshFailure: refreshFailure
        ))
        self.onOpenScope = onOpenScope
        self.onOpenIdiom = onOpenIdiom
        self.onSearch = onSearch
        self.onBack = onBack
        self.isActive = isActive
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        HStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Tokens.Space.block) {
                    header(colors)
                    rangeCard(colors)
                    CSegmentedControl(
                        items: LibraryDimension.allCases.map(\.label),
                        selection: Binding(
                            get: { model.dimension.rawValue },
                            set: { model.selectDimension(LibraryDimension(rawValue: $0) ?? .scope) }
                        )
                    )
                    content(colors)
                    footer(colors)
                }
                .padding(.horizontal, Tokens.Space.pageX)
                .padding(.top, Tokens.Space.contentTop)
                .padding(.bottom, Tokens.Space.block)
            }
            if !model.letters.isEmpty {
                CLetterIndex(letters: model.letters, selected: model.selectedLetter) { letter in
                    model.selectLetter(letter)
                }
                .padding(.trailing, Tokens.Space.lsubTop)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
        .task(id: isActive) {
            if isActive { await model.load(model.scope) }
        }
    }

    private func header(_ colors: TokenColors) -> some View {
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            HStack(spacing: 0) {
                Text("成语库")
                    .font(.system(size: Tokens.Font.n20, weight: .semibold))
                    .foregroundStyle(colors.text)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                Button(action: onSearch) {
                    CIcon(.search, size: Tokens.Icon.n21, strokeWidth: 1.8, color: colors.accent)
                        .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                        .contentShape(Rectangle())
                }
                .buttonStyle(CPressDimStyle())
                .accessibilityLabel("搜索成语")
            }
            if let onBack {
                CButton(text: "返回", variant: .ghost, action: onBack)
            }
        }
    }

    private func rangeCard(_ colors: TokenColors) -> some View {
        CCard {
            // 方案 A 同排（用户 2026-11-19 拍板；草图 P03范围卡同排方案-v0.1.html 已于 2026-11-20 清理）：范围文字与「调整省份」链接同一行；
            // 空间不足时范围文字折行、链接保持右对齐单行不换行。
            HStack(alignment: .center, spacing: Tokens.Space.lmetaGap) {
                rangeLine(colors)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                CLink("调整省份", action: onOpenScope)
            }
            // 省份仅为筛选维度：中性说明行，无橙字、无警示（信息架构 v1.4 P03 ②）。
            if model.scope.provincial && !model.scopeEmpty {
                Text("省份用于筛选省考内容，可自行增减")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if model.scopeEmpty {
                Text("不选范围时成语库和例题库会是空的，可以稍后在『我的』里改。")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text3)
                    .padding(.top, Tokens.Space.lsubTop)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// 范围行：`范围：国考 · 省考 · 事业编`，范围三段加重（省份仅为筛选维度，无警示片段）。
    private func rangeLine(_ colors: TokenColors) -> Text {
        Text("范围：")
            .font(.system(size: Tokens.Font.n13))
            .foregroundColor(colors.text2) +
            Text(model.scope.description)
                .font(.system(size: Tokens.Font.n13, weight: .semibold))
                .foregroundColor(colors.text)
    }

    @ViewBuilder
    @ViewBuilder
    private func content(_ colors: TokenColors) some View {
        if let failure = model.updateFailure {
            CBar(
                kind: .warn,
                title: failure,
                subtitle: "当前题库仍可离线浏览，版本号不变；可到题库版本页重试。"
            )
        }
        if model.failed {
            CBar(
                kind: .warn,
                title: "成语库读取失败，请重试",
                subtitle: "内置题库仍可继续浏览；重试只重新读取本机数据。",
                actionTitle: "重试",
                onAction: { Task { await model.retry() } }
            )
        } else if model.loading {
            HStack {
                Spacer(minLength: 0)
                CSpinner()
                Spacer(minLength: 0)
            }
        } else if model.page.isEmpty {
            emptyState
        } else {
            idiomList(colors)
        }
    }

    private func idiomList(_ colors: TokenColors) -> some View {
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            ForEach(model.page.sections) { section in
                VStack(alignment: .leading, spacing: 0) {
                    if let letter = section.letter {
                        Text(letter)
                            .font(.system(size: Tokens.Font.n10))
                            .foregroundStyle(colors.text3)
                            .padding(.top, Tokens.Space.lmetaTop)
                            .padding(.bottom, Tokens.Space.lsubTop)
                    }
                    CList {
                        ForEach(Array(section.items.enumerated()), id: \.element.id) { index, idiom in
                            CListRow(
                                name: idiom.word,
                                pinyin: idiom.pinyin,
                                action: { onOpenIdiom(idiom.id) },
                                showsDivider: index != section.items.count - 1,
                                meta: {
                                    CTag(text: "考频 \(idiom.frequency)")
                                    // A6 学习状态位：MVP 只呈现预留位，不读写 idiom_state。
                                    CTag(text: "未学", style: .disabled)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: Tokens.Space.ghostY) {
            CEmptyState(
                title: "这个条件下还没有成语",
                description: emptyDescription,
                actionTitle: "清除条件，看全部",
                onAction: { model.clearConditions() }
            )
            CButton(text: "去改考试范围", variant: .ghost, action: onOpenScope)
                .padding(.horizontal, Tokens.Space.pageX)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Tokens.Space.block)
    }

    private var emptyDescription: String {
        let scopeText = model.scope.description
        if let letter = model.selectedLetter {
            return "当前范围是「\(scopeText)」，首字母「\(letter)」下没有收录。" +
                "范围越窄、字母越靠后，能看到的就越少。"
        }
        return "当前范围是「\(scopeText)」。" +
            "范围越窄，能看到的就越少；改范围或多选几类考试即可看到更多成语。"
    }

    private func footer(_ colors: TokenColors) -> some View {
        Text("共 \(model.page.count) 条 · 已到末尾")
            .font(.system(size: Tokens.Font.n11))
            .foregroundStyle(colors.text3)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.top, Tokens.Space.ghostY)
    }
}
