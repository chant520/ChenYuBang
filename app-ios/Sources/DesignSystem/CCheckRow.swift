import SwiftUI
import CangyuTokens

/// 勾选行（规范 5.15，状态矩阵：勾选 / 未勾选 / 禁用 / 带警示）。
///
/// 行：gap 10pt、padding 12pt 0、13pt text、行底 1px line2 分隔（末行由容器置 showsDivider = false）；
/// 勾选框 18×18pt、圆角 4pt：选中 accent 底 + accent 描边 + 对勾 12pt stroke 2 chkfg；
/// 未选透明底 + line 描边；禁用 disbg 底 + disline 描边、无对勾，行文字 disfg。
/// 右侧可选挂行内警示（规范 5.4）或纯文字（规范 5.15 `.r`，11pt text3），
/// 警示优先。整行可点切换，按下按规范 5.0 亮度 ×0.9。
/// 加载 / 错误态不适用。
public struct CCheckRow: View {
    @Environment(\.colorScheme) private var colorScheme

    private let label: String
    @Binding private var isOn: Bool
    private let enabled: Bool
    private let warningText: String?
    private let trailingText: String?
    private let showsDivider: Bool

    /// - Parameters:
    ///   - label: 行文案（13pt）。
    ///   - isOn: 勾选绑定。
    ///   - enabled: 是否可用；false 时勾选框与文字走禁用口径且不可点。
    ///   - warningText: 可选行内警示文案，靠右渲染（优先于 trailingText）。
    ///   - trailingText: 可选右侧纯文字（11pt text3，规范 5.15 `.r`）。
    ///   - showsDivider: 是否画行底 1px line2 分隔，默认 true（末行由容器置 false）。
    public init(
        label: String,
        isOn: Binding<Bool>,
        enabled: Bool = true,
        warningText: String? = nil,
        trailingText: String? = nil,
        showsDivider: Bool = true
    ) {
        self.label = label
        self._isOn = isOn
        self.enabled = enabled
        self.warningText = warningText
        self.trailingText = trailingText
        self.showsDivider = showsDivider
    }

    public var body: some View {
        Button(
            action: { if enabled { isOn.toggle() } },
            label: { rowContent }
        )
        .buttonStyle(CPressDimStyle())
        .disabled(!enabled)
        .contentShape(Rectangle())
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    private var rowContent: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        // 行底分隔线计入行盒高度（CSS border-bottom 口径），故用 VStack 而非 overlay。
        return VStack(spacing: 0) {
            HStack(spacing: Tokens.Space.chkGap) {
                checkbox(colors)
                Text(label)
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(enabled ? colors.text : colors.disfg)
                    .fixedSize(horizontal: false, vertical: true)
                if let warningText {
                    Spacer(minLength: 0)
                    CInlineWarning(text: warningText)
                } else if let trailingText {
                    Spacer(minLength: 0)
                    Text(trailingText)
                        .font(.system(size: Tokens.Font.n11))
                        .foregroundStyle(colors.text3)
                }
            }
            .padding(.vertical, Tokens.Space.chkY)
            .frame(maxWidth: .infinity, alignment: .leading)
            if showsDivider {
                Rectangle()
                    .fill(colors.line2)
                    .frame(height: 1)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func checkbox(_ colors: TokenColors) -> some View {
        // 禁用态无对勾（规范 5.15 `.chk.dis`）。
        let checked = isOn && enabled
        return RoundedRectangle(cornerRadius: Tokens.Radius.r4)
            .fill(boxFill(checked: checked, colors: colors))
            .frame(width: Tokens.Space.chkSize, height: Tokens.Space.chkSize)
            .overlay(
                RoundedRectangle(cornerRadius: Tokens.Radius.r4)
                    .strokeBorder(boxBorder(checked: checked, colors: colors), lineWidth: 1)
            )
            .overlay {
                if checked {
                    // 对勾内联 SVG 12pt stroke 2 chkfg（规范 5.15 / §6）。
                    CIcon(.check, size: Tokens.Icon.n12, strokeWidth: 2, color: colors.chkfg)
                }
            }
    }

    private func boxFill(checked: Bool, colors: TokenColors) -> Color {
        if checked {
            return colors.accent
        }
        return enabled ? Color.clear : colors.disbg
    }

    private func boxBorder(checked: Bool, colors: TokenColors) -> Color {
        if checked {
            return colors.accent
        }
        return enabled ? colors.line : colors.disline
    }
}
