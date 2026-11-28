import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P05 详情正文（词义 → 易错点 → 近义与反义 → 相关题目 → 录入题占位 → 单成语复习入口）。
/// 稿面 A P05 v2.4：释义+出处合并为「词义」卡；标签区上方与页尾各有一个「复习这个成语」入口。
struct P05DetailBody: View {
    private let detail: IdiomDetailView
    private let colors: TokenColors
    private let pendingReviewCount: Int
    private let examples: [IdiomExampleView]
    private let onOpenMistakes: () -> Void
    private let onOpenExample: (String) -> Void
    private let onReviewIdiom: (String) -> Void

    init(
        detail: IdiomDetailView,
        colors: TokenColors,
        pendingReviewCount: Int,
        examples: [IdiomExampleView],
        onOpenMistakes: @escaping () -> Void,
        onOpenExample: @escaping (String) -> Void,
        onReviewIdiom: @escaping (String) -> Void
    ) {
        self.detail = detail
        self.colors = colors
        self.pendingReviewCount = pendingReviewCount
        self.examples = examples
        self.onOpenMistakes = onOpenMistakes
        self.onOpenExample = onOpenExample
        self.onReviewIdiom = onReviewIdiom
    }

    var body: some View {
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
        // 标题/拼音/标签区（稿面 A P05 v2.4）：标签下 12px 处放上方复习入口，与页尾按钮同回调。
        VStack(alignment: .leading, spacing: Tokens.Space.dangerY) {
            HStack {
                CTag(text: detail.color)
                Spacer(minLength: 0)
            }
            reviewButton
        }

        // 词义卡（稿面 A P05 v2.4 A17）：释义 + 出处合并为一卡，近义与反义仍独立成卡。
        CCard {
            CLabel("词义")
            Text(detail.sense)
                .font(.system(size: Tokens.Font.n13, weight: .semibold))
                .foregroundStyle(colors.text)
                .fixedSize(horizontal: false, vertical: true)
            Text(detail.origin)
                .font(.system(size: Tokens.Font.n13))
                .foregroundStyle(colors.text2)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, Tokens.Space.lsubTop)
        }

        CCard {
            CLabel("易错点")
            if detail.pitfalls.isEmpty {
                bodyText(IdiomDetail.placeholder)
            } else {
                ForEach(detail.pitfalls, id: \.self) { item in
                    bodyText(item)
                }
            }
        }

        CCard {
            CLabel("近义与反义")
            bodyText("近义：\(IdiomDetail.presentList(detail.synonyms))")
            bodyText("反义：\(IdiomDetail.presentList(detail.antonyms))")
        }

        relatedExamplesCard()
        myExamplesPlaceholder()

        // D1 单成语复习入口（决策 H）：页尾按钮与顶部入口共用回调，按待复习数判定可点性。
        reviewButton
        if pendingReviewCount == 0 {
            Text("这个成语下还没有待复习的错题")
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text3)
        }
    }

    /// 稿面 v2.4 双入口：标签区上方与页尾复用同一按钮构造与复习回调。
    private var reviewButton: some View {
        CButton(
            text: "复习这个成语",
            variant: .primary,
            enabled: pendingReviewCount > 0,
            action: { onReviewIdiom(detail.word) }
        )
    }

    private func bodyText(_ text: String) -> some View {
        Text(text)
            .font(.system(size: Tokens.Font.n13))
            .foregroundStyle(colors.text2)
            .padding(.top, Tokens.Space.lsubTop)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// A4「相关题目」双来源（信息架构 v1.4 P05 ⑧）：系统题库按 example_idiom 列出关联真题（1.6），
    /// 每条题干摘要 + 年份 · 地区 · 考试类型 + 题型，点击进 P07；暂无时保留文案不隐藏。
    /// 阶段2：我录入的题接 user_example，暂无时保留「还没在这个成语下录入题」不隐藏。
    private func relatedExamplesCard() -> some View {
        CCard {
            CLabel("相关题目")
            sectionCaption("系统题库")
            if examples.isEmpty {
                bodyText("暂无真题，后续补充")
            } else {
                ForEach(examples) { example in
                    relatedExampleRow(example)
                }
            }
            sectionCaption("我录入的题（标错才进错题本）")
            bodyText("还没在这个成语下录入题")
        }
    }

    private func relatedExampleRow(_ example: IdiomExampleView) -> some View {
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

    private func sectionCaption(_ text: String) -> some View {
        Text(text)
            .font(.system(size: Tokens.Font.n12))
            .foregroundStyle(colors.text3)
            .padding(.top, Tokens.Space.lmetaTop)
    }

    /// A5「我的录入题 N」数字入口占位。阶段2：N 接 user_example 计数，N>0 可点进 P09。
    private func myExamplesPlaceholder() -> some View {
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
