import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

struct RoutePlaceholderPage: View {
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var router: RouterState
    @ObservedObject var launch: FirstLaunchState
    @State private var updateFailure: String?
    let entry: RouteEntry
    let tab: AppTab
    let isActive: Bool

    var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Tokens.Space.block) {
                    Text(entry.route.title)
                        .font(.system(size: Tokens.Font.n20, weight: .semibold))
                        .foregroundStyle(colors.text)
                        .accessibilityAddTraits(.isHeader)
                        .id(entry.id)
                    if router.canGoBack {
                        CButton(text: entry.route == .camera ? "取消拍照" : "返回",
                                variant: .ghost, action: router.back)
                    }
                    if entry.route == .examples, let updateFailure {
                        CBar(
                            kind: .warn,
                            title: updateFailure,
                            subtitle: "当前题库仍可离线浏览，版本号不变；可到题库版本页重试。"
                        )
                    }
                    PendingPageText(text: "页面待接入。没有真实数据时不展示题目、统计或示例详情。")
                    actions
                }
                .padding(.horizontal, Tokens.Space.pageX)
                .padding(.vertical, Tokens.Space.contentTop)
            }
            .onAppear { consumeTopRequest(proxy) }
            .onChange(of: router.topRequests[tab]) { _ in consumeTopRequest(proxy) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
        .task(id: packUpdateTaskID) {
            guard isActive, entry.route == .examples, let updates = launch.packUpdates else { return }
            await updates.onEnterLibrary()
            updateFailure = updates.failureMessage
        }
    }

    var packUpdateTaskID: Int? {
        isActive ? router.tabEntryEpoch[tab] : nil
    }

    @ViewBuilder
    private var actions: some View {
        switch entry.route {
        case .recommend:
            if let scope = launch.preferences?.scope { PendingPageText(text: scope.description) }
            CButton(text: "成语库", variant: .ghost) { router.push(.library) }
            CButton(text: "搜索", variant: .ghost) { router.openSearch() }
            CButton(text: "去错题本", variant: .ghost, action: router.goToMistakes)
        case .profile:
            if let message = router.clearResult { PendingPageText(text: message) }
            CButton(text: "考试范围", variant: .ghost) { router.push(.scope) }
            CButton(text: "成语自测", variant: .ghost) { router.beginSelfTest() }
            CButton(text: "备份与恢复", variant: .ghost) { router.push(.backup) }
            CButton(text: "题库版本", variant: .ghost) { router.push(.pack) }
            CButton(text: "清空确认", variant: .dangerOutline) { router.openOverlay(.clear) }
        case .backup:
            CButton(text: "导入方式", variant: .ghost) { router.openOverlay(.importMode) }
        case .mistakes:
            PendingPageText(text: "错题本需在本机数据库就绪后显示；未读到数据时不展示题目与统计。")
        case .idiomDetail:
            PendingPageText(text: "复习队列待真实错题数据接入；空队列不启动复习。")
            CButton(text: "去错题本", variant: .ghost, action: router.goToMistakes)
        case .queue, .summary:
            PendingPageText(text: "复习业务待接入；仅业务完成回调推进，不生成成绩或重试队列。")
            CButton(text: "结束复习", variant: .ghost, action: router.dismissFullScreen)
        case .crop, .ocr, .match:
            PendingPageText(text: "拍照业务待接入；仅真实拍摄、裁切、识别、匹配及保存回调推进。")
            CButton(text: "取消拍照", variant: .ghost, action: router.cancelCapture)
        case .done:
            CButton(text: "回错题本") { router.finishCapture() }
        default:
            EmptyView()
        }
    }

    private func consumeTopRequest(_ proxy: ScrollViewProxy) {
        guard entry.route == tab.root, let token = router.topRequests[tab] else { return }
        proxy.scrollTo(entry.id, anchor: .top)
        router.consumeTopRequest(for: tab, token: token)
    }
}

struct PendingPageText: View {
    @Environment(\.colorScheme) private var colorScheme
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: Tokens.Font.n13))
            .foregroundStyle(CangyuTheme.colors(for: colorScheme).text2)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
