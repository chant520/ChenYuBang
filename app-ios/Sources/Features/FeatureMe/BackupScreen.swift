import CangyuData
import CangyuDesignSystem
import CangyuTokens
import SwiftUI
import UIKit
import UniformTypeIdentifiers

public struct BackupScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var busy = false
    @State private var status: String?
    @State private var failed = false
    @State private var showingImporter = false
    @State private var shareItem: ShareItem?

    private let service: BackupService
    private let importResult: String?
    private let onValidated: (ValidatedBackup) -> Bool
    private let onBack: () -> Void

    public init(
        service: BackupService,
        importResult: String?,
        onValidated: @escaping (ValidatedBackup) -> Bool,
        onBack: @escaping () -> Void
    ) {
        self.init(service: service, importResult: importResult, onValidated: onValidated, onBack: onBack)
    }

    init(
        service: BackupService,
        importResult: String?,
        onValidated: @escaping (ValidatedBackup) -> Bool,
        onBack: @escaping () -> Void,
        initialStatus: String? = nil,
        initialFailed: Bool = false
    ) {
        self.service = service
        self.importResult = importResult
        self.onValidated = onValidated
        self.onBack = onBack
        _status = State(initialValue: initialStatus)
        _failed = State(initialValue: initialFailed)
    }

    nonisolated static func exportMessage(_ exported: BackupExport, share: Bool) -> String {
        "错题 \(exported.counts.mistakes) · 录入题 \(exported.counts.userExamples) · "
            + "复习 \(exported.counts.reviews) · 学习状态 \(exported.counts.idiomStates) · "
            + "考试范围 \(exported.counts.scopes)"
            + (share ? "。可在系统面板中另存。" : "。已存到手机本地：\(exported.url.lastPathComponent)")
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                HStack(spacing: Tokens.Space.lt1Gap) {
                    Button(action: onBack) {
                        CIcon(.back, size: Tokens.Icon.n21, strokeWidth: 1.8, color: colors.text)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("返回")
                    Text("备份与恢复")
                        .font(.system(size: Tokens.Font.n20, weight: .semibold))
                        .foregroundStyle(colors.text)
                        .accessibilityAddTraits(.isHeader)
                }

                CCard {
                    CLabel("导出备份")
                    Text("导出的内容：错题、录入题、收藏、学习状态。不含题库本身。")
                        .font(.system(size: Tokens.Font.n13))
                        .cLineHeight(1.7, size: Tokens.Font.n13)
                        .foregroundStyle(colors.text2)
                        .padding(.top, Tokens.Space.lsubTop)
                    Phase4MenuRow(title: "存到手机本地", action: { export(share: false) },
                                  showsDivider: true, disabled: busy)
                    Phase4MenuRow(title: "通过系统文件 / 分享面板另存到网盘",
                                  action: { export(share: true) }, showsDivider: false, disabled: busy)
                }

                CCard {
                    CLabel("导入备份")
                    Text("读入前会先问你：覆盖还是合并（见 P22）。")
                        .font(.system(size: Tokens.Font.n13))
                        .cLineHeight(1.7, size: Tokens.Font.n13)
                        .foregroundStyle(colors.text2)
                        .padding(.top, Tokens.Space.lsubTop)
                    Phase4MenuRow(title: "从手机本地选文件", action: { showingImporter = true },
                                  showsDivider: true, disabled: busy)
                    Phase4MenuRow(title: "从网盘选文件", action: { showingImporter = true },
                                  showsDivider: false, disabled: busy)
                }

                CCard(soft: true) {
                    HStack {
                        Text("最近一次备份时间")
                            .font(.system(size: Tokens.Font.n13, weight: .semibold))
                            .foregroundStyle(colors.text)
                        Spacer()
                        CTag(text: "进阶位", style: .disabled)
                    }
                    Text(Phase4Copy.lastBackup)
                        .font(.system(size: Tokens.Font.n115))
                        .foregroundStyle(colors.text3)
                        .padding(.top, Tokens.Space.lsubTop)
                }

                Text("全程不登录、不上传、不代传：文件只在你和网盘之间走。")
                    .font(.system(size: Tokens.Font.n115))
                    .cLineHeight(1.65, size: Tokens.Font.n115)
                    .foregroundStyle(colors.text3)

                if let message = status ?? importResult {
                    let successTitle = status == nil ? Phase4Copy.importSuccess : Phase4Copy.exportSuccess
                    CBar(kind: failed ? .error : .info,
                         title: failed ? message : successTitle,
                         subtitle: failed ? nil : message)
                }
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.contentTop)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
        .fileImporter(isPresented: $showingImporter, allowedContentTypes: [.zip]) { result in
            switch result {
            case .success(let url): loadBackup(url)
            case .failure(let error) where (error as? CocoaError)?.code == .userCancelled:
                break
            case .failure:
                showFailure(Phase4Copy.importFailure)
            }
        }
        .sheet(item: $shareItem) { item in ActivityView(url: item.url) }
    }

    private func export(share: Bool) {
        guard !busy else { return }
        busy = true
        status = nil
        let service = self.service
        let directory = share ? FileManager.default.temporaryDirectory : documentsDirectory()
        let task = Task.detached { () -> Result<BackupExport, Phase4MessageError> in
            do {
                return .success(try service.export(to: directory))
            } catch {
                return .failure(Phase4MessageError(message: Phase4Copy.exportFailure))
            }
        }
        Task { @MainActor in
            let result = await task.value
            busy = false
            switch result {
            case .success(let exported):
                if share {
                    shareItem = ShareItem(url: exported.url)
                }
                status = Self.exportMessage(exported, share: share)
                failed = false
            case .failure(let error):
                showFailure(error.message)
            }
        }
    }

    private func loadBackup(_ url: URL) {
        guard !busy else { return }
        busy = true
        status = nil
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let data: Data
        do {
            data = try Data(contentsOf: url)
        } catch {
            busy = false
            showFailure(Phase4Copy.backupUnreadable)
            return
        }
        let service = self.service
        let task = Task.detached { () -> Result<ValidatedBackup, Phase4MessageError> in
            do { return .success(try service.validate(data)) } catch {
                return .failure(Phase4MessageError(message: backupImportUserMessage(for: error)))
            }
        }
        Task { @MainActor in
            let result = await task.value
            busy = false
            switch result {
            case .success(let backup):
                guard onValidated(backup) else {
                    showFailure(Phase4Copy.importFailure)
                    return
                }
            case .failure(let error):
                showFailure(error.message)
            }
        }
    }

    private func showFailure(_ message: String) {
        status = message
        failed = true
    }

    private func documentsDirectory() -> URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
    }

}

