import Combine
import CangyuData
import Foundation

struct RouteViewState: Equatable {
    var query = ""
    var filters: [String: String] = [:]
    var scrollAnchor: String?
}

struct RouteEntry: Identifiable, Equatable {
    let id = UUID()
    let route: AppRoute
    var recordID: String?
    var queueIDs: [String] = []
    /// 本轮复习小结：跟着 P12 那一条栈条目存，「再复习一轮」后再退回上一轮 P12 仍是当轮数字。
    var reviewOutcome: ReviewRoundOutcome?
    var viewState = RouteViewState()
}

enum ReviewSource: Equatable {
    case global
    /// 单成语模式：`word` 为该成语词面，供 P11 / P12 的「本轮范围」一行显示。
    case idiom(id: String, word: String)
}

struct ReviewSession: Equatable {
    let source: ReviewSource
    let tab: AppTab
    let originEntryID: UUID
}

/// iOS 15 的显式 entry 栈；页面状态跟随 UUID，不按路由键去重。
final class RouterState: ObservableObject {
    @Published private(set) var selectedTab: AppTab = .idiom
    @Published private(set) var tabEntries: [AppTab: [RouteEntry]]
    @Published private(set) var fullScreenEntries: [RouteEntry] = []
    @Published internal(set) var overlay: RouteEntry?
    @Published private(set) var reviewSession: ReviewSession?
    @Published private(set) var captureWorkflow: CaptureWorkflow?
    @Published private(set) var needsFirstLaunch: Bool
    @Published private(set) var topRequests: [AppTab: UUID] = [:]
    @Published private(set) var tabEntryEpoch: [AppTab: Int]
    /// 用户内容写入令牌：P10 保存 / 删除后刷新，让常驻挂载的来源页重新读库。
    /// `RetainedRouteStack` 只切透明度、不重建页面，`.task` 不会在返回时重跑（开发规范 §8.1.1「来源页列表刷新」）。
    @Published private(set) var userContentRevision = UUID()
    @Published internal(set) var pendingBackup: ValidatedBackup?
    @Published internal(set) var backupImportInProgress = false
    @Published internal(set) var backupImportResult: String?
    @Published internal(set) var backupImportError: String?
    @Published internal(set) var clearInProgress = false
    @Published internal(set) var clearResult: String?
    @Published internal(set) var clearError: String?
    private let onboardingEntry = RouteEntry(route: .onboarding)
    @Published private(set) var suspendedFullScreenEntries: [RouteEntry] = []

    init(needsFirstLaunch: Bool = false) {
        self.needsFirstLaunch = needsFirstLaunch
        tabEntries = Dictionary(uniqueKeysWithValues: AppTab.allCases.map {
            ($0, [RouteEntry(route: $0.root)])
        })
        tabEntryEpoch = Dictionary(uniqueKeysWithValues: AppTab.allCases.map { ($0, 0) })
    }

    var currentEntry: RouteEntry {
        if needsFirstLaunch { return onboardingEntry }
        return fullScreenEntries.last ?? entries(for: selectedTab)[entries(for: selectedTab).count - 1]
    }

    var tabBarVisible: Bool { !needsFirstLaunch && fullScreenEntries.isEmpty }
    var fabVisible: Bool { tabBarVisible && overlay == nil && RouteRegistry.fabRoutes.contains(currentEntry.route) }
    var canGoBack: Bool {
        if overlay != nil { return true }
        if needsFirstLaunch || currentEntry.route == .done { return false }
        return !fullScreenEntries.isEmpty || entries(for: selectedTab).count > 1
    }

    func entries(for tab: AppTab) -> [RouteEntry] { tabEntries[tab] ?? [] }

    func completeFirstLaunch() { needsFirstLaunch = false }

    func selectTab(_ tab: AppTab) {
        guard tabBarVisible, overlay == nil else { return }
        if tab == selectedTab { resetToRoot(tab) }
        selectedTab = tab
        tabEntryEpoch[tab, default: 0] += 1
    }

