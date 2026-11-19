import SwiftUI
import CangyuTokens

/// 右侧字母索引条（全页稿 v2 P03）：10pt、行高 20pt、宽 24pt；
/// 选中的字母为 accent/700 且底 tintbg，未选为 text3。
///
/// 几何无对应令牌：10pt 字号 × 1.45 行高 ≈ 15pt，触摸端抬高到 20pt（与 Android CLetterIndex 一致）。
public struct CLetterIndex: View {
    @Environment(\.colorScheme) private var colorScheme

    private static let columnWidth: CGFloat = 24
    private static let rowHeight: CGFloat = 20

    private let letters: [String]
    private let selected: String?
    private let onSelect: (String) -> Void

    public init(letters: [String], selected: String?, onSelect: @escaping (String) -> Void) {
        self.letters = letters
        self.selected = selected
        self.onSelect = onSelect
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        return VStack(spacing: 0) {
            ForEach(letters, id: \.self) { letter in
                Button(action: { onSelect(letter) }) {
                    Text(letter)
                        .font(.system(size: Tokens.Font.n10, weight: letter == selected ? .bold : .regular))
                        .foregroundStyle(letter == selected ? colors.accent : colors.text3)
                        .frame(width: Self.columnWidth, height: Self.rowHeight)
                        .background(
                            RoundedRectangle(cornerRadius: Tokens.Radius.r4)
                                .fill(letter == selected ? colors.tintbg : Color.clear)
                        )
                        .contentShape(Rectangle())
                }
                .buttonStyle(CPressDimStyle())
                .accessibilityLabel("首字母 \(letter)")
                .accessibilityAddTraits(letter == selected ? .isSelected : [])
            }
        }
    }
}
