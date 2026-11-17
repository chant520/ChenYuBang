import SwiftUI
import CangyuTokens

/// 徽标类别（规范 5.6）。命名与 Android 侧对齐。
public enum CBadgeKind: String, CaseIterable, Sendable {
    /// 警示徽标：warnbg / warnfg / 1px warnline（可恢复异常）。
    case warn
    /// 错误徽标：errbg / errfg / 1px errline（硬错误 / 破坏性动作，如 P22「覆盖」）。
    case error
    /// 信息徽标（v1.3 收编）：tintbg / tintfg / 1px tintline（中性状态标识，如「合并」）。
    case info
    /// 中性徽标（v1.3 收编）：disbg / disfg / 1px disline（已停用等弱化标识）。
    case neutral
}

/// 徽标（规范 5.6）：r3、padding 2px 6px、字号 10px。
/// 静态状态标识，激活 / 禁用 / 加载 / 错误态不适用。
/// 纯信息一律用 .info 或 .neutral，不许用橙（开发规范 §7）。
public struct CBadge: View {
    @Environment(\.colorScheme) private var colorScheme

    private let text: String
    private let kind: CBadgeKind

    public init(text: String, kind: CBadgeKind = .warn) {
        self.text = text
        self.kind = kind
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        Text(text)
            .font(.system(size: Tokens.Font.n10))
            .foregroundStyle(foreground(colors))
            .padding(.vertical, Tokens.Space.badgeY)
            .padding(.horizontal, Tokens.Space.badgeX)
            .background(RoundedRectangle(cornerRadius: Tokens.Radius.r3).fill(background(colors)))
            .overlay(
                RoundedRectangle(cornerRadius: Tokens.Radius.r3)
                    .strokeBorder(border(colors), lineWidth: 1)
            )
            .accessibilityElement(children: .combine)
    }

    private func background(_ colors: TokenColors) -> Color {
        switch kind {
        case .warn:
            return colors.warnbg
        case .error:
            return colors.errbg
        case .info:
            return colors.tintbg
        case .neutral:
            return colors.disbg
        }
    }

    private func foreground(_ colors: TokenColors) -> Color {
        switch kind {
        case .warn:
            return colors.warnfg
        case .error:
            return colors.errfg
        case .info:
            return colors.tintfg
        case .neutral:
            return colors.disfg
        }
    }

    private func border(_ colors: TokenColors) -> Color {
        switch kind {
        case .warn:
            return colors.warnline
        case .error:
            return colors.errline
        case .info:
            return colors.tintline
        case .neutral:
            return colors.disline
        }
    }
}
