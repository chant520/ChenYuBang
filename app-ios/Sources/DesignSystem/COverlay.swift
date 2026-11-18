import SwiftUI
import CangyuTokens

public enum COverlayStyle {
    case sheet, dialog
}

/// iOS 15 自绘底 sheet/居中 dialog；蒙层和取消只调用 onDismiss。
public struct COverlay<Content: View>: View {
    @Environment(\.colorScheme) private var colorScheme
    private let style: COverlayStyle
    private let title: String
    private let onDismiss: () -> Void
    private let content: Content

    public init(
        style: COverlayStyle,
        title: String,
        onDismiss: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.style = style
        self.title = title
        self.onDismiss = onDismiss
        self.content = content()
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        ZStack(alignment: style == .sheet ? .bottom : .center) {
            Button(action: onDismiss) {
                colors.modal
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .ignoresSafeArea()
            .accessibilityLabel("关闭弹层")
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                Text(title)
                    .font(.system(size: Tokens.Font.n15, weight: .semibold))
                    .foregroundStyle(colors.text)
                    .accessibilityAddTraits(.isHeader)
                content
                CButton(text: "取消", variant: .ghost, action: onDismiss)
            }
            .padding(Tokens.Space.pageX)
            .frame(maxWidth: style == .dialog ? Tokens.Space.modalWidth : .infinity)
            .background(
                RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                    .fill(colors.surface)
                    .ignoresSafeArea(edges: style == .sheet ? .bottom : [])
            )
            .accessibilityElement(children: .contain)
            .accessibilityAction(.escape, onDismiss)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
