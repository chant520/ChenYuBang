import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P05 详情只读查询口子：页面只依赖协议，读库实现由 app 注入。
public protocol IdiomDetailReading {
    func detail(id: String) throws -> IdiomRecord?
}

extension IdiomLibraryReader: IdiomDetailReading {}

/// P05 页面状态与行为（文案与状态语义与 Android P05IdiomDetailViewModel 一致）。
@MainActor
public final class IdiomDetailViewModel: ObservableObject {
    @Published public private(set) var detail: IdiomDetailView?
    @Published public private(set) var loading = true
    @Published public private(set) var missing = false
    @Published public private(set) var failed = false

    private let reader: IdiomDetailReading

    public init(reader: IdiomDetailReading) {
        self.reader = reader
    }

    public func load(_ id: String) async {
        detail = nil
        missing = false
        failed = false
        loading = true
        do {
            if let record = try reader.detail(id: id) {
                detail = IdiomDetail.view(of: record)
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

/// P05 成语详情长页（信息架构 v1.3 P05 ①–⑪）：五项齐全 + 相关区 / 录入题占位。
public struct P05IdiomDetailScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: IdiomDetailViewModel

    private let idiomID: String
    private let onBack: () -> Void
    private let onOpenMistakes: () -> Void

    @MainActor
    public init(
        id: String,
        reader: IdiomDetailReading,
        onBack: @escaping () -> Void,
        onOpenMistakes: @escaping () -> Void
    ) {
        _model = StateObject(wrappedValue: IdiomDetailViewModel(reader: reader))
        self.idiomID = id
        self.onBack = onBack
        self.onOpenMistakes = onOpenMistakes
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
        .task { await model.load(idiomID) }
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
            Text("成语详情")
                .font(.system(size: Tokens.Font.n15, weight: .semibold))
                .foregroundStyle(colors.head)
            Spacer(minLength: 0)
            CTag(text: "收藏（预留）", style: .ghost)
        }
    }

    @ViewBuilder
    private func content(_ colors: TokenColors) -> some View {
        if model.loading {
            CBar(kind: .info, title: "正在读取成语详情…", subtitle: "内容来自随安装包内置的题库。")
        } else if model.missing {
            CEmptyState(
                title: "没有找到这个成语",
                description: "该词条不在当前题库中，可返回成语库换一个查看。"
            )
        } else if model.failed {
            CBar(
                kind: .warn,
                title: "详情读取失败，请重试",
                subtitle: "只重新读取本机数据，不影响已录入的内容。"
            )
        } else if let detail = model.detail {
            detailBody(detail, colors)
        }
    }

    @ViewBuilder
    private func detailBody(_ detail: IdiomDetailView, _ colors: TokenColors) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: Tokens.Space.lsubTop) {
                Text(detail.word)
                    .font(.system(size: Tokens.Font.n20, weight: .bold))
                    .foregroundStyle(colors.head)
                Text(detail.pinyin)
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text3)
            }
            Spacer(minLength: 0)
            // 读音播放是未来项；本期只展示图标，不触发播放。
            CIcon(.sound, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text3)
                .accessibilityHidden(true)
        }
        HStack {
            CTag(text: detail.color)
            Spacer(minLength: 0)
        }

        infoCard("释义", detail.sense, colors, strong: true)
        infoCard("出处", detail.origin, colors)

        CCard {
            CLabel("易错点")
            if detail.pitfalls.isEmpty {
                bodyText(IdiomDetail.placeholder, colors)
            } else {
                ForEach(detail.pitfalls, id: \.self) { item in
                    bodyText(item, colors)
                }
            }
        }

        CCard {
            CLabel("近义与反义")
            bodyText("近义：\(IdiomDetail.presentList(detail.synonyms))", colors)
            bodyText("反义：\(IdiomDetail.presentList(detail.antonyms))", colors)
        }

        relatedPlaceholder(colors)
        myExamplesPlaceholder(colors)

        // D1 单成语复习入口：MVP 内容只有错题，待阶段 3 接入真实待复习数据；无内容置灰。
        // 阶段3：P05 → P11 单成语模式，按该成语待复习错题判定可点性。
        CButton(text: "复习这个成语", variant: .primary, enabled: false, action: {})
        Text("这个成语下还没有待复习的错题")
            .font(.system(size: Tokens.Font.n12))
            .foregroundStyle(colors.text3)
    }

    private func infoCard(
        _ label: String,
        _ value: String,
        _ colors: TokenColors,
        strong: Bool = false
    ) -> some View {
        CCard {
            CLabel(label)
            Text(value)
                .font(.system(size: Tokens.Font.n13, weight: strong ? .semibold : .regular))
                .foregroundStyle(strong ? colors.text : colors.text2)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func bodyText(_ text: String, _ colors: TokenColors) -> some View {
        Text(text)
            .font(.system(size: Tokens.Font.n13))
            .foregroundStyle(colors.text2)
            .padding(.top, Tokens.Space.lsubTop)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// A4「相关题目」双来源占位。1.6 系统真题接 example_idiom；阶段2 我录入的题接 user_example。
    private func relatedPlaceholder(_ colors: TokenColors) -> some View {
        CCard {
            CLabel("相关题目")
            sectionCaption("系统题库", colors)
            bodyText("暂无真题，后续补充", colors)
            sectionCaption("我录入的题（标错才进错题本）", colors)
            bodyText("还没在这个成语下录入题", colors)
        }
    }

    private func sectionCaption(_ text: String, _ colors: TokenColors) -> some View {
        Text(text)
            .font(.system(size: Tokens.Font.n12))
            .foregroundStyle(colors.text3)
            .padding(.top, Tokens.Space.lmetaTop)
    }

    /// A5「我的录入题 N」数字入口占位。阶段2：N 接 user_example 计数，N>0 可点进 P09。
    private func myExamplesPlaceholder(_ colors: TokenColors) -> some View {
        CCard {
            HStack {
                CLabel("我在这个成语下录入了")
                Text(" 0 道题")
                    .font(.system(size: Tokens.Font.n20, weight: .bold))
                    .foregroundStyle(colors.disfg)
                Spacer(minLength: 0)
            }
            HStack {
                Text("还没在这个成语下录入题")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text3)
                Spacer(minLength: 0)
                CLink("去错题本", action: onOpenMistakes)
            }
        }
    }
}
