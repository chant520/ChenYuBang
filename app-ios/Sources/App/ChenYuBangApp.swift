import SwiftUI
import CangyuDesignSystem
import CangyuTokens

@main
struct ChenYuBangApp: App {
    var body: some Scene {
        WindowGroup {
            // 骨架占位：P02 推荐首页在下一阶段接入，路由表见规范 §5.1
            PlaceholderView()
                .preferredColorScheme(nil)
        }
    }
}

private struct PlaceholderView: View {
    @Environment(\.colorScheme) private var colorScheme

    private var colors: TokenColors {
        colorScheme == .dark ? Tokens.dark : Tokens.light
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            Text("成语帮")
                .font(.system(size: Tokens.Font.n20, weight: .semibold))
                .foregroundStyle(colors.text)
            Text("工程骨架已初始化，页面待接入。")
                .font(.system(size: Tokens.Font.n13))
                .foregroundStyle(colors.text2)
            Spacer()
        }
        .padding(.horizontal, Tokens.Space.pageX)
        .padding(.top, Tokens.Space.contentTop)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(colors.page)
    }
}
