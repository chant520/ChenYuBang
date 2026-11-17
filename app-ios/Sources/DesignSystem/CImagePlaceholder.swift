import SwiftUI
import CangyuTokens

/// 图片占位（规范 5.7 / 5.10，状态矩阵：橙原图丢失 / 中性空态 / 骨架 / 长页截断）。
///
/// - neutral：phbg + 1px dashed phline，单行文字 11.5px text3（无异常空态，如「还没有照片」）。
/// - warn：warnbg + 1px dashed warnline，图标 26pt stroke 1.7 warnfg、标题 12.5px/700 warnfg、
///   副说明 11px text2，内容居中 gap 7px（试色稿 v0.10 `.imgph`）。
/// - skeleton：skel 块 r4 + skelhi 流光 1.5s ease-in-out 无限（规范 5.10）；
///   reduce motion 时降级为静态 skel 色块，不新增色值（开发规范 §9.3）。
/// - truncated：占位块下方 1px dashed cut 分隔 + 10px text3 标注（构件板 `.cut`）。
///
/// 高度默认页面口径 150pt（Tokens.Space.imgphH）；展示口径可传 96 / 64。
/// 静态占位，激活 / 禁用 / 加载 / 错误态不适用。
public struct CImagePlaceholder: View {
    /// 占位类别。
    public enum Kind: String, CaseIterable, Sendable {
        case neutral
        case warn
        case skeleton
        case truncated
    }

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let kind: Kind
    private let height: CGFloat
    private let title: String?
    private let subtitle: String?
    private let cutText: String?

    /// - Parameters:
    ///   - kind: 占位类别，默认中性空态。
    ///   - height: 占位块高度，默认 Tokens.Space.imgphH（页面口径 150pt）。
    ///   - title: 占位标题（骨架态不适用）。
    ///   - subtitle: 占位副说明（骨架态不适用）。
    ///   - cutText: 截断标注文案（规范示例「长页 · 已截断」），仅 truncated 态渲染。
    public init(
        kind: Kind = .neutral,
        height: CGFloat = Tokens.Space.imgphH,
        title: String? = nil,
        subtitle: String? = nil,
        cutText: String? = nil
    ) {
        self.kind = kind
        self.height = height
        self.title = title
        self.subtitle = subtitle
        self.cutText = cutText
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(alignment: .leading, spacing: 0) {
            block(colors)
            if kind == .truncated {
                cutMark(colors)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func block(_ colors: TokenColors) -> some View {
        Group {
            if kind == .skeleton {
                RoundedRectangle(cornerRadius: Tokens.Radius.r4)
                    .fill(colors.skel)
                    .overlay {
                        if !reduceMotion {
                            CShimmer(highlight: colors.skelhi)
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: Tokens.Radius.r4))
            } else {
                content(colors)
                    .frame(maxWidth: .infinity)
                    .frame(height: height)
                    .background(RoundedRectangle(cornerRadius: Tokens.Radius.r6).fill(fill(colors)))
                    .overlay(
                        RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                            .strokeBorder(
                                dashColor(colors),
                                style: StrokeStyle(lineWidth: 1, dash: [3, 3])
                            )
                    )
            }
        }
        .frame(height: height)
    }

    /// 内容居中、gap 7px（试色稿 v0.10 `.imgph`）。
    /// warn：图标 + 标题 12.5px/700 warnfg + 副说明 11px text2；
    /// neutral：单行 11.5px text3，副说明不适用（规范 5.7，与 Android 对齐）。
    private func content(_ colors: TokenColors) -> some View {
        VStack(spacing: 7) {
            if kind == .warn {
                // 图标 26pt stroke 1.7（规范 5.7 / §6）。
                CIcon(.warn, size: Tokens.Icon.n26, strokeWidth: 1.7, color: colors.warnfg)
                if let title {
                    Text(title)
                        .font(.system(size: Tokens.Font.n125, weight: .bold))
                        .foregroundStyle(colors.warnfg)
                        .multilineTextAlignment(.center)
                }
                if let subtitle {
                    Text(subtitle)
                        .font(.system(size: Tokens.Font.n11))
                        .foregroundStyle(colors.text2)
                        .multilineTextAlignment(.center)
                }
            } else if let title {
                Text(title)
                    .font(.system(size: Tokens.Font.n115))
                    .foregroundStyle(colors.text3)
                    .multilineTextAlignment(.center)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    /// 截断标线（构件板 `.cut`）：上距 6px、1px dashed cut、标注上距 5px、10px text3、字距 .06em。
    private func cutMark(_ colors: TokenColors) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            CHorizontalRule()
                .stroke(colors.cut, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                .frame(height: 1)
            if let cutText {
                Text(cutText)
                    .font(.system(size: Tokens.Font.n10))
                    .tracking(Tokens.Font.n10 * 0.06)
                    .foregroundStyle(colors.text3)
                    .padding(.top, 5)
            }
        }
        .padding(.top, 6)
    }

    private func fill(_ colors: TokenColors) -> Color {
        kind == .warn ? colors.warnbg : colors.phbg
    }

    private func dashColor(_ colors: TokenColors) -> Color {
        kind == .warn ? colors.warnline : colors.phline
    }
}

/// 骨架流光（规范 5.10）：linear-gradient(90deg, transparent, skelhi, transparent)
/// 从 translateX(-100%) 到 100%，1.5s ease-in-out 无限。
private struct CShimmer: View {
    let highlight: Color
    @State private var phase: CGFloat = -1

    var body: some View {
        GeometryReader { geo in
            LinearGradient(
                // transparent → skelhi → transparent（规范 5.10，transparent 非设计色值）。
                colors: [Color.clear, highlight, Color.clear],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: geo.size.width, height: geo.size.height)
            .offset(x: phase * geo.size.width)
            .onAppear {
                withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: false)) {
                    phase = 1
                }
            }
        }
    }
}

/// 水平细线（截断标线用），虚线由调用方以 StrokeStyle 给定。
private struct CHorizontalRule: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        return path
    }
}
