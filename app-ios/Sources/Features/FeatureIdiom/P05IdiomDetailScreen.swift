import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P05 详情只读查询口子：页面只依赖协议，读库实现由 app 注入。
public protocol IdiomDetailReading {
    func detail(id: String) throws -> IdiomRecord?
}

extension IdiomLibraryReader: IdiomDetailReading {}

/// P05「相关题目 · 系统题库」只读查询口子（工作包 1.6）：页面只依赖协议，读库实现由 app 注入。
public protocol IdiomExamplesReading {
    func examples(idiomID: String) throws -> [ExampleRecord]
}

extension ExampleLibraryReader: IdiomExamplesReading {}

/// P05「复习这个成语」置灰判定口子（阶段 3，决策 H）：只取该成语的待复习错题数。
/// 读库实现 `ReviewLibraryService` 属 feature-review / 数据层，feature 之间不互相引用，
/// 故在本侧声明协议并由 app 层注入实例（与 `MistakeLibraryReader: MistakeBookReading` 同构）。
public protocol PendingReviewCountReading {
    func pendingCount(idiomID: String) throws -> Int
}

extension ReviewLibraryService: PendingReviewCountReading {}

/// P05「相关题目 · 系统题库」单行展示模型（与 Android IdiomExampleView 同口径）：
/// 题干摘要 + 「年份 · 地区 · 考试类型」+ 题型，缺字段已在 `IdiomExamples.view(of:)` 归到「暂无」。
public struct IdiomExampleView: Identifiable, Equatable, Sendable {
    public let id: String
    public let stem: String
    public let sourceLine: String
    public let questionType: String

    public init(id: String, stem: String, sourceLine: String, questionType: String) {
        self.id = id
        self.stem = stem
        self.sourceLine = sourceLine
        self.questionType = questionType
    }
}

/// 系统真题行取值（纯函数，与 Android IdiomExamples 同口径）：只做字段呈现，不改写题目内容。
public enum IdiomExamples {
    public static func view(of record: ExampleRecord) -> IdiomExampleView {
        IdiomExampleView(
            id: record.id,
            stem: IdiomDetail.present(record.stem),
            sourceLine: sourceLine(record),
            questionType: IdiomDetail.present(record.questionType)
        )
    }

    /// 年份 · 地区 · 考试类型；缺项跳过，全缺归「暂无」。
    public static func sourceLine(_ record: ExampleRecord) -> String {
        let parts = [
            record.year > 0 ? String(record.year) : nil,
            presentOrNil(record.region),
            presentOrNil(record.examType)
        ].compactMap { $0 }
        return parts.isEmpty ? IdiomDetail.placeholder : parts.joined(separator: " · ")
    }

    private static func presentOrNil(_ value: String) -> String? {
        let present = IdiomDetail.present(value)
        return present == IdiomDetail.placeholder ? nil : present
    }
}

/// P05 页面状态与行为（文案与状态语义与 Android P05IdiomDetailViewModel 一致）。
@MainActor
public final class IdiomDetailViewModel: ObservableObject {
    @Published public private(set) var detail: IdiomDetailView?
    @Published public private(set) var examples: [IdiomExampleView] = []
    @Published public private(set) var loading = true
    @Published public private(set) var missing = false
    @Published public private(set) var failed = false
    /// 该成语的待复习错题数（决策 H：0 时「复习这个成语」保持置灰）。
    @Published public private(set) var pendingReviewCount = 0

    private let reader: IdiomDetailReading
    private let examplesReader: IdiomExamplesReading
    private let reviewCounter: PendingReviewCountReading?

    public init(
        reader: IdiomDetailReading,
        examplesReader: IdiomExamplesReading,
        reviewCounter: PendingReviewCountReading? = nil
    ) {
        self.reader = reader
        self.examplesReader = examplesReader
        self.reviewCounter = reviewCounter
    }

    public func load(_ id: String) async {
        detail = nil
        examples = []
        missing = false
        failed = false
        pendingReviewCount = 0
        loading = true
        do {
            if let record = try reader.detail(id: id) {
                detail = IdiomDetail.view(of: record)
                examples = try examplesReader.examples(idiomID: id).map(IdiomExamples.view(of:))
                pendingReviewCount = try reviewCounter?.pendingCount(idiomID: id) ?? 0
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
    private let onOpenExample: (String) -> Void
    /// 单成语复习入口（决策 H）：参数为该成语词面，供 P11 / P12 的「本轮范围」一行显示。
    private let onReviewIdiom: (String) -> Void

    @MainActor
    public init(
        id: String,
        reader: IdiomDetailReading,
        examplesReader: IdiomExamplesReading,
        reviewCounter: PendingReviewCountReading? = nil,
        onBack: @escaping () -> Void,
        onOpenMistakes: @escaping () -> Void,
        onOpenExample: @escaping (String) -> Void,
        onReviewIdiom: @escaping (String) -> Void = { _ in }
    ) {
        _model = StateObject(wrappedValue: IdiomDetailViewModel(
            reader: reader,
            examplesReader: examplesReader,
            reviewCounter: reviewCounter
        ))
        self.idiomID = id
        self.onBack = onBack
        self.onOpenMistakes = onOpenMistakes
        self.onOpenExample = onOpenExample
        self.onReviewIdiom = onReviewIdiom
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

        relatedExamplesCard(colors)
        myExamplesPlaceholder(colors)

        // D1 单成语复习入口（决策 H）：按该成语待复习错题数判定可点性，为 0 时保持置灰并说明原因。
        CButton(
            text: "复习这个成语",
            variant: .primary,
            enabled: model.pendingReviewCount > 0,
            action: { onReviewIdiom(detail.word) }
        )
        if model.pendingReviewCount == 0 {
            Text("这个成语下还没有待复习的错题")
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text3)
        }
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

    /// A4「相关题目」双来源（信息架构 v1.4 P05 ⑧）：系统题库按 example_idiom 列出关联真题（1.6），
    /// 每条题干摘要 + 年份 · 地区 · 考试类型 + 题型，点击进 P07；暂无时保留文案不隐藏。
    /// 阶段2：我录入的题接 user_example，暂无时保留「还没在这个成语下录入题」不隐藏。
    private func relatedExamplesCard(_ colors: TokenColors) -> some View {
        CCard {
            CLabel("相关题目")
            sectionCaption("系统题库", colors)
            if model.examples.isEmpty {
                bodyText("暂无真题，后续补充", colors)
            } else {
                ForEach(model.examples) { example in
                    relatedExampleRow(example, colors)
                }
            }
            sectionCaption("我录入的题（标错才进错题本）", colors)
            bodyText("还没在这个成语下录入题", colors)
        }
    }

    private func relatedExampleRow(_ example: IdiomExampleView, _ colors: TokenColors) -> some View {
        Button {
            onOpenExample(example.id)
        } label: {
            VStack(alignment: .leading, spacing: 0) {
                Text(example.stem)
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(colors.text2)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                HStack(spacing: Tokens.Space.lmetaGap) {
                    Text(example.sourceLine)
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(colors.text3)
                    Text(example.questionType)
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(colors.text3)
                }
                .padding(.top, Tokens.Space.lsubTop)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .padding(.top, Tokens.Space.lmetaTop)
        }
        .buttonStyle(CPressDimStyle())
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
