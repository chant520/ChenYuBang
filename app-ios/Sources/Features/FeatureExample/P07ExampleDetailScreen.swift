import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P07 详情只读查询口子：页面只依赖协议，读库实现由 app 注入。
public protocol ExampleDetailReading {
    func example(id: String) throws -> ExampleRecord?
    func relatedIdiomIDs(exampleID: String) throws -> [String]
    func idiomWords() throws -> [IdiomWordRef]
}

extension ExampleLibraryReader: ExampleDetailReading {}

/// 关联成语的展示项：id 用于跳 P05，word 为展示名（词表缺项时为占位）。
public struct RelatedIdiomRef: Identifiable, Equatable, Sendable {
    public let id: String
    public let word: String

    public init(id: String, word: String) {
        self.id = id
        self.word = word
    }
}

/// P07 页面状态与行为（文案与状态语义与 Android P07ExampleDetailViewModel 一致）。
@MainActor
public final class ExampleDetailViewModel: ObservableObject {
    @Published public private(set) var detail: ExampleDetailView?
    @Published public private(set) var relatedIdioms: [RelatedIdiomRef] = []
    @Published public private(set) var wordIndex: [String: String] = [:]
    @Published public private(set) var loading = true
    @Published public private(set) var missing = false
    @Published public private(set) var failed = false

    private let reader: ExampleDetailReading

    public init(reader: ExampleDetailReading) {
        self.reader = reader
    }

    public func load(_ id: String) async {
        detail = nil
        relatedIdioms = []
        wordIndex = [:]
        missing = false
        failed = false
        loading = true
        do {
            if let record = try reader.example(id: id) {
                let words = try reader.idiomWords()
                let index = Dictionary(words.map { ($0.id, $0.word) }, uniquingKeysWith: { first, _ in first })
                detail = ExampleDetail.view(of: record)
                wordIndex = index
                relatedIdioms = try reader.relatedIdiomIDs(exampleID: id).map {
                    RelatedIdiomRef(id: $0, word: index[$0] ?? ExampleDetail.placeholder)
                }
            } else {
                missing = true
            }
            loading = false
        } catch {
            loading = false
            failed = true
        }
    }
}

