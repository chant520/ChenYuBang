import SwiftUI
import CangyuTokens

/// 标签样式（规范 5.2）。命名与 Android 侧对齐。
public enum CTagStyle: String, CaseIterable, Sendable {
    /// 实心标签：tintbg / tintfg / 1px tintline。
    case solid
    /// 幽灵标签：透明底 / text3 / 1px dashed line。
    case ghost
    /// 选中标签（v1.3 收编）：accent / segonfg / 1px accent。
    case selected
    /// 禁用标签：disbg / disfg / 1px disline。
    case disabled
}

/// 标签（规范 5.2）：r4、padding 4px 9px、字号 11px/1.4。
/// 静态信息展示，激活 / 加载 / 错误态不适用；禁用由 style 表达。
public struct CTag: View {
    @Environment(\.colorScheme) private var colorScheme

    private let text: String
    private let style: CTagStyle

    public init(text: String, style: CTagStyle = .solid) {
        self.text = text
        self.style = style
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        Text(text)
            .font(.system(size: Tokens.Font.n11))
            .cLineHeight(1.4, size: Tokens.Font.n11)
            .foregroundStyle(foreground(colors))
            .padding(.vertical, Tokens.Space.tagY)
            .padding(.horizontal, Tokens.Space.tagX)
            .background(RoundedRectangle(cornerRadius: Tokens.Radius.r4).fill(background(colors)))
            .overlay(
                RoundedRectangle(cornerRadius: Tokens.Radius.r4)
                    .strokeBorder(
                        border(colors),
                        style: StrokeStyle(lineWidth: 1, dash: style == .ghost ? [3, 3] : [])
                    )
            )
            .accessibilityElement(children: .combine)
    }

    private func background(_ colors: TokenColors) -> Color {
        switch style {
        case .solid:
            return colors.tintbg
        case .ghost:
            return Color.clear
        case .selected:
            return colors.accent
        case .disabled:
            return colors.disbg
        }
    }

    private func foreground(_ colors: TokenColors) -> Color {
        switch style {
        case .solid:
            return colors.tintfg
        case .ghost:
            return colors.text3
        case .selected:
            return colors.segonfg
        case .disabled:
            return colors.disfg
        }
    }

    private func border(_ colors: TokenColors) -> Color {
        switch style {
        case .solid:
            return colors.tintline
        case .ghost:
            return colors.line
        case .selected:
            return colors.accent
        case .disabled:
            return colors.disline
        }
    }
}
