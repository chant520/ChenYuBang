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
                let cancelEnabled = overlay.route == .importMode
                    ? !router.backupImportInProgress : !router.clearInProgress
                COverlay(style: overlay.route == .importMode ? .sheet : .dialog,
                         title: overlay.route.title,
                         cancelEnabled: cancelEnabled,
                         onDismiss: router.closeOverlay) {
                    BackupOverlayContent(route: overlay.route, router: router, launch: launch)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func isActive(_ tab: AppTab) -> Bool {
        router.selectedTab == tab && router.fullScreenEntries.isEmpty && !router.needsFirstLaunch
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
                AppRoutePage(router: router, launch: launch, entry: entry, tab: tab,
                             isActive: entries.last?.id == entry.id)
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
    let isActive: Bool

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
                  let exampleReader = launch.exampleReader,
                  let idiomID = entry.recordID {
            P05IdiomDetailScreen(
                id: idiomID,
                reader: reader,
                examplesReader: exampleReader,
                reviewCounter: launch.reviewReader as PendingReviewCountReading?,
                onBack: { router.back() },
                onOpenMistakes: { router.goToMistakes() },
                onOpenExample: { id in _ = router.push(.exampleDetail, recordID: id) },
                onReviewIdiom: { word in
                    _ = self.startReview(source: .idiom(id: idiomID, word: word))
                }
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
        } else if entry.route == .mistakes, let reader = launch.mistakeReader {
            P08MistakeScreen(
                reader: reader,
                contentRevision: router.userContentRevision,
                onOpenIdiom: { id in _ = router.push(.idiomMistakes, recordID: id) },
                onOpenUserExample: { id in _ = router.push(.mistakeDetail, recordID: id) },
                onCapture: { _ = router.beginCapture() },
                onStartReview: { self.startReview(source: .global) }
            )
        } else if entry.route == .idiomMistakes,
                  let reader = launch.mistakeReader,
                  let idiomID = entry.recordID {
            P09IdiomCapturedScreen(
                idiomID: idiomID,
                reader: reader,
                contentRevision: router.userContentRevision,
                onBack: { router.back() },
                onOpenIdiom: { _ = router.push(.idiomDetail, recordID: idiomID) },
                onOpenExample: { id in _ = router.push(.mistakeDetail, recordID: id) },
                onBackToBook: { router.back() }
            )
        } else if entry.route == .mistakeDetail,
                  let reader = launch.mistakeReader,
                  let editor = launch.userExampleEditor,
                  let libraryReader = launch.libraryReader,
                  let exampleID = entry.recordID {
            // 路由键 `mistake/{id}` 沿用规范 §5.1 的 23 行，值取 `user_example.id`（P10 是录入题详情）。
            P10UserExampleScreen(
                id: exampleID,
                reader: reader,
                editor: editor,
                libraryReader: libraryReader,
                dataDirectory: launch.dataDirectory,
                onBack: { router.back() },
                onOpenIdiom: { id in _ = router.push(.idiomDetail, recordID: id) },
                onContentChanged: { router.bumpUserContent() },
                onDeleted: { router.back() }
            )
        } else if entry.route == .camera, let workflowID = router.captureWorkflow?.id {
            P17CaptureScreen(
                isActive: isActive,
                onCancel: router.cancelCapture,
                onImageReady: { url in _ = router.captured(workflowID: workflowID, imageURL: url) }
            )
        } else if entry.route == .crop,
                  let workflowID = router.captureWorkflow?.id,
                  let imageURL = router.captureWorkflow?.imageURL {
            P18CropScreen(
                imageURL: imageURL,
                onRetake: { router.back() },
                onImageReady: { url in _ = router.cropped(workflowID: workflowID, imageURL: url) }
            )
        } else if entry.route == .ocr,
                  let workflowID = router.captureWorkflow?.id,
                  let imageURL = router.captureWorkflow?.croppedURL {
            P19OcrScreen(
                imageURL: imageURL,
                onBack: { router.back() },
                onRetake: { _ = router.retakeFromOcr(workflowID: workflowID) },
                onManual: { _ = router.recognizedManually(workflowID: workflowID, entryID: entry.id) },
                onNext: { text in
                    if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        _ = router.recognizedManually(workflowID: workflowID, entryID: entry.id)
                    } else {
                        _ = router.recognized(workflowID: workflowID, entryID: entry.id, text: text)
                    }
                }
            )
        } else if entry.route == .match,
                  let workflowID = router.captureWorkflow?.id,
                  let imageURL = router.captureWorkflow?.croppedURL,
                  let reader = launch.libraryReader,
                  let saver = launch.captureSaver {
            P20MatchScreen(
                text: router.captureWorkflow?.recognizedText ?? "",
                imageURL: imageURL,
                reader: reader,
                saver: saver,
                onBack: { router.back() },
                onSaved: { saved in
                    _ = router.saved(
                        workflowID: workflowID,
                        userExampleID: saved.userExampleID,
                        mistakeID: saved.mistakeID,
                        idioms: saved.idioms
                    )
                }
            )
        } else if entry.route == .done,
                  let workflow = router.captureWorkflow,
                  !workflow.savedIdioms.isEmpty {
            P21DoneScreen(
                idioms: workflow.savedIdioms,
                onOpenIdiom: { id in _ = router.finishCaptureToIdiom(id) },
                onOpenMistakes: { _ = router.finishCapture() },
                onContinue: { _ = router.continueCapture() }
            )
        } else if entry.route == .backup, let service = launch.backupService {
            BackupScreen(
                service: service,
                importResult: router.backupImportResult,
                onValidated: router.prepareBackupImport,
                onBack: { router.back() }
            )
        } else if entry.route == .pack, let pack = launch.currentPack {
            PackVersionScreen(pack: pack, onBack: { router.back() })
        } else if entry.route == .queue, let reader = launch.reviewReader {
            P11ReviewScreen(
                reader: reader,
                scope: reviewScope(for: router.reviewSession),
                queueIDs: entry.queueIDs,
                onRoundFinished: { outcome in
                    _ = router.finishReview(queueEntryID: entry.id, outcome: outcome)
                },
                onExit: { router.dismissFullScreen() }
            )
        } else if entry.route == .summary,
                  let reader = launch.reviewReader,
                  let summary = entry.reviewOutcome {
            P12SummaryScreen(
                reader: reader,
                scope: reviewScope(for: router.reviewSession),
                summary: summary,
                queueIDs: entry.queueIDs,
                onHome: { router.dismissFullScreen() },
                onRetryRound: { _ = router.retryReview(queueIDs: entry.queueIDs) },
                onOpenIdiom: { id in _ = router.push(.idiomDetail, recordID: id) }
            )
        } else {
            RoutePlaceholderPage(router: router, launch: launch, entry: entry, tab: tab)
        }
    }

    /// P11 / P12 的「本轮范围」一行取复习会话的来源（决策 E）；会话不在时按全局兜底。
    private func reviewScope(for session: ReviewSession?) -> ReviewScope {
        guard let session, case .idiom(let id, let word) = session.source else { return .global() }
        return .idiom(id: id, word: word)
    }

    /// 复习入口（P08 ④ 全局、P05 D1 单成语）：点下时才读该来源的待复习队列，队列 id 交路由。
    /// 返回 false＝队列已空或读库失败，由来源页如实提示（跳转表 19），不开空复习。
    private func startReview(source: ReviewSource) -> Bool {
        guard let reader = launch.reviewReader else { return false }
        let idiomID: String?
        if case .idiom(let id, _) = source { idiomID = id } else { idiomID = nil }
        guard let ids = try? reader.queue(idiomID: idiomID).map(\.id), !ids.isEmpty else { return false }
        return router.beginReview(source: source, queueIDs: ids)
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
            if let message = router.clearResult { PendingPageText(text: message) }
            CButton(text: "考试范围", variant: .ghost) { router.push(.scope) }
            CButton(text: "备份与恢复", variant: .ghost) { router.push(.backup) }
            CButton(text: "题库版本", variant: .ghost) { router.push(.pack) }
            CButton(text: "清空确认", variant: .dangerOutline) { router.openOverlay(.clear) }
        case .backup:
            CButton(text: "导入方式", variant: .ghost) { router.openOverlay(.importMode) }
        case .mistakes:
            // P08 已接线，本分支只在本机数据库未就绪（reader 为 nil）时兜底。
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
