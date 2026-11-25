import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// A6 三态文字徽标映射（拍板口径：未学=disabled、学过=solid 浅绿、已掌握=selected 实绿）。
/// P03 / P24 共用，只展示不切换。
struct StatusTag {
    let text: String
    let style: CTagStyle
}

func statusTag(for status: IdiomLearningStatus) -> StatusTag {
    switch status {
    case .notStarted: return StatusTag(text: "未学", style: .disabled)
    case .studied: return StatusTag(text: "学过", style: .solid)
    case .proficient: return StatusTag(text: "已掌握", style: .selected)
    }
}

/// P03 学习状态只读口子：页面只依赖协议，实现由 app 注入的 IdiomStateStore 承担。
public protocol IdiomStateReading {
    func statesForIds(_ ids: [String]) throws -> [IdiomLearningState]
}

extension IdiomStateStore: IdiomStateReading {}

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
    /// A6 学习状态：仅已显式落行的成语有值，缺行由 status(of:) 投影补未学。
    @Published public private(set) var states: [String: IdiomLearningState] = [:]
    @Published public private(set) var loading = true
    @Published public private(set) var failed = false
    @Published public private(set) var updateFailure: String?

    public var scopeEmpty: Bool { scope.isEmpty }

    /// 缺行=未学、未收藏，不创建 idiom_state 行。
    public func status(of idiomID: String) -> IdiomLearningState {
        states[idiomID] ?? IdiomLearningState(idiomID: idiomID, status: .notStarted, favorite: false)
    }

    private let reader: IdiomLibraryReading
    private let stateReader: IdiomStateReading?
    private let refreshTrigger: LibraryRefreshTrigger
    private let refreshFailure: (() -> String?)?
    private var items: [LibraryIdiom] = []

    public init(
        scope: ExamScopeSelection,
        reader: IdiomLibraryReading,
        stateReader: IdiomStateReading? = nil,
        refreshTrigger: LibraryRefreshTrigger = NoLibraryRefreshTrigger(),
        refreshFailure: (() -> String?)? = nil
    ) {
        self.scope = scope
        self.reader = reader
        self.stateReader = stateReader
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
        // A6：一次性批量取该页所有成语状态，避免逐行 state 的 N+1；只取显式落行，缺行展示时补未学。
        let rows = try stateReader?.statesForIds(items.map(\.id)) ?? []
        states = Dictionary(uniqueKeysWithValues: rows.map { ($0.idiomID, $0) })
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
        stateReader: IdiomStateReading? = nil,
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
            stateReader: stateReader,
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

    private func content(_ colors: TokenColors) -> some View {
        Group {
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
                                    // A6 学习状态位：按 idiom_state 三态展示，缺行补未学；只读不切换。
                                    let tag = statusTag(for: model.status(of: idiom.id).status)
                                    CTag(text: tag.text, style: tag.style)
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
