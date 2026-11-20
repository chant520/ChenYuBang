import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P21 保存成功（信息架构 v1.4 P21 / 高保真稿 C P21 1014–1062 行）：
/// 告知已存入我的题库与归属，给出三个下一步去处；纯过渡页，无返回。
/// 多条归属按候选顺序以「、」连接；「去成语详情」由路由取第一条。
struct P21DoneScreen: View {
    @Environment(\.colorScheme) private var colorScheme

    private let idioms: [SavedIdiom]
    private let onOpenIdiom: (String) -> Void
    private let onOpenMistakes: () -> Void
    private let onContinue: () -> Void

    init(
        idioms: [SavedIdiom],
        onOpenIdiom: @escaping (String) -> Void,
        onOpenMistakes: @escaping () -> Void,
        onContinue: @escaping () -> Void
    ) {
        self.idioms = idioms
        self.onOpenIdiom = onOpenIdiom
        self.onOpenMistakes = onOpenMistakes
        self.onContinue = onContinue
    }

    var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                P21SuccessMark(color: colors.accent)
                    .frame(width: 46, height: 46)
                Text("已存入我的题库")
                    .font(.system(size: Tokens.Font.n15, weight: .bold))
                    .foregroundStyle(colors.head)
                    .padding(.top, 14)
                ownershipLine(colors)
                    .padding(.top, 6)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("已存入我的题库，\(ownershipText)")

            CButton(text: "去成语详情") {
                if let first = idioms.first { onOpenIdiom(first.id) }
            }
            .padding(.top, 26)

            HStack(spacing: Tokens.Space.lmetaGap) {
                CButton(text: "去错题本", variant: .ghost, action: onOpenMistakes)
                CButton(text: "继续录下一题", variant: .ghost, action: onContinue)
            }
            .padding(.top, Tokens.Space.ghostY)

            Text("只存在本机，不会上传")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
                .padding(.top, 20)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, Tokens.Space.pageX)
        .padding(.top, 52)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(colors.page)
    }

    private var ownershipText: String {
        "这道题归到了 \(idioms.map(\.word).joined(separator: "、")) 下"
    }

    /// 归属句：成语名用 text 加粗，其余 text2；拼接 Text 保留行内加粗与换行。
    private func ownershipLine(_ colors: TokenColors) -> some View {
        (
            Text("这道题归到了 ")
                + Text(idioms.map(\.word).joined(separator: "、"))
                .fontWeight(.semibold)
                .foregroundColor(colors.text)
                + Text(" 下")
        )
        .font(.system(size: Tokens.Font.n12))
        .foregroundColor(colors.text2)
        .multilineTextAlignment(.center)
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// 46pt 成功标记：SVG viewBox 24 等比（圆 r9、勾 stroke 1.6）。
private struct P21SuccessMark: View {
    let color: Color

    var body: some View {
        let scale = 46.0 / 24.0
        let stroke = 1.6 * scale
        ZStack {
            Circle()
                .stroke(color, lineWidth: stroke)
                .frame(width: 18 * scale, height: 18 * scale)
            Path { path in
                path.move(to: CGPoint(x: 8 * scale, y: 12.5 * scale))
                path.addLine(to: CGPoint(x: 11 * scale, y: 15.5 * scale))
                path.addLine(to: CGPoint(x: 16 * scale, y: 9.5 * scale))
            }
            .stroke(
                color,
                style: StrokeStyle(lineWidth: stroke, lineCap: .round, lineJoin: .round)
            )
        }
        .frame(width: 46, height: 46)
        .accessibilityHidden(true)
    }
}
