import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// 空态（D 稿 44 屏同一结构）：标题 15pt text2、说明 12pt text3、可选幽灵动作。
/// 文案由页面按信息架构 §5.4 传入，构件不自造文案。
public struct CEmptyState: View {
    @Environment(\.colorScheme) private var colorScheme

    private let title: String
    private let description: String
    private let actionTitle: String?
    private let onAction: (() -> Void)?

    public init(
        title: String,
        description: String,
        actionTitle: String? = nil,
        onAction: (() -> Void)? = nil
    ) {
        self.title = title
        self.description = description
        self.actionTitle = actionTitle
        self.onAction = onAction
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(spacing: Tokens.Space.ghostY) {
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