    func consumeTopRequest(for tab: AppTab, token: UUID) {
        guard topRequests[tab] == token else { return }
        topRequests.removeValue(forKey: tab)
    }

    /// 用户录入题 / 错题写入本机库后调用；来源页据此重新读库。
    func bumpUserContent() {
        userContentRevision = UUID()
    }

    func updateViewState(entryID: UUID, state: RouteViewState) {
        if let index = suspendedFullScreenEntries.firstIndex(where: { $0.id == entryID }) {
            suspendedFullScreenEntries[index].viewState = state
            return
        }
        if let index = fullScreenEntries.firstIndex(where: { $0.id == entryID }) {
            fullScreenEntries[index].viewState = state
            return
        }
        for tab in AppTab.allCases {
            if let index = tabEntries[tab]?.firstIndex(where: { $0.id == entryID }) {
                tabEntries[tab]?[index].viewState = state
                return
            }
        }
    }

    @discardableResult
    func push(_ route: AppRoute, recordID: String? = nil) -> Bool {
        guard !needsFirstLaunch, overlay == nil, route.presentation == .push,
              captureWorkflow == nil, reviewSession == nil || currentEntry.route != .queue else { return false }
        let details: Set<AppRoute> = [.idiomDetail, .exampleDetail, .idiomMistakes, .mistakeDetail]
        if details.contains(route), !validID(recordID) { return false }
        let entry = RouteEntry(route: route, recordID: recordID)
        if fullScreenEntries.isEmpty {
            tabEntries[selectedTab]?.append(entry)
        } else {
            fullScreenEntries.append(entry)
        }
        return true
    }

    @discardableResult
    func openSearch() -> Bool {
        guard canStartFullScreen else { return false }
        fullScreenEntries = [RouteEntry(route: .search)]
        return true
    }

    @discardableResult
    func openOverlay(_ route: AppRoute) -> Bool {
        guard tabBarVisible, overlay == nil else { return false }
        guard (route == .importMode && currentEntry.route == .backup)
            || (route == .clear && currentEntry.route == .profile) else { return false }
        overlay = RouteEntry(route: route)
        return true
    }

    func closeOverlay() {
        guard !backupImportInProgress, !clearInProgress else { return }
        overlay = nil
        pendingBackup = nil
    }

    @discardableResult
    func beginReview(source: ReviewSource, queueIDs: [String]) -> Bool {
        guard canStartWorkflow, validQueue(queueIDs) else { return false }
        switch source {
        case .global:
            guard currentEntry.route == .mistakes else { return false }
        case .idiom(let idiomID, _):
            guard currentEntry.route == .idiomDetail, currentEntry.recordID == idiomID else { return false }
        }
        reviewSession = ReviewSession(source: source, tab: selectedTab, originEntryID: currentEntry.id)
        suspendedFullScreenEntries = fullScreenEntries
        fullScreenEntries = [RouteEntry(route: .queue, queueIDs: queueIDs)]
        return true
    }

    /// 仅由真实复习业务完成回调调用；不在占位页制造成绩。
    /// 题量取本轮队列长度，`answered` 之外的无法比对题只计题量（第 8.2 节）。
    @discardableResult
    func finishReview(queueEntryID: UUID, outcome: ReviewRoundOutcome) -> Bool {
        guard reviewSession != nil, currentEntry.route == .queue, currentEntry.id == queueEntryID,
              outcome.total > 0 else { return false }
        fullScreenEntries[fullScreenEntries.count - 1] = RouteEntry(
            route: .summary,
            queueIDs: currentEntry.queueIDs,
            reviewOutcome: outcome
        )
        return true
    }

    @discardableResult
    func retryReview(queueIDs: [String]) -> Bool {
        guard reviewSession != nil, currentEntry.route == .summary, validQueue(queueIDs) else { return false }
        fullScreenEntries.append(RouteEntry(route: .queue, queueIDs: queueIDs))
        return true
    }

