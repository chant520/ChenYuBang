import SwiftUI
import CangyuDesignSystem
import CangyuTokens

@main
struct ChenYuBangApp: App {
    var body: some Scene {
        WindowGroup {
            LocalAppRoot()
                .preferredColorScheme(nil)
        }
    }
}

private struct LocalAppRoot: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var launch = FirstLaunchState()
    @StateObject private var router = RouterState(needsFirstLaunch: true)

    var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        Group {
            if launch.preferences != nil {
                AppRouteHost(router: router, launch: launch)
            } else {
                VStack(alignment: .leading, spacing: Tokens.Space.block) {
                    Text("成语帮")
                        .font(.system(size: Tokens.Font.n20, weight: .semibold))
                        .foregroundStyle(colors.text)
                    if let error = launch.initializationError {
                        Text(error)
                            .font(.system(size: Tokens.Font.n13))
                            .foregroundStyle(launch.contentError ? colors.warnfg : colors.errfg)
                        CButton(text: "重试初始化", action: initialize)
                    } else {
                        CSpinner()
                            .foregroundStyle(colors.text2)
                        Text("正在初始化本机数据…")
                            .font(.system(size: Tokens.Font.n13))
                            .foregroundStyle(colors.text2)
                    }
                }
                .padding(.horizontal, Tokens.Space.pageX)
                .padding(.vertical, Tokens.Space.contentTop)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(colors.page)
            }
        }
        .task { initialize() }
    }

    private func initialize() {
        launch.initialize()
        if launch.preferences?.complete == true { router.completeFirstLaunch() }
    }
}