/// P07 例题详情长页（信息架构 v1.4 P07 ①–⑧）：题干 / 四选项 / 解析里的成语点击跳 P05，
/// 关联成语区并排展示本题全部关联成语（B3）。本期不做做题判分（二期 B6）。
public struct P07ExampleDetailScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: ExampleDetailViewModel

    private let exampleID: String
    private let onBack: () -> Void
    private let onOpenIdiom: (String) -> Void

    @MainActor
    public init(
        id: String,
        reader: ExampleDetailReading,
        onBack: @escaping () -> Void,
        onOpenIdiom: @escaping (String) -> Void
    ) {
        _model = StateObject(wrappedValue: ExampleDetailViewModel(reader: reader))
        self.exampleID = id
        self.onBack = onBack
        self.onOpenIdiom = onOpenIdiom
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                header(colors)
                content(colors)
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.contentTop)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
        .environment(\.openURL, OpenURLAction { url in openIdiomLink(url) })
        .task { await model.load(exampleID) }
    }

    private func header(_ colors: TokenColors) -> some View {
        HStack(spacing: Tokens.Space.lmetaGap) {
            Button(action: onBack) {
                CIcon(.back, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                    .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                    .contentShape(Rectangle())
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel("返回")
            Text("例题详情")
                .font(.system(size: Tokens.Font.n15, weight: .semibold))
                .foregroundStyle(colors.head)
            Spacer(minLength: 0)
            CTag(text: "标记（预留）", style: .ghost)
        }
    }

    @ViewBuilder
    private func content(_ colors: TokenColors) -> some View {
        if model.loading {
            CBar(kind: .info, title: "正在读取例题详情…", subtitle: "内容来自随安装包内置的题库。")
        } else if model.missing {
            CEmptyState(
                title: "没有找到这道例题",
                description: "该题不在当前题库中，可返回上一个页面换一道看。"
            )
        } else if model.failed {
            CBar(
                kind: .warn,
                title: "例题读取失败，请重试",
                subtitle: "只重新读取本机数据，不影响已录入的内容。",
                actionTitle: "重试",
                onAction: retry
            )
        } else if let detail = model.detail {
            detailBody(detail, colors)
        }
    }

    private func retry() {
        Task { await model.load(exampleID) }
    }

    @ViewBuilder
    private func detailBody(_ detail: ExampleDetailView, _ colors: TokenColors) -> some View {
        // ② 出处行：年份 · 地区 · 考试类型 · 题型
        CCard {
            bodyText(detail.sourceLine, colors)
        }

        // ③ 题干 + ④ 四个选项（成语可点跳 P05）
        CCard {
            CLabel("题干")
            annotated(detail.stem, colors, strong: true)
            optionRow("A", detail.optionA, colors)
            optionRow("B", detail.optionB, colors)
            optionRow("C", detail.optionC, colors)
            optionRow("D", detail.optionD, colors)
        }

        // ⑥ 正确答案 + 一句解析（只说明为什么选这项，决策 5）
        CCard {
            CLabel("正确答案与解析")
            Text("正确答案：\(detail.answer)")
                .font(.system(size: Tokens.Font.n13, weight: .semibold))
                .foregroundStyle(colors.text)
                .padding(.top, Tokens.Space.lsubTop)
            annotated(detail.explain, colors, topPad: true)
        }

        // ⑧ 关联成语区：一题多成语并排展示全部关联成语，每个可点进 P05
        CCard {
            CLabel("本题关联成语")
            relatedTags(colors)
        }

        // ⑤ 做题判分：二期（B6 进阶），本期不出现先选后判。
    }

    private func optionRow(_ letter: String, _ option: String, _ colors: TokenColors) -> some View {
        HStack(alignment: .top, spacing: Tokens.Space.lmetaGap) {
            Text(letter)
                .font(.system(size: Tokens.Font.n13, weight: .semibold))
                .foregroundStyle(colors.text3)
            annotated(option, colors, topPad: true)
        }
        .padding(.top, Tokens.Space.lmetaTop)
    }

    @ViewBuilder
    private func relatedTags(_ colors: TokenColors) -> some View {
        if model.relatedIdioms.isEmpty {
            bodyText("暂未关联成语", colors)
        } else {
            let groups = rows(of: model.relatedIdioms, perRow: 3)
            VStack(alignment: .leading, spacing: Tokens.Space.lmetaGap) {
                // iOS 15 无流式布局 API（开发规范 §7 平台映射）：按固定每行 3 个分组。
                ForEach(groups.indices, id: \.self) { index in
                    HStack(spacing: Tokens.Space.lmetaGap) {
                        ForEach(groups[index]) { item in
                            Button {
                                onOpenIdiom(item.id)
                            } label: {
                                CTag(item.word)
                            }
                            .buttonStyle(CPressDimStyle())
                        }
                        Spacer(minLength: 0)
                    }
                }
            }
            .padding(.top, Tokens.Space.lmetaTop)
        }
    }

    private func rows<T>(of items: [T], perRow: Int) -> [[T]] {
        stride(from: 0, to: items.count, by: perRow).map {
            Array(items[$0..<min($0 + perRow, items.count)])
        }
    }

    /// 把命中成语渲染为 accent + 下划线 + 可点链接（custom scheme 交由 openURL 拦截），其余为普通正文。
    private func annotated(
        _ text: String,
        _ colors: TokenColors,
        strong: Bool = false,
        topPad: Bool = false
    ) -> some View {
        let hits = IdiomHighlight.match(text, wordIndex: model.wordIndex)
        return Text(annotatedString(text, hits: hits, colors: colors, strong: strong))
            .font(.system(size: Tokens.Font.n13, weight: strong ? .semibold : .regular))
            .foregroundStyle(strong ? colors.text : colors.text2)
            .padding(.top, topPad ? Tokens.Space.lsubTop : 0)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func annotatedString(
        _ text: String,
        hits: [IdiomHighlightHit],
        colors: TokenColors,
        strong: Bool
    ) -> AttributedString {
        let characters = Array(text)
        let base = strong ? colors.text : colors.text2
        var result = AttributedString()
        var cursor = 0
        for hit in hits {
            if hit.start > cursor {
                result += plain(String(characters[cursor..<hit.start]), color: base)
            }
            var linked = AttributedString(String(characters[hit.start..<hit.end]))
            linked.foregroundColor = colors.accent
            linked.underlineStyle = .single
            linked.link = URL(string: "chenyubang://idiom/\(hit.idiomID)")
            result += linked
            cursor = hit.end
        }
        if cursor < characters.count {
            result += plain(String(characters[cursor...]), color: base)
        }
        return result
    }

    private func plain(_ text: String, color: Color) -> AttributedString {
        var part = AttributedString(text)
        part.foregroundColor = color
        return part
    }

    /// 拦截 `chenyubang://idiom/{id}` 链接，转交 app 路由跳 P05；其他链接走系统默认。
    private func openIdiomLink(_ url: URL) -> OpenURLAction.Result {
        guard url.scheme == "chenyubang", url.host == "idiom" else { return .systemAction }
        let idiomID = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !idiomID.isEmpty else { return .handled }
        onOpenIdiom(idiomID)
        return .handled
    }

    private func bodyText(_ text: String, _ colors: TokenColors) -> some View {
        Text(text)
            .font(.system(size: Tokens.Font.n13))
            .foregroundStyle(colors.text2)
            .padding(.top, Tokens.Space.lsubTop)
            .fixedSize(horizontal: false, vertical: true)
    }
}
