import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// 不使用 NavigationStack：隐藏而不销毁四标签和每个 entry，保留页面局部状态及 ScrollView。
struct AppRouteHost: View {
    @ObservedObject var router: RouterState
    @ObservedObject var launch: FirstLaunchState

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                ZStack {
                    ForEach(AppTab.allCases, id: \.self) { tab in
                        RetainedRouteStack(router: router, launch: launch, entries: router.entries(for: tab), tab: tab)
                            .opacity(isActive(tab) ? 1 : 0)
                            .allowsHitTesting(isActive(tab))
                            .accessibilityHidden(!isActive(tab))
                    }
                    if !router.fullScreenEntries.isEmpty {
                        RetainedRouteStack(router: router, launch: launch,
                                           entries: router.suspendedFullScreenEntries + router.fullScreenEntries,
                                           tab: router.selectedTab)
                    }
                }
                if router.tabBarVisible {
                    CTabBar(selected: router.selectedTab.chromeItem) { item in
                        if let tab = AppTab.allCases.first(where: { $0.chromeItem == item }) { router.selectTab(tab) }
                    }
                }
            }
            .allowsHitTesting(router.overlay == nil && !router.needsFirstLaunch)
            .accessibilityHidden(router.overlay != nil || router.needsFirstLaunch)
            if router.fabVisible {
                CFab { router.beginCapture() }
                    .accessibilityLabel("拍照添加错题")
                    .padding(.trailing, Tokens.Space.fabRight)
                    .padding(.bottom, Tokens.Space.fabBottom)
            }
            if router.needsFirstLaunch {
                FirstLaunchGate(launch: launch, router: router)
            }
            if let overlay = router.overlay {
                COverlay(style: overlay.route == .importMode ? .sheet : .dialog,
                         title: overlay.route.title, onDismiss: router.closeOverlay) {
                    overlayContent(overlay.route)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func isActive(_ tab: AppTab) -> Bool {
        router.selectedTab == tab && router.fullScreenEntries.isEmpty && !router.needsFirstLaunch
    }

    @ViewBuilder
    private func overlayContent(_ route: AppRoute) -> some View {
        if route == .importMode {
            PendingPageText(text: "导入业务待接入；取消不会修改本机数据。")
            CButton(text: "合并导入（待接入）", enabled: false) {}
            CButton(text: "覆盖导入（待接入）", enabled: false) {}
        } else {
            PendingPageText(text: "清空业务待接入；取消不会修改本机数据。")
            CButton(text: "确认清空（待接入）", variant: .dangerFilled, enabled: false) {}
        }
    }
}

private extension AppTab {
    var chromeItem: CTabBarItem {
        switch self {
        case .idiom: return .idiom
        case .example: return .example
        case .mistake: return .mistake
        case .profile: return .profile
        }
    }
}

private struct RetainedRouteStack: View {
    @ObservedObject var router: RouterState
    @ObservedObject var launch: FirstLaunchState
    let entries: [RouteEntry]
    let tab: AppTab

    var body: some View {
        ZStack {
            ForEach(entries) { entry in
                AppRoutePage(router: router, launch: launch, entry: entry, tab: tab)
                    .opacity(entries.last?.id == entry.id ? 1 : 0)
                    .allowsHitTesting(entries.last?.id == entry.id)
                    .accessibilityHidden(entries.last?.id != entry.id)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct AppRoutePage: View {
    @ObservedObject var router: RouterState
    @ObservedObject var launch: FirstLaunchState
    let entry: RouteEntry
    let tab: AppTab

    var body: some View {
        if entry.route == .scope, let preferences = launch.preferences {
            ExamScopeScreen(
                mode: .settings,
                initial: preferences.scope,
                availableProvinces: launch.availableProvinces,
                onSave: { scope, complete in
                    try launch.save(scope: scope, complete: complete)
                    router.back()
                },
                onCancel: { router.back() }
            )
        } else if entry.route == .library,
                  let preferences = launch.preferences,
                  let reader = launch.libraryReader {
            LibraryScreen(
                scope: preferences.scope,
                reader: reader,
                onOpenScope: { _ = router.push(.scope) },
                onOpenIdiom: { id in _ = router.push(.idiomDetail, recordID: id) },
                onSearch: { _ = router.openSearch() },
                onBack: router.canGoBack ? { router.back() } : nil
            )
        } else if entry.route == .idiomDetail,
                  let reader = launch.libraryReader,
                  let idiomID = entry.recordID {
            P05IdiomDetailScreen(
                id: idiomID,
                reader: reader,
                onBack: { router.back() },
                onOpenMistakes: { router.goToMistakes() }
            )
        } else if entry.route == .exampleDetail,
                  let reader = launch.exampleReader,
                  let exampleID = entry.recordID {
            P07ExampleDetailScreen(
                id: exampleID,
                reader: reader,
                onBack: { router.back() },
                onOpenIdiom: { id in _ = router.push(.idiomDetail, recordID: id) }
            )
        } else if entry.route == .search,
                  let reader = launch.libraryReader {
            P04SearchScreen(
                reader: reader,
                onClose: { router.back() },
                onOpenIdiom: { id in _ = router.push(.idiomDetail, recordID: id) }
            )
        } else {
            RoutePlaceholderPage(router: router, launch: launch, entry: entry, tab: tab)
        }
    }
}

private struct RoutePlaceholderPage: View {
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject var router: RouterState
    @ObservedObject var launch: FirstLaunchState
    let entry: RouteEntry
    let tab: AppTab

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
            CButton(text: "考试范围", variant: .ghost) { router.push(.scope) }
            CButton(text: "备份与恢复", variant: .ghost) { router.push(.backup) }
            CButton(text: "题库版本", variant: .ghost) { router.push(.pack) }
            CButton(text: "清空确认", variant: .dangerOutline) { router.openOverlay(.clear) }
        case .backup:
            CButton(text: "导入方式", variant: .ghost) { router.openOverlay(.importMode) }
        case .mistakes:
            PendingPageText(text: "复习队列待真实错题数据接入；空队列不启动复习。")
        case .idiomDetail:
            PendingPageText(text: "复习队列待真实错题数据接入；空队列不启动复习。")
            CButton(text: "去错题本", variant: .ghost, action: router.goToMistakes)
        case .queue, .summary:
            PendingPageText(text: "复习业务待接入；仅业务完成回调推进，不生成成绩或重试队列。")
            CButton(text: "结束复习", variant: .ghost, action: router.dismissFullScreen)
        case .camera, .crop, .ocr, .match:
            PendingPageText(text: "拍照业务待接入；仅真实拍摄、裁切、识别、匹配及保存回调推进。")
            if entry.route != .camera {
                CButton(text: "取消拍照", variant: .ghost, action: router.cancelCapture)
            }
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

private struct PendingPageText: View {
    @Environment(\.colorScheme) private var colorScheme
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: Tokens.Font.n13))
            .foregroundStyle(CangyuTheme.colors(for: colorScheme).text2)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
