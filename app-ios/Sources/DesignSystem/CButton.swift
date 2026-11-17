import SwiftUI
import CangyuTokens

/// 按钮变体（规范 5.1）。命名与 Android 侧 CButtonVariant 对齐。
public enum CButtonVariant: String, CaseIterable, Sendable {
    /// 主按钮：btnbg / btnfg / 1px btnline，r6，14px/700，padding 13px 0。
    case primary
    /// 危险按钮·填充：errfill / errfillfg / 1px errfill，r6，13.5px/700，padding 12px 0。
    case dangerFilled
    /// 危险按钮·描边：透明底 / errfg / 1px errline，r6，13.5px/700，padding 12px 0。
    case dangerOutline
    /// 幽灵按钮：透明底 / text2 / 1px line，r5，11.5px，padding 10px 0。
    case ghost
    /// 顶部按钮：btnbg / btnfg，r5，11.5px/700，padding 7px 11px，内含 14pt 图标。
    case top
}

/// 按钮（规范 5.1，状态矩阵：默认 / 按下 / 禁用 / 加载；错误态不适用）。
///
/// - 按下：整体亮度 ×0.9（规范 5.0，见 CPressDimStyle）。
/// - 禁用：disbg / disfg / 1px disline、13.5px/700（规范 5.0 统一口径）；
///   顶部按钮按规范 5.1「其余几何同默认」，只换底与字色。
/// - 加载：各自默认样式不变 + 14pt CSpinner + 文案，期间不接受点击（规范 5.0 / 5.11）。
/// - 命中区：≥44pt（开发规范 §9.3），视觉尺寸不变。
/// - 宽度：主 / 危险 / 幽灵通栏（CSS width:100% / flex:1，横向并排时等分）；顶部按钮按内容自适应。
public struct CButton: View {
    @Environment(\.colorScheme) private var colorScheme

    private let text: String
    private let variant: CButtonVariant
    private let enabled: Bool
    private let loading: Bool
    private let icon: CIcons.Kind
    private let action: () -> Void

    /// - Parameters:
    ///   - text: 按钮文案；加载态即进行中文案（如「正在保存…」）。
    ///   - variant: 变体，默认主按钮。
    ///   - enabled: 是否可用；false 走禁用统一口径。
    ///   - loading: 是否加载中；true 时沿用默认样式 + spinner，且不响应点击。
    ///   - icon: 顶部按钮图标（14pt stroke 1.7），默认 plus；加载态由 spinner 替换。
    ///   - action: 点击回调。
    public init(
        text: String,
        variant: CButtonVariant = .primary,
        enabled: Bool = true,
        loading: Bool = false,
        icon: CIcons.Kind = .plus,
        action: @escaping () -> Void
    ) {
        self.text = text
        self.variant = variant
        self.enabled = enabled
        self.loading = loading
        self.icon = icon
        self.action = action
    }

    public var body: some View {
        Button(
            action: { if !loading { action() } },
            label: { buttonLabel }
        )
        .buttonStyle(CPressDimStyle())
        .disabled(!enabled || loading)
        .frame(minHeight: Tokens.Space.hitMin)
        .contentShape(Rectangle())
    }

    private var buttonLabel: some View {
        let look = appearance
        return HStack(spacing: contentGap) {
            if loading {
                CSpinner()
            } else if variant == .top {
                // 顶部按钮内含 14pt SVG，stroke 1.7（规范 5.1 / §6）；图标由调用方指定。
                CIcon(icon, size: Tokens.Icon.n14, strokeWidth: 1.7, color: look.foreground)
            }
            Text(text)
                .font(.system(size: look.fontSize, weight: look.fontWeight))
                // 不设 lineLimit：AX3 字号下文案换行、不裁切（开发规范 §9.3）。
        }
        .foregroundStyle(look.foreground)
        .padding(.vertical, look.padY)
        .padding(.horizontal, look.padX)
        .frame(maxWidth: variant == .top ? nil : CGFloat.infinity)
        .background(RoundedRectangle(cornerRadius: look.radius).fill(look.background))
        .overlay(
            RoundedRectangle(cornerRadius: look.radius)
                .strokeBorder(look.border, lineWidth: 1)
        )
    }

