import SwiftUI
import CangyuTokens

/// 小标签文字（全页稿 v2 `.lbl`）：11pt/700、字距 0.1em、text3。
/// 只用于卡片内的小标题（如「首次打开 · 先定考试范围」「生效范围」），不承载交互。
public struct CLabel: View {
    @Environment(\.colorScheme) private var colorScheme

    private let text: String

    public init(_ text: String) {
        self.text = text
    }

    public var body: some View {
        Text(text)
            .font(.system(size: Tokens.Font.n11, weight: .bold))
            // letter-spacing .1em（规范 5.0 `.lbl`）。
            .tracking(Tokens.Font.n11 * 0.1)
            .foregroundStyle(CangyuTheme.colors(for: colorScheme).text3)
            .accessibilityAddTraits(.isHeader)
    }
}