    func goToMistakes() {
        guard !needsFirstLaunch, overlay == nil, captureWorkflow == nil else { return }
        dismissFullScreen()
        fullScreenEntries = []
        selectedTab = .mistake
        resetToRoot(.mistake)
    }

    func back() {
        if overlay != nil { closeOverlay(); return }
        guard canGoBack else { return }
        if !fullScreenEntries.isEmpty {
            if fullScreenEntries.count == 1 { dismissFullScreen() } else {
                let leaving = fullScreenEntries.removeLast()
                if leaving.route == .crop {
                    // 返回/重拍回 P17：原图与裁切图都丢弃，重拍拿新图（IA §4.3 链路 28）。
                    captureWorkflow?.imageURL = nil
                    captureWorkflow?.croppedURL = nil
                }
                invalidateCaptureResults()
            }
        } else if entries(for: selectedTab).count > 1 {
            tabEntries[selectedTab]?.removeLast()
        }
    }

    func dismissFullScreen() {
        fullScreenEntries = suspendedFullScreenEntries
        suspendedFullScreenEntries = []
        reviewSession = nil
        captureWorkflow = nil
    }

    private var canStartFullScreen: Bool {
        canStartWorkflow && fullScreenEntries.isEmpty
    }

    var canStartWorkflow: Bool {
        !needsFirstLaunch && overlay == nil && reviewSession == nil && captureWorkflow == nil
    }

    private func resetToRoot(_ tab: AppTab) {
        guard var root = entries(for: tab).first else { return }
        root.viewState.scrollAnchor = nil
        tabEntries[tab] = [root]
        topRequests[tab] = UUID()
    }

    func validID(_ value: String?) -> Bool {
        guard let value else { return false }
        return !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func validQueue(_ values: [String]) -> Bool {
        !values.isEmpty && values.allSatisfy { validID($0) }
    }
}

extension RouterState {
    @discardableResult
    func beginCapture() -> Bool {
        guard canStartWorkflow else { return false }
        captureWorkflow = CaptureWorkflow(tab: selectedTab, originEntryID: currentEntry.id)
        suspendedFullScreenEntries = fullScreenEntries
        fullScreenEntries = [RouteEntry(route: .camera)]
        return true
    }

    @discardableResult
    func captured(workflowID: UUID, imageURL: URL) -> Bool {
        guard accepts(workflowID, step: .camera) else { return false }
        captureWorkflow?.imageURL = imageURL
        captureWorkflow?.croppedURL = nil
        clearCaptureResult()
        fullScreenEntries.append(RouteEntry(route: .crop))
        return true
    }

    @discardableResult
    func cropped(workflowID: UUID, imageURL: URL) -> Bool {
        guard accepts(workflowID, step: .crop) else { return false }
        captureWorkflow?.croppedURL = imageURL
        clearCaptureResult()
        fullScreenEntries.append(RouteEntry(route: .ocr))
        return true
    }

    @discardableResult
    func recognized(workflowID: UUID, entryID: UUID, text: String) -> Bool {
        guard accepts(workflowID, step: .ocr), currentEntry.id == entryID,
              !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return false }
        captureWorkflow?.recognizedText = text
        fullScreenEntries.append(RouteEntry(route: .match))
        return true
    }

    /// P19 失败态「手动补字」：以空文本进入修正页（IA §4.3 链路 27 第三条退路）。
    @discardableResult
    func recognizedManually(workflowID: UUID, entryID: UUID) -> Bool {
        guard accepts(workflowID, step: .ocr), currentEntry.id == entryID else { return false }
        captureWorkflow?.recognizedText = nil
        fullScreenEntries.append(RouteEntry(route: .match))
        return true
    }

