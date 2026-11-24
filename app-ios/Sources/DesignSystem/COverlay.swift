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
    private let description: String?
    private let onDismiss: () -> Void
    private let cancelEnabled: Bool
    private let content: Content

    public init(
        style: COverlayStyle,
        title: String,
        description: String? = nil,
        onDismiss: @escaping () -> Void,
        cancelEnabled: Bool = true,
        @ViewBuilder content: () -> Content
    ) {
        self.style = style
        self.title = title
        self.description = description
        self.onDismiss = onDismiss
        self.cancelEnabled = cancelEnabled
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
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.system(size: Tokens.Font.n15, weight: .bold))
                    .foregroundStyle(colors.head)
                    .accessibilityAddTraits(.isHeader)
                if let description {
                    Text(description)
                        .font(.system(size: Tokens.Font.n12))
                        .cLineHeight(1.7, size: Tokens.Font.n12)
                        .foregroundStyle(colors.text2)
                        .padding(.top, Tokens.Space.lsubTop)
                }
                content
                    .padding(.top, Tokens.Space.block)
                if cancelEnabled {
                    CButton(text: "取消", variant: .ghost, action: onDismiss)
                        .padding(.top, Tokens.Space.block)
                }
            }
            .padding(.horizontal, style == .dialog ? Tokens.Space.block : Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.contentTop)
            .frame(maxWidth: style == .dialog ? Tokens.Space.modalWidth : .infinity)
            .background(
                Phase4OverlaySurface(style: style)
                    .fill(colors.surface)
                    .ignoresSafeArea(edges: style == .sheet ? .bottom : [])
            )
            .overlay(
                Phase4OverlaySurface(style: style)
                    .strokeBorder(colors.line, lineWidth: 1)
                    .ignoresSafeArea(edges: style == .sheet ? .bottom : [])
            )
            .accessibilityElement(children: .contain)
            .accessibilityAction(.escape, onDismiss)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct Phase4OverlaySurface: Shape {
    let style: COverlayStyle

    func path(in rect: CGRect) -> Path {
        guard style == .sheet else {
            return Path(roundedRect: rect, cornerRadius: Tokens.Radius.r6)
        }
        let radius = Tokens.Radius.r6
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.maxY))
        path.addLine(to: CGPoint(x: 0, y: radius))
        path.addQuadCurve(to: CGPoint(x: radius, y: 0), control: CGPoint(x: 0, y: 0))
        path.addLine(to: CGPoint(x: rect.maxX - radius, y: 0))
        path.addQuadCurve(to: CGPoint(x: rect.maxX, y: radius), control: CGPoint(x: rect.maxX, y: 0))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}