private func backupImportUserMessage(for error: Error) -> String {
    if case BackupError.format = error { return Phase4Copy.backupUnreadable }
    return Phase4Copy.importFailure
}

public struct ImportModeSheet: View {
    @Environment(\.colorScheme) private var colorScheme

    private let backup: ValidatedBackup
    private let service: BackupService
    private let inProgress: Bool
    private let error: String?
    private let onStart: () -> Bool
    private let onFinish: (Result<String, Phase4MessageError>) -> Void

    public init(
        backup: ValidatedBackup,
        service: BackupService,
        inProgress: Bool,
        error: String?,
        onStart: @escaping () -> Bool,
        onFinish: @escaping (Result<String, Phase4MessageError>) -> Void
    ) {
        self.backup = backup
        self.service = service
        self.inProgress = inProgress
        self.error = error
        self.onStart = onStart
        self.onFinish = onFinish
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            Button { importBackup(.replace) } label: {
                CCard(soft: true) {
                    HStack {
                        Text("覆盖")
                            .font(.system(size: Tokens.Font.n13, weight: .semibold))
                            .foregroundStyle(colors.text)
                        Spacer()
                        CBadge(text: "会丢现有数据", kind: .error)
                    }
                    Text("用备份文件替换本机现有记录。本机现在的错题、录入题、收藏、学习状态都会被清掉，之后无法找回。")
                        .font(.system(size: Tokens.Font.n13))
                        .cLineHeight(1.7, size: Tokens.Font.n13)
                        .foregroundStyle(colors.text2)
                        .padding(.top, Tokens.Space.lsubTop)
                }
            }
            .buttonStyle(CPressDimStyle())
            .disabled(inProgress)

            Button { importBackup(.merge) } label: {
                CCard(soft: true) {
                    HStack {
                        Text("合并")
                            .font(.system(size: Tokens.Font.n13, weight: .semibold))
                            .foregroundStyle(colors.text)
                        Spacer()
                        CTag(text: "保留本机", style: .solid)
                    }
                    Text("把文件里的记录并入本机，同一道题以本机记录为准，重复的不会叠加。")
                        .font(.system(size: Tokens.Font.n13))
                        .cLineHeight(1.7, size: Tokens.Font.n13)
                        .foregroundStyle(colors.text2)
                        .padding(.top, Tokens.Space.lsubTop)
                }
            }
            .buttonStyle(CPressDimStyle())
            .disabled(inProgress)

            if inProgress {
                CBar(kind: .info, title: Phase4Copy.importBusy)
            }
            if let error {
                CBar(kind: .error, title: error)
            }
        }
        .accessibilityLabel("这份备份包含 \(backup.counts.mistakes) 条错题、"
            + "\(backup.counts.userExamples) 条录入题、\(backup.counts.reviews) 条复习记录")
    }

    private func importBackup(_ mode: BackupImportMode) {
        guard onStart() else { return }
        let service = self.service
        let backup = self.backup
        let task = Task.detached { () -> Result<String, Phase4MessageError> in
            do {
                let summary = try service.importBackup(backup, mode: mode)
                return .success(summary.message)
            } catch let error as BackupError {
                switch error {
                case .database: return .failure(Phase4MessageError(message: Phase4Copy.importFailure))
                case .fileIO: return .failure(Phase4MessageError(message: Phase4Copy.importFailure))
                case .format: return .failure(Phase4MessageError(message: Phase4Copy.importFailure))
                }
            } catch {
                return .failure(Phase4MessageError(message: Phase4Copy.importFailure))
            }
        }
        Task { @MainActor in onFinish(await task.value) }
    }
}

private struct Phase4MenuRow: View {
    @Environment(\.colorScheme) private var colorScheme
    let title: String
    let action: () -> Void
    let showsDivider: Bool
    var disabled = false

    var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        Button(action: action) {
            HStack {
                Text(title)
                    .font(.system(size: Tokens.Font.n14, weight: .semibold))
                    .foregroundStyle(colors.text)
                    .multilineTextAlignment(.leading)
                Spacer()
                CIcon(.chevron, size: Tokens.Icon.n15, strokeWidth: 1.8, color: colors.accent)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, Tokens.Space.lrowY)
            .contentShape(Rectangle())
            if showsDivider {
                Rectangle().fill(colors.line2).frame(height: 1)
            }
        }
        .buttonStyle(CPressDimStyle())
        .disabled(disabled)
    }
}

private struct ShareItem: Identifiable {
    let id = UUID()
    let url: URL
}

private struct ActivityView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
