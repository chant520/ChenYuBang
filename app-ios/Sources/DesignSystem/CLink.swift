import SwiftUI
import CangyuTokens

/// 文字链接（全页稿 v2 `.linkbtn`）：12pt、accent、下划线。
/// 用于「调整省份」等页内跳转；纵向内边距把命中区补到 ≥44pt，视觉字号不变（开发规范 §9.3）。
public struct CLink: View {
    @Environment(\.colorScheme) private var colorScheme

    private let text: String
    private let action: () -> Void

    public init(_ text: String, action: @escaping () -> Void) {
        self.text = text
        self.action = action
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        Button(action: action) {
            Text(text)
                .font(.system(size: Tokens.Font.n12))
                .underline()
                .foregroundStyle(colors.accent)
                .padding(.vertical, Tokens.Space.ghostY)
                .contentShape(Rectangle())
        }
        .buttonStyle(CPressDimStyle())
    }
}
