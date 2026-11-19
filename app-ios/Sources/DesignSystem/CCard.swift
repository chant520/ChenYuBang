import SwiftUI
import CangyuTokens

/// 卡片（全页稿 v2 `.card` / `.card.soft`）：r6、1px line 描边、padding 16pt。
/// 常规卡底 surface，软卡底 surface2（如 P14「生效范围」说明块）。
public struct CCard<Content: View>: View {
    @Environment(\.colorScheme) private var colorScheme

    private let soft: Bool
    private let content: Content

    public init(soft: Bool = false, @ViewBuilder content: () -> Content) {
        self.soft = soft
        self.content = content()
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        return VStack(alignment: .leading, spacing: 0) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Tokens.Space.block)
        .background(
            RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                .fill(soft ? colors.surface2 : colors.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                .strokeBorder(colors.line, lineWidth: 1)
        )
    }
}
