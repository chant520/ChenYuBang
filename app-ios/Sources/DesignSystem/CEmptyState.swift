import SwiftUI
import CangyuTokens

/// 空态（D 稿 44 屏同一结构）：可选插画位 + 标题 15pt text2 + 说明 12pt text3 + 可选幽灵动作。
/// 文案由页面按信息架构 §5.4 传入，构件不自造文案；插画位只在该页 §5.4 行写明「插画位」时传（现为 P08 一处）。
public struct CEmptyState: View {
    @Environment(\.colorScheme) private var colorScheme

    private let title: String
    private let description: String
    private let icon: CIcons.Kind?
    private let actionTitle: String?
    private let onAction: (() -> Void)?

    public init(
        title: String,
        description: String,
        icon: CIcons.Kind? = nil,
        actionTitle: String? = nil,
        onAction: (() -> Void)? = nil
    ) {
        self.title = title
        self.description = description
        self.icon = icon
        self.actionTitle = actionTitle
        self.onAction = onAction
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(spacing: Tokens.Space.ghostY) {
            if let icon {
                // `.eic`：直径 64、底 `--surface2`、描边 1px `--line2`、内 26pt 图标描边 1.5 取 `--text3`。
                ZStack {
                    Circle()
                        .fill(colors.surface2)
                        .overlay(Circle().strokeBorder(colors.line2, lineWidth: 1))
                    CIcon(icon, size: Tokens.Icon.n26, strokeWidth: 1.5, color: colors.text3)
                }
                .frame(width: 64, height: 64)
                // 稿面 `.eic` 下距 16px，VStack 已给 10pt，补差值。
                .padding(.bottom, 6)
            }
            Text(title)
                .font(.system(size: Tokens.Font.n15))
                .foregroundStyle(colors.text2)
                .multilineTextAlignment(.center)
            Text(description)
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text3)
                .multilineTextAlignment(.center)
            if let actionTitle, let onAction {
                CButton(text: actionTitle, variant: .ghost, action: onAction)
                    .padding(.top, Tokens.Space.ghostY)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Tokens.Space.pageX)
    }
}
