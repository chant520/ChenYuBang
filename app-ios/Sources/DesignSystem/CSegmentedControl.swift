import SwiftUI
import CangyuTokens

/// 分段控件（规范 5.14，状态矩阵：默认 / 换段选中 / 含禁用项 / 三段及以上）。
///
/// 外框：padding 3pt、圆角 5pt、底 seg、描边 1px line；
/// 项：等分（flex:1）、padding 8pt 0、居中、11pt text2、圆角 4pt；
/// 选中项：底 segon、字 segonfg、700；禁用项：字 disfg、底不变、不可点。
/// 按下按规范 5.0 亮度 ×0.9；加载 / 错误态不适用。
public struct CSegmentedControl: View {
    @Environment(\.colorScheme) private var colorScheme

    private let items: [String]
    @Binding private var selection: Int
    private let disabledIndices: Set<Int>

    /// - Parameters:
    ///   - items: 分段文案，项数随页面（几何不变）。
    ///   - selection: 选中项下标绑定。
    ///   - disabledIndices: 禁用项下标集合。
    public init(items: [String], selection: Binding<Int>, disabledIndices: Set<Int> = []) {
        self.items = items
        self._selection = selection
        self.disabledIndices = disabledIndices
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        HStack(spacing: 0) {
            ForEach(items.indices, id: \.self) { idx in
                segment(idx, colors: colors)
            }
        }
        .padding(Tokens.Space.segPad)
        .background(RoundedRectangle(cornerRadius: Tokens.Radius.r5).fill(colors.seg))
        .overlay(
            RoundedRectangle(cornerRadius: Tokens.Radius.r5)
                .strokeBorder(colors.line, lineWidth: 1)
        )
    }

    private func segment(_ idx: Int, colors: TokenColors) -> some View {
        let isOn = idx == selection
        let isDisabled = disabledIndices.contains(idx)
        return Button(
            action: { selection = idx },
            label: {
                Text(items[idx])
                    .font(.system(size: Tokens.Font.n11, weight: isOn ? .bold : .regular))
                    .foregroundStyle(itemForeground(isOn: isOn, isDisabled: isDisabled, colors: colors))
                    // 不设 lineLimit：AX3 字号下允许换行增高、不裁切（开发规范 §9.3）。
                    .padding(.vertical, Tokens.Space.segY)
                    .frame(maxWidth: .infinity)
                    .background(
                        RoundedRectangle(cornerRadius: Tokens.Radius.r4)
                            .fill(isOn ? colors.segon : Color.clear)
                    )
            }
        )
        .buttonStyle(CPressDimStyle())
        .disabled(isDisabled)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    private func itemForeground(isOn: Bool, isDisabled: Bool, colors: TokenColors) -> Color {
        if isDisabled {
            return colors.disfg
        }
        return isOn ? colors.segonfg : colors.text2
    }
}
