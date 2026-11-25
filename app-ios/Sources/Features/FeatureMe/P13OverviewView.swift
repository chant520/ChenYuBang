import CangyuDesignSystem
import CangyuTokens
import SwiftUI

/// P13「我的」E2 数据概览（4+1 纯数字）+ 设置分组首行「我的收藏 N」入口。
/// 数字格不可点、不跳页；全空显示 0。本 View 只渲染概览区与收藏入口行，
/// 其余设置行由调用方（RoutePlaceholderPage）拼装。
public struct P13OverviewView: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: P13OverviewModel
    private let onOpenFavorites: () -> Void
    private let revision: UUID

    @MainActor
    public init(
        reader: OverviewReading,
        onOpenFavorites: @escaping () -> Void,
        revision: UUID = UUID()
    ) {
        _model = StateObject(wrappedValue: P13OverviewModel(reader: reader))
        self.onOpenFavorites = onOpenFavorites
        self.revision = revision
    }

    public var body: some View {
        P13OverviewContent(stats: model.stats, colors: CangyuTheme.colors(for: colorScheme)) {
            onOpenFavorites()
        }
        .task(id: revision) { model.refresh() }
    }
}

/// E2 概览主体（供快照直接渲染，不依赖数据库）。
public struct P13OverviewContent: View {
    private let stats: P13OverviewStats
    private let colors: TokenColors
    private let onOpenFavorites: () -> Void

    public init(
        stats: P13OverviewStats,
        colors: TokenColors,
        onOpenFavorites: @escaping () -> Void = {}
    ) {
        self.stats = stats
        self.colors = colors
        self.onOpenFavorites = onOpenFavorites
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            overviewCard
            CList {
                CListRow(
                    name: "我的收藏",
                    pinyin: "",
                    action: onOpenFavorites,
                    meta: { EmptyView() },
                    trailing: { Text("\(stats.favorites)") }
                )
            }
        }
    }

    private var overviewCard: some View {
        CCard {
            CLabel("数据概览")
            HStack(alignment: .top) {
                numberCell("已学成语", stats.learned, hot: false)
                numberCell("收藏", stats.favorites, hot: false)
                numberCell("错题", stats.mistakes, hot: false)
                numberCell("待复习", stats.pending, hot: true)
            }
            .padding(.top, Tokens.Space.lmetaTop)
            numberCell("已掌握", stats.proficient, hot: false)
                .padding(.top, Tokens.Space.lmetaTop)
        }
    }

    /// 单个数字格：数字 20/700 + 标签 11，纯展示不可点；待复习标签取 hot 色。
    private func numberCell(_ label: String, _ value: Int, hot: Bool) -> some View {
        VStack(spacing: Tokens.Space.lsubTop) {
            Text("\(value)")
                .font(.system(size: Tokens.Font.n20, weight: .bold))
                .foregroundStyle(colors.text)
            Text(label)
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(hot ? colors.hot : colors.text3)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label) \(value)")
    }
}
