import SwiftUI
import CangyuTokens

/// 悬浮拍照按钮（规范 5.8，状态矩阵：默认 / 按下 / 禁用 / 带徽标组合）。
///
/// 52×52pt、圆角 6pt、底 fab；图标 camera 24pt stroke 1.6 fabicon；投影取 Tokens.Shadow（见 CFabShadow）。
/// 禁用：底 disbg、图标 disfg、无投影，几何同默认；按下按规范 5.0 亮度 ×0.9。
/// 带徽标：右侧 gap 10pt 挂警示徽标（CBadge(.warn)，构件板 `.fabcell` 展示口径）。
/// 定位（right 20pt / bottom 88pt，Tokens.Space.fabRight / fabBottom）由页面负责；
/// 仅 P02 / P06 / P08 / P13 四个主标签页出现。加载 / 错误态不适用。
public struct CFab: View {
    @Environment(\.colorScheme) private var colorScheme

    private let enabled: Bool
    private let badgeText: String?
    private let action: () -> Void

    /// - Parameters:
    ///   - enabled: 是否可用；false 走禁用口径（无投影）。
    ///   - badgeText: 可选徽标文案（如「待复习 3」），挂右侧。
    ///   - action: 点击回调。
    public init(enabled: Bool = true, badgeText: String? = nil, action: @escaping () -> Void) {
        self.enabled = enabled
        self.badgeText = badgeText
        self.action = action
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        // 徽标间距 10pt（规范 5.8「带徽标组合」gap:10px，构件板 `.fabcell`）。
        HStack(spacing: 10) {
            fabButton(colors)
            if let badgeText {
                CBadge(text: badgeText, kind: .warn)
            }
        }
    }

    private func fabButton(_ colors: TokenColors) -> some View {
        Button(
            action: action,
            label: {
                CIcon(
                    .camera,
                    size: Tokens.Icon.n24,
                    strokeWidth: 1.6,
                    color: enabled ? colors.fabicon : colors.disfg
                )
                .frame(width: Tokens.Space.fabSize, height: Tokens.Space.fabSize)
                .background(
                    RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                        .fill(enabled ? colors.fab : colors.disbg)
                )
            }
        )
        .buttonStyle(CPressDimStyle())
        .disabled(!enabled)
        .modifier(CFabShadow(layers: enabled ? shadowLayers : []))
        .contentShape(Rectangle())
    }

    private var shadowLayers: [ShadowLayer] {
        colorScheme == .dark ? Tokens.Shadow.fabDark : Tokens.Shadow.fabLight
    }
}

/// CSS box-shadow → SwiftUI 投影映射（规范 2.2 / 2.3 `--fabshadow`，令牌见 Tokens.Shadow）：
/// blur > 0 的层用 `.shadow(color:radius:blur/2, x:y:)`（SwiftUI radius 约等于 CSS blur 的一半）；
/// blur == 0 的 spread 层没有模糊，用外扩 spread/2 的同色描边（lineWidth: spread）叠画，
/// 等效 CSS box-shadow 向外扩展 spread（与 Android CFab 的 outset 画法对齐）。
private struct CFabShadow: ViewModifier {
    let layers: [ShadowLayer]

    func body(content: Content) -> some View {
        layers.reduce(AnyView(content)) { partial, layer in
            if layer.blur > 0 {
                return AnyView(
                    partial.shadow(
                        color: layer.color,
                        radius: layer.blur / 2,
                        x: layer.offsetX,
                        y: layer.offsetY
                    )
                )
            }
            if layer.spread > 0 {
                return AnyView(
                    partial.overlay(
                        RoundedRectangle(cornerRadius: Tokens.Radius.r6 + layer.spread / 2)
                            .stroke(layer.color, lineWidth: layer.spread)
                            .padding(-layer.spread / 2)
                    )
                )
            }
            return partial
        }
    }
}
