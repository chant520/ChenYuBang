import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P04 搜索只读查询口子：页面只依赖协议，读库实现由 app 注入。
public protocol IdiomSearchReading {
    func search(query: String, limit: Int) throws -> [IdiomRecord]
}

extension IdiomLibraryReader: IdiomSearchReading {}

/// P04 搜索页面状态与行为（文案与状态语义与 Android P04SearchViewModel 一致）。
@MainActor
public final class SearchViewModel: ObservableObject {
    @Published public private(set) var query = ""
    @Published public private(set) var results: [SearchIdiom] = []
    @Published public private(set) var loading = false
    @Published public private(set) var failed = false

    private let reader: IdiomSearchReading
    private var searchTask: Task<Void, Never>?

    public init(reader: IdiomSearchReading) {
        self.reader = reader
    }

    /// 边输边出：每次输入取消上一次在途查询，避免旧结果覆盖新结果。
    public func onQueryChange(_ text: String) {
        searchTask?.cancel()
        query = text
        results = []
        failed = false
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            loading = false
            return
        }
        loading = true
        searchTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 120_000_000)
            guard !Task.isCancelled else { return }
            do {
                let rows = try reader.search(query: text, limit: IdiomSearch.defaultLimit)
                guard !Task.isCancelled else { return }
                results = IdiomSearchUi.from(rows)
                loading = false
            } catch {
                guard !Task.isCancelled else { return }
                results = []
                loading = false
                failed = true
            }
        }
    }

    public func clear() {
        onQueryChange("")
    }
}

/// P04 搜索全屏浮层（信息架构 v1.3 P04 ①–⑤）：关闭 + 自动聚焦搜索框 + 结果 / 空态。
public struct P04SearchScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: SearchViewModel

    private let onClose: () -> Void
    private let onOpenIdiom: (String) -> Void

    @MainActor
    public init(
        reader: IdiomSearchReading,
        onClose: @escaping () -> Void,
        onOpenIdiom: @escaping (String) -> Void
    ) {
        _model = StateObject(wrappedValue: SearchViewModel(reader: reader))
        self.onClose = onClose
        self.onOpenIdiom = onOpenIdiom
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            header(colors)
            CInput(
                text: Binding(
                    get: { model.query },
                    set: { model.onQueryChange($0) }
                ),
                placeholder: "完整成语 / 单字 / 全拼 / 首字母",
                autoFocus: true,
                clearAction: model.query.isEmpty ? nil : { model.clear() }
            )
            Text(IdiomSearchUi.inputHint)
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text3)
            content(colors)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Tokens.Space.pageX)
        .padding(.vertical, Tokens.Space.contentTop)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
    }

    private func header(_ colors: TokenColors) -> some View {
        HStack(spacing: Tokens.Space.lmetaGap) {
            Button(action: onClose) {
                CIcon(.close, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                    .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                    .contentShape(Rectangle())
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel("关闭搜索")
            Text("搜索")
                .font(.system(size: Tokens.Font.n15, weight: .semibold))
                .foregroundStyle(colors.head)
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private func content(_ colors: TokenColors) -> some View {
        if model.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            EmptyView()
        } else if model.failed {
            CBar(
                kind: .warn,
                title: "搜索失败，请重试",
                subtitle: "改一个字或稍后再试；内置题库的成语仍可浏览。"
            )
        } else if model.loading {
            HStack {
                Spacer(minLength: 0)
                CSpinner()
                Spacer(minLength: 0)
            }
        } else if IdiomSearchUi.showNoResult(query: model.query, results: model.results) {
            VStack(spacing: Tokens.Space.ghostY) {
                CEmptyState(
                    title: IdiomSearchUi.noResultTitle,
                    description: IdiomSearchUi.noResultHint
                )
                Text("已输入的内容不会自动清空，改字即时出新结果")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text3)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Tokens.Space.block)
        } else {
            resultList(colors)
        }
    }

    private func resultList(_ colors: TokenColors) -> some View {
        VStack(spacing: Tokens.Space.block) {
            CList {
                ForEach(Array(model.results.enumerated()), id: \.element.id) { index, idiom in
                    CListRow(
                        name: idiom.word,
                        pinyin: idiom.pinyin,
                        sub: idiom.senseFirst,
                        action: { onOpenIdiom(idiom.id) },
                        showsDivider: index != model.results.count - 1
                    )
                }
            }
            Text("\(model.results.count) 条结果")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
                .frame(maxWidth: .infinity, alignment: .center)
        }
    }
}
