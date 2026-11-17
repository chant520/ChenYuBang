import SwiftUI
import CangyuTokens

/// 提示条类别（规范 5.4 / 5.5）。命名与 Android 侧对齐。
public enum CBarKind: String, CaseIterable, Sendable {
    /// 信息提示条（中性，v1.3 收编）：tintbg / tintline，标题与图标 tintfg。
    case info
    /// 警示条（可恢复异常）：warnbg / warnline，标题与图标 warnfg。
    case warn
    /// 错误条（硬错误）：errbg / errline，标题与图标 errfg。
    case error
}

/// 提示条（规范 5.4 / 5.5）：r6、padding 12px 14px、gap 10px；
/// 图标 18pt stroke 1.8；标题 13px/700/1.55；副文案 11.5px/1.65 text2；
/// 条内操作链接 12px/700 + 下划线、色随标题、靠右（v1.3 收编）。
/// 状态提示非交互控件，激活 / 禁用 / 加载 / 错误态不适用（操作链接按下按 5.0 反馈）。
public struct CBar: View {
    @Environment(\.colorScheme) private var colorScheme

    private let kind: CBarKind
    private let title: String
    private let subtitle: String?
    private let actionTitle: String?
    private let onAction: (() -> Void)?

    /// - Parameters:
    ///   - kind: 信息 / 警示 / 错误。
    ///   - title: 标题（单行场景可只给标题）。
    ///   - subtitle: 可选副文案。
    ///   - actionTitle: 可选操作链接文案（如「重试」）。
    ///   - onAction: 操作链接回调；与 actionTitle 同时给出才渲染链接。
    public init(
        kind: CBarKind,
        title: String,
        subtitle: String? = nil,
        actionTitle: String? = nil,
        onAction: (() -> Void)? = nil
    ) {
        self.kind = kind
        self.title = title
        self.subtitle = subtitle
        self.actionTitle = actionTitle
        self.onAction = onAction
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        HStack(alignment: .top, spacing: Tokens.Space.barGap) {
            // 图标 18pt stroke 1.8，上距 2px（试色稿 v0.10 `.warnbar svg` / `.errbar svg`）。
            CIcon(iconKind, size: Tokens.Icon.n18, strokeWidth: 1.8, color: tint(colors))
                .padding(.top, 2)
            // 标题-副文案间距 4px（试色稿 v0.10 `.ws` / `.es` margin-top）。
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: Tokens.Font.n13, weight: .bold))
                    .cLineHeight(1.55, size: Tokens.Font.n13)
                    .foregroundStyle(tint(colors))
                if let subtitle {
                    Text(subtitle)
                        .font(.system(size: Tokens.Font.n115))
                        .cLineHeight(1.65, size: Tokens.Font.n115)
                        .foregroundStyle(colors.text2)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            if let actionTitle, let onAction {
                Spacer(minLength: 0)
                actionLink(actionTitle, tint: tint(colors), onAction: onAction)
            }
        }
        .padding(.vertical, Tokens.Space.barY)
        .padding(.horizontal, Tokens.Space.barX)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Tokens.Radius.r6).fill(background(colors)))
        .overlay(
            RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                .strokeBorder(border(colors), lineWidth: 1)
        )
    }

    /// 操作链接：700 + 下划线、色随条标题、margin-left:auto（规范 5.4，字号 12px 取构件板 `.bar .act`）。
    private func actionLink(
        _ actionTitle: String,
        tint: Color,
        onAction: @escaping () -> Void
    ) -> some View {
        Button(action: onAction, label: {
            Text(actionTitle)
                .font(.system(size: Tokens.Font.n12, weight: .bold))
                .underline(true)
                .foregroundStyle(tint)
                .fixedSize()
        })
        .buttonStyle(CPressDimStyle())
        .layoutPriority(1)
    }

    private var iconKind: CIcons.Kind {
        switch kind {
        case .info:
            return .info
        case .warn:
            return .warn
        case .error:
            return .err
        }
    }

    private func tint(_ colors: TokenColors) -> Color {
        switch kind {
        case .info:
            return colors.tintfg
        case .warn:
            return colors.warnfg
        case .error:
            return colors.errfg
        }
    }

    private func background(_ colors: TokenColors) -> Color {
        switch kind {
        case .info:
            return colors.tintbg
        case .warn:
            return colors.warnbg
        case .error:
            return colors.errbg
        }
    }

    private func border(_ colors: TokenColors) -> Color {
        switch kind {
        case .info:
            return colors.tintline
        case .warn:
            return colors.warnline
        case .error:
            return colors.errline
        }
    }
}

/// 行内警示（规范 5.4）：12px/600 warnfg、gap 6px、图标 13pt stroke 1.9。
public struct CInlineWarning: View {
    private let text: String

    public init(text: String) {
        self.text = text
    }

    public var body: some View {
        CInlineNotice(text: text, isError: false)
    }
}

/// 行内错误（规范 5.5）：12px/600 errfg、gap 6px、图标 13pt stroke 1.9。
public struct CInlineError: View {
    private let text: String

    public init(text: String) {
        self.text = text
    }

    public var body: some View {
        CInlineNotice(text: text, isError: true)
    }
}

/// 行内提示共用实现：图标与文字同色（规范 §6），不另取第二种强调色。
private struct CInlineNotice: View {
    @Environment(\.colorScheme) private var colorScheme

    let text: String
    let isError: Bool

    var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        let tint = isError ? colors.errfg : colors.warnfg
        // 图标-文字 gap 6px、图标 13pt stroke 1.9（规范 5.4 / 5.5 / §6）。
        HStack(spacing: 6) {
            CIcon(isError ? .err : .warn, size: Tokens.Icon.n13, strokeWidth: 1.9, color: tint)
            Text(text)
                .font(.system(size: Tokens.Font.n12, weight: .semibold))
                .foregroundStyle(tint)
        }
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityElement(children: .combine)
    }
}