    /// P19 失败态「重拍这张」：回 P17 重拍，原图 / 裁切图 / 识别文本全部作废。
    @discardableResult
    func retakeFromOcr(workflowID: UUID) -> Bool {
        guard accepts(workflowID, step: .ocr) else { return false }
        captureWorkflow?.imageURL = nil
        captureWorkflow?.croppedURL = nil
        clearCaptureResult()
        fullScreenEntries = [RouteEntry(route: .camera)]
        return true
    }

    /// P20 保存成功：记下 user_example / 可选 mistake / 全部归属，进 P21。
    /// 未标错时 mistakeID 为 nil（P21 仍给「去错题本」去处，只是本题不在错题本里）。
    @discardableResult
    func saved(workflowID: UUID, userExampleID: String, mistakeID: String?, idioms: [SavedIdiom]) -> Bool {
        guard accepts(workflowID, step: .match), validID(userExampleID),
              mistakeID == nil || validID(mistakeID),
              !idioms.isEmpty, idioms.allSatisfy({ validID($0.id) && !$0.word.isEmpty })
        else { return false }
        captureWorkflow?.savedUserExampleID = userExampleID
        captureWorkflow?.savedMistakeID = mistakeID
        captureWorkflow?.savedIdioms = idioms
        fullScreenEntries.append(RouteEntry(route: .done, recordID: userExampleID))
        return true
    }

    func cancelCapture() {
        guard captureWorkflow != nil else { return }
        dismissFullScreen()
    }

    /// P21「去错题本」：P17–P21 全部出栈，切到错题标签首屏。
    @discardableResult
    func finishCapture() -> Bool {
        guard currentEntry.route == .done, captureWorkflow?.savedUserExampleID != nil else { return false }
        dismissFullScreen()
        fullScreenEntries = []
        selectedTab = .mistake
        resetToRoot(.mistake)
        // P08 根入口常驻挂载、返回时不重跑 .task，新录入的题要靠写入令牌才看得到。
        bumpUserContent()
        return true
    }

    /// P21「去成语详情」：P17–P21 全部出栈，在拍照来源标签压入 P05（可返回该标签首屏）。
    /// 录题归属按候选顺序，手动增补排末尾；只接受本次保存过的成语。
    @discardableResult
    func finishCaptureToIdiom(_ idiomID: String) -> Bool {
        guard currentEntry.route == .done,
              let workflow = captureWorkflow,
              workflow.savedUserExampleID != nil,
              workflow.savedIdioms.contains(where: { $0.id == idiomID }),
              validID(idiomID)
        else { return false }
        let tab = workflow.tab
        dismissFullScreen()
        selectedTab = tab
        tabEntries[tab]?.append(RouteEntry(route: .idiomDetail, recordID: idiomID))
        return true
    }

    /// P21「继续录下一题」：保留本次来源标签，清掉上一题的图与保存回执，回到 P17。
    @discardableResult
    func continueCapture() -> Bool {
        guard currentEntry.route == .done, captureWorkflow?.savedUserExampleID != nil else { return false }
        captureWorkflow?.imageURL = nil
        captureWorkflow?.croppedURL = nil
        clearCaptureResult()
        fullScreenEntries = [RouteEntry(route: .camera)]
        return true
    }

    func accepts(_ workflowID: UUID, step: AppRoute) -> Bool {
        captureWorkflow?.id == workflowID && currentEntry.route == step
    }

    func invalidateCaptureResults() {
        guard captureWorkflow != nil else { return }
        clearSavedResult()
        if currentEntry.route == .camera || currentEntry.route == .crop {
            captureWorkflow?.recognizedText = nil
        }
        if currentEntry.route == .camera { captureWorkflow?.croppedURL = nil }
    }

    /// 上游图或文字一改，上一次保存回执即失效，避免 P21 显示旧归属。
    private func clearCaptureResult() {
        captureWorkflow?.recognizedText = nil
        clearSavedResult()
    }

    private func clearSavedResult() {
        captureWorkflow?.savedUserExampleID = nil
        captureWorkflow?.savedMistakeID = nil
        captureWorkflow?.savedIdioms = []
    }
}
