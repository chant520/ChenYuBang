import SwiftUI
import CangyuTokens

public enum CTabBarItem: String, CaseIterable, Identifiable {
    case idiom, example, mistake, profile

    public var id: String { rawValue }
    public var label: String {
        switch self {
        case .idiom: return "成语"
        case .example: return "例题"
        case .mistake: return "错题"
        case .profile: return "我的"
        }
    }
}

/// 四标签 Chrome；重复点击仍发回调，由 Router 消费回根/置顶事件。
public struct CTabBar: View {
    @Environment(\.colorScheme) private var colorScheme
    private let selected: CTabBarItem
    private let onSelect: (CTabBarItem) -> Void

    public init(selected: CTabBarItem, onSelect: @escaping (CTabBarItem) -> Void) {
        self.selected = selected
        self.onSelect = onSelect
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        HStack(spacing: 0) {
            ForEach(CTabBarItem.allCases) { item in
                Button {
                    onSelect(item)
                } label: {
                    VStack(spacing: Tokens.Space.tabIconGap) {
                        TabIconShape(item: item)
                            // 内联 SVG 描边，24 viewBox；数值是矢量几何而非布局。
                            .stroke(style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                            .frame(width: Tokens.Icon.n24, height: Tokens.Icon.n24)
                            .accessibilityHidden(true)
                        Text(item.label)
                            .font(.system(size: Tokens.Font.n11, weight: item == selected ? .semibold : .regular))
                    }
                    .foregroundStyle(item == selected ? colors.accent : colors.text3)
                    .padding(.top, Tokens.Space.tabTop)
                    .padding(.bottom, Tokens.Space.tabBottom)
                    .frame(maxWidth: .infinity, minHeight: Tokens.Space.hitMin)
                    .contentShape(Rectangle())
                    .overlay(alignment: .top) {
                        Rectangle()
                            .fill(item == selected ? colors.accent : colors.tabbg)
                            .frame(height: Tokens.Space.tabIndicator)
                    }
                }
                .buttonStyle(CPressDimStyle())
                .accessibilityAddTraits(item == selected ? .isSelected : [])
            }
        }
        .background(colors.tabbg.ignoresSafeArea(edges: .bottom))
    }
}

/// 与 CIcons 相同的 Shape/Path 实现，禁用系统图标和图标字体。
private struct TabIconShape: Shape {
    let item: CTabBarItem

    func path(in rect: CGRect) -> Path {
        var path = Path()
        switch item {
        case .idiom:
            path.move(to: CGPoint(x: 12, y: 5))
            path.addQuadCurve(to: CGPoint(x: 3, y: 4), control: CGPoint(x: 7, y: 2))
            path.addLine(to: CGPoint(x: 3, y: 19))
            path.addQuadCurve(to: CGPoint(x: 12, y: 20), control: CGPoint(x: 8, y: 17))
            path.addQuadCurve(to: CGPoint(x: 21, y: 19), control: CGPoint(x: 16, y: 17))
            path.addLine(to: CGPoint(x: 21, y: 4))
            path.addQuadCurve(to: CGPoint(x: 12, y: 5), control: CGPoint(x: 17, y: 2))
            path.addLine(to: CGPoint(x: 12, y: 20))
        case .example:
            path.addRoundedRect(in: CGRect(x: 5, y: 3, width: 14, height: 18), cornerSize: CGSize(width: 1, height: 1))
            for baseline in [8, 12, 16] {
                path.move(to: CGPoint(x: 8, y: CGFloat(baseline)))
                path.addLine(to: CGPoint(x: 16, y: CGFloat(baseline)))
            }
        case .mistake:
            path.addRoundedRect(in: CGRect(x: 4, y: 3, width: 16, height: 18), cornerSize: CGSize(width: 1, height: 1))
            path.move(to: CGPoint(x: 8, y: 8))
            path.addLine(to: CGPoint(x: 16, y: 16))
            path.move(to: CGPoint(x: 16, y: 8))
            path.addLine(to: CGPoint(x: 8, y: 16))
        case .profile:
            path.addEllipse(in: CGRect(x: 8, y: 3, width: 8, height: 8))
            path.move(to: CGPoint(x: 4, y: 21))
            path.addCurve(to: CGPoint(x: 20, y: 21),
                          control1: CGPoint(x: 4, y: 10), control2: CGPoint(x: 20, y: 10))
        }
        let scale = min(rect.width, rect.height) / 24
        return path.applying(CGAffineTransform(scaleX: scale, y: scale))
    }
}