    /// spinner-文案间距 9px（试色稿 v0.10 `.btn.loading`）；顶部按钮图标-文案间距 6px（`.topbtn`）。
    private var contentGap: CGFloat {
        if loading {
            return 9
        }
        if variant == .top {
            return 6
        }
        return 0
    }

    private var appearance: Look {
        enabled ? enabledLook : disabledLook
    }

    private var enabledLook: Look {
        let colors = CangyuTheme.colors(for: colorScheme)
        switch variant {
        case .primary:
            return Look(
                background: colors.btnbg,
                foreground: colors.btnfg,
                border: colors.btnline,
                radius: Tokens.Radius.r6,
                fontSize: Tokens.Font.n14,
                fontWeight: .bold,
                padY: Tokens.Space.btnY,
                padX: 0
            )
        case .dangerFilled:
            return Look(
                background: colors.errfill,
                foreground: colors.errfillfg,
                border: colors.errfill,
                radius: Tokens.Radius.r6,
                fontSize: Tokens.Font.n135,
                fontWeight: .bold,
                padY: Tokens.Space.dangerY,
                padX: 0
            )
        case .dangerOutline:
            return Look(
                background: Color.clear,
                foreground: colors.errfg,
                border: colors.errline,
                radius: Tokens.Radius.r6,
                fontSize: Tokens.Font.n135,
                fontWeight: .bold,
                padY: Tokens.Space.dangerY,
                padX: 0
            )
        case .ghost:
            return Look(
                background: Color.clear,
                foreground: colors.text2,
                border: colors.line,
                radius: Tokens.Radius.r5,
                fontSize: Tokens.Font.n115,
                fontWeight: .regular,
                padY: Tokens.Space.ghostY,
                padX: 0
            )
        case .top:
            // 顶部按钮无描边（规范 5.1 默认态只列底 / 字 / 圆角 / 内边距 / 字号）。
            return Look(
                background: colors.btnbg,
                foreground: colors.btnfg,
                border: Color.clear,
                radius: Tokens.Radius.r5,
                fontSize: Tokens.Font.n115,
                fontWeight: .bold,
                padY: Tokens.Space.topBtnY,
                padX: Tokens.Space.topBtnX
            )
        }
    }

    private var disabledLook: Look {
        let colors = CangyuTheme.colors(for: colorScheme)
        if variant == .top {
            // 规范 5.1 顶部按钮禁用：底 disbg、字 disfg，其余几何同默认。
            return Look(
                background: colors.disbg,
                foreground: colors.disfg,
                border: Color.clear,
                radius: Tokens.Radius.r5,
                fontSize: Tokens.Font.n115,
                fontWeight: .bold,
                padY: Tokens.Space.topBtnY,
                padX: Tokens.Space.topBtnX
            )
        }
        // 规范 5.0 禁用统一口径（`.disbtn`）：disbg / disfg / 1px disline、r6、13.5px/700、padding 12px 0。
        return Look(
            background: colors.disbg,
            foreground: colors.disfg,
            border: colors.disline,
            radius: Tokens.Radius.r6,
            fontSize: Tokens.Font.n135,
            fontWeight: .bold,
            padY: Tokens.Space.dangerY,
            padX: 0
        )
    }

    /// 一个状态下的全部样式取值（色 / 圆角 / 字号 / 字重 / 内边距）。
    private struct Look {
        let background: Color
        let foreground: Color
        let border: Color
        let radius: CGFloat
        let fontSize: CGFloat
        let fontWeight: Font.Weight
        let padY: CGFloat
        let padX: CGFloat
    }
}
