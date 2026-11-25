import CangyuData
import CangyuDesignSystem
import CangyuTokens
import SwiftUI

/// P24「我的收藏」页面行为（快照可直接注入空操作）。
public struct P24FavoriteActions {
    public let onBack: () -> Void
    public let onOpenIdiom: (String) -> Void
    public let onGoLibrary: () -> Void
    public let onRetry: () -> Void

    public init(
        onBack: @escaping () -> Void,
        onOpenIdiom: @escaping (String) -> Void,
        onGoLibrary: @escaping () -> Void,
        onRetry: @escaping () -> Void
    ) {
        self.onBack = onBack
        self.onOpenIdiom = onOpenIdiom
        self.onGoLibrary = onGoLibrary
        self.onRetry = onRetry
    }
}

/// P24「我的收藏」主体：复用 P03 的 CListRow（带三态），点行进 P05；空态三件套 + 去成语库。
/// 纯展示，状态与行为由外部注入，供快照直接渲染。
public struct P24FavoriteContent: View {
    private let state: P24FavoriteState
    private let actions: P24FavoriteActions
    private let colors: TokenColors

    public init(state: P24FavoriteState, actions: P24FavoriteActions, colors: TokenColors) {
        self.state = state
        self.actions = actions
        self.colors = colors
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                header
                bodyContent
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.contentTop)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
    }

    private var header: some View {
        HStack(spacing: Tokens.Space.lmetaGap) {
            Button(action: actions.onBack) {
                CIcon(.back, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                    .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                    .contentShape(Rectangle())
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel("返回")
            Text("我的收藏")
                .font(.system(size: Tokens.Font.n20, weight: .semibold))
                .foregroundStyle(colors.head)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private var bodyContent: some View {
        if state.failed {
            CBar(
                kind: .warn,
                title: "收藏列表读取失败，请重试",
                subtitle: "本机收藏数据未改动。",
                actionTitle: "重试",
                onAction: actions.onRetry
            )
        } else if state.loading {
            HStack {
                Spacer(minLength: 0)
                CSpinner()
                Spacer(minLength: 0)
            }
        } else if state.isEmpty {
            CEmptyState(
                title: "还没收藏成语",
                description: "在成语库里点右上角收藏，就能在这里集中看",
                actionTitle: "去成语库",
                onAction: actions.onGoLibrary
            )
            .padding(.vertical, Tokens.Space.block)
        } else {
            CList {
                ForEach(Array(state.items.enumerated()), id: \.element.id) { index, item in
                    let tag = statusTag(for: item.status)
                    CListRow(
                        name: item.word,
                        pinyin: item.pinyin,
                        action: { actions.onOpenIdiom(item.idiomId) },
                        showsDivider: index != state.items.count - 1,
                        meta: {
                            CTag(text: "考频 \(item.frequency)")
                            CTag(text: tag.text, style: tag.style)
                        }
                    )
                }
            }
        }
    }
}

/// P24 路由页：装配只读收藏 Model，返回/内容 revision 变化时重读。
public struct P24FavoriteScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: P24FavoriteModel

    private let onBack: () -> Void
    private let onOpenIdiom: (String) -> Void
    private let onGoBackToLibrary: () -> Void
    private let revision: UUID

    @MainActor
    public init(
        reader: FavoriteReading,
        onBack: @escaping () -> Void,
        onOpenIdiom: @escaping (String) -> Void,
        onGoBackToLibrary: @escaping () -> Void,
        revision: UUID = UUID()
    ) {
        _model = StateObject(wrappedValue: P24FavoriteModel(reader: reader))
        self.onBack = onBack
        self.onOpenIdiom = onOpenIdiom
        self.onGoBackToLibrary = onGoBackToLibrary
        self.revision = revision
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        P24FavoriteContent(
            state: model.state,
            actions: P24FavoriteActions(
                onBack: onBack,
                onOpenIdiom: onOpenIdiom,
                onGoLibrary: onGoBackToLibrary,
                onRetry: { model.load() }
            ),
            colors: colors
        )
        .task(id: revision) { model.load() }
    }
}
