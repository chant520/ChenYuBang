import Combine
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
    var viewState = RouteViewState()
}

enum ReviewSource: Equatable {
    case global
    case idiom(String)
}

struct ReviewSession: Equatable {
    let source: ReviewSource
    let tab: AppTab
    let originEntryID: UUID
}

struct CaptureWorkflow: Equatable, Identifiable {
    let id = UUID()
    let tab: AppTab
    let originEntryID: UUID
    var imageURL: URL?
    var croppedURL: URL?
    var recognizedText: String?
    var draftID: String?
    var savedMistakeID: String?
}

/// iOS 15 的显式 entry 栈；页面状态跟随 UUID，不按路由键去重。
final class RouterState: ObservableObject {
    @Published private(set) var selectedTab: AppTab = .idiom
    @Published private(set) var tabEntries: [AppTab: [RouteEntry]]
    @Published private(set) var fullScreenEntries: [RouteEntry] = []
    @Published private(set) var overlay: RouteEntry?
    @Published private(set) var reviewSession: ReviewSession?
    @Published private(set) var captureWorkflow: CaptureWorkflow?
    @Published private(set) var needsFirstLaunch: Bool
    @Published private(set) var topRequests: [AppTab: UUID] = [:]
    private let onboardingEntry = RouteEntry(route: .onboarding)
    @Published private(set) var suspendedFullScreenEntries: [RouteEntry] = []

    init(needsFirstLaunch: Bool = false) {
        self.needsFirstLaunch = needsFirstLaunch
        tabEntries = Dictionary(uniqueKeysWithValues: AppTab.allCases.map {
            ($0, [RouteEntry(route: $0.root)])
        })
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
        if tab == selectedTab { resetToRoot(tab) } else { selectedTab = tab }
    }

    func consumeTopRequest(for tab: AppTab, token: UUID) {
        guard topRequests[tab] == token else { return }
        topRequests.removeValue(forKey: tab)
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

    func closeOverlay() { overlay = nil }

    @discardableResult
    func beginReview(source: ReviewSource, queueIDs: [String]) -> Bool {
        guard canStartWorkflow, validQueue(queueIDs) else { return false }
        switch source {
        case .global:
            guard currentEntry.route == .mistakes else { return false }
        case .idiom(let idiomID):
            guard currentEntry.route == .idiomDetail, currentEntry.recordID == idiomID else { return false }
        }
        reviewSession = ReviewSession(source: source, tab: selectedTab, originEntryID: currentEntry.id)
        suspendedFullScreenEntries = fullScreenEntries
        fullScreenEntries = [RouteEntry(route: .queue, queueIDs: queueIDs)]
        return true
    }

    /// 仅由真实复习业务完成回调调用；不在占位页制造成绩。
    @discardableResult
    func finishReview(queueEntryID: UUID) -> Bool {
        guard reviewSession != nil, currentEntry.route == .queue, currentEntry.id == queueEntryID else { return false }
        fullScreenEntries[fullScreenEntries.count - 1] = RouteEntry(route: .summary, queueIDs: currentEntry.queueIDs)
        return true
    }

    @discardableResult
    func retryReview(queueIDs: [String]) -> Bool {
        guard reviewSession != nil, currentEntry.route == .summary, validQueue(queueIDs) else { return false }
        fullScreenEntries.append(RouteEntry(route: .queue, queueIDs: queueIDs))
        return true
    }

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
        captureWorkflow?.recognizedText = nil
        captureWorkflow?.draftID = nil
        fullScreenEntries.append(RouteEntry(route: .crop))
        return true
    }

    @discardableResult
    func cropped(workflowID: UUID, imageURL: URL) -> Bool {
        guard accepts(workflowID, step: .crop) else { return false }
        captureWorkflow?.croppedURL = imageURL
        captureWorkflow?.recognizedText = nil
        captureWorkflow?.draftID = nil
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

    /// 匹配业务保留真实草稿引用；保存成功回调之前仍停留在 Match。
    @discardableResult
    func matched(workflowID: UUID, draftID: String) -> Bool {
        guard accepts(workflowID, step: .match), validID(draftID) else { return false }
        captureWorkflow?.draftID = draftID
        return true
    }

    @discardableResult
    func saved(workflowID: UUID, mistakeID: String) -> Bool {
        guard accepts(workflowID, step: .match), captureWorkflow?.draftID != nil, validID(mistakeID)
        else { return false }
        captureWorkflow?.savedMistakeID = mistakeID
        fullScreenEntries.append(RouteEntry(route: .done, recordID: mistakeID))
        return true
    }

    func cancelCapture() {
        guard captureWorkflow != nil else { return }
        dismissFullScreen()
    }

    @discardableResult
    func finishCapture() -> Bool {
        guard currentEntry.route == .done, captureWorkflow?.savedMistakeID != nil else { return false }
        dismissFullScreen()
        fullScreenEntries = []
        selectedTab = .mistake
        resetToRoot(.mistake)
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

    private var canStartWorkflow: Bool {
        !needsFirstLaunch && overlay == nil && reviewSession == nil && captureWorkflow == nil
    }

    private func resetToRoot(_ tab: AppTab) {
        guard var root = entries(for: tab).first else { return }
        root.viewState.scrollAnchor = nil
        tabEntries[tab] = [root]
        topRequests[tab] = UUID()
    }

    private func validID(_ value: String?) -> Bool {
        guard let value else { return false }
        return !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func validQueue(_ values: [String]) -> Bool {
        !values.isEmpty && values.allSatisfy { validID($0) }
    }

    private func accepts(_ workflowID: UUID, step: AppRoute) -> Bool {
        captureWorkflow?.id == workflowID && currentEntry.route == step
    }

    private func invalidateCaptureResults() {
        guard captureWorkflow != nil else { return }
        captureWorkflow?.savedMistakeID = nil
        captureWorkflow?.draftID = nil
        if currentEntry.route == .camera || currentEntry.route == .crop {
            captureWorkflow?.recognizedText = nil
        }
        if currentEntry.route == .camera { captureWorkflow?.croppedURL = nil }
    }
}
