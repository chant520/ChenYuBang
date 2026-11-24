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
        self.service = service
        self.importResult = importResult
        self.onValidated = onValidated
        self.onBack = onBack
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                Text("备份与恢复")
                    .font(.system(size: Tokens.Font.n20, weight: .semibold))
                    .foregroundStyle(colors.text)
                    .accessibilityAddTraits(.isHeader)
                CButton(text: "返回", variant: .ghost, action: onBack)

                CCard {
                    Text("导出备份")
                        .font(.system(size: Tokens.Font.n15, weight: .semibold))
                        .foregroundStyle(colors.text)
                    Text("包含错题、复习记录、考试范围与录入题；不含成语库和原图。")
                        .font(.system(size: Tokens.Font.n12))
                        .cLineHeight(1.6, size: Tokens.Font.n12)
                        .foregroundStyle(colors.text2)
                        .padding(.top, Tokens.Space.lsubTop)
                    CButton(text: busy ? "正在导出…" : "存到手机本地", loading: busy) { export(share: false) }
                        .padding(.top, Tokens.Space.block)
                    CButton(text: "通过系统分享面板另存到网盘", variant: .ghost, loading: busy) { export(share: true) }
                        .padding(.top, Tokens.Space.block)
                }

                CCard {
                    Text("导入备份")
                        .font(.system(size: Tokens.Font.n15, weight: .semibold))
                        .foregroundStyle(colors.text)
                    Text("从手机本地或网盘选择 backup-YYYYMMDD-HHMMSS.zip。读取成功后再选择合并或覆盖。")
                        .font(.system(size: Tokens.Font.n12))
                        .cLineHeight(1.6, size: Tokens.Font.n12)
                        .foregroundStyle(colors.text2)
                        .padding(.top, Tokens.Space.lsubTop)
                    CButton(text: busy ? "正在读取…" : "从手机本地选文件", loading: busy) { showingImporter = true }
                        .padding(.top, Tokens.Space.block)
                    CButton(text: "从网盘选文件", variant: .ghost, loading: busy) { showingImporter = true }
                        .padding(.top, Tokens.Space.block)
                }

                CCard(soft: true) {
                    Text("全程不登录、不上传、不代传：文件只在你和网盘之间走。")
                        .font(.system(size: Tokens.Font.n12))
                        .cLineHeight(1.6, size: Tokens.Font.n12)
                        .foregroundStyle(colors.text2)
                }

                if let message = status ?? importResult {
                    Text(message)
                        .font(.system(size: Tokens.Font.n13))
                        .foregroundStyle(failed ? colors.errfg : colors.tintfg)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityLabel(message)
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
            case .failure: showFailure("无法读取所选文件，请重新选择。")
            }
        }
        .sheet(item: $shareItem) { item in ActivityView(url: item.url) }
    }

    private func export(share: Bool) {
        guard !busy else { return }
        busy = true
        status = nil
        let directory = share ? FileManager.default.temporaryDirectory : documentsDirectory()
        let task = Task.detached { () -> Result<BackupExport, String> in
            do { return .success(try service.export(to: directory)) } catch { return .failure(userMessage(for: error)) }
        }
        Task { @MainActor in
            let result = await task.value
            busy = false
            switch result {
            case .success(let exported):
                if share {
                    shareItem = ShareItem(url: exported.url)
                    status = "备份已生成，可在系统面板中另存。"
                } else {
                    status = "已存到手机本地：\(exported.url.lastPathComponent)"
                }
                failed = false
            case .failure(let message):
                showFailure(message)
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
            showFailure("无法读取所选文件，请重新选择。")
            return
        }
        let task = Task.detached { () -> Result<ValidatedBackup, String> in
            do { return .success(try service.validate(data)) } catch { return .failure(userMessage(for: error)) }
        }
        Task { @MainActor in
            let result = await task.value
            busy = false
            switch result {
            case .success(let backup):
                guard onValidated(backup) else { showFailure("当前页面无法打开导入方式，请重试。"); return }
            case .failure(let message):
                showFailure(message)
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

    private static func userMessage(for error: Error) -> String {
        switch error {
        case let error as BackupError:
            switch error {
            case .database: return "本机数据库读写失败，请重试。"
            case .fileIO: return "文件读写失败，请检查可用空间后重试。"
            case .format: return "备份文件不完整、版本过新或校验失败。"
            }
        default:
            return "备份处理失败，请重试。"
        }
    }
}

public struct ImportModeSheet: View {
    @Environment(\.colorScheme) private var colorScheme

    private let backup: ValidatedBackup
    private let service: BackupService
    private let inProgress: Bool
    private let error: String?
    private let onStart: () -> Bool
    private let onFinish: (Result<String, String>) -> Void
    private let onCancel: () -> Void

    public init(
        backup: ValidatedBackup,
        service: BackupService,
        inProgress: Bool,
        error: String?,
        onStart: @escaping () -> Bool,
        onFinish: @escaping (Result<String, String>) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.backup = backup
        self.service = service
        self.inProgress = inProgress
        self.error = error
        self.onStart = onStart
        self.onFinish = onFinish
        self.onCancel = onCancel
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            CCard(soft: true) {
                Text("这份备份包含 \(backup.counts.mistakes) 条错题、"
                     + "\(backup.counts.userExamples) 条录入题、\(backup.counts.reviews) 条复习记录。")
                    .font(.system(size: Tokens.Font.n12))
                    .cLineHeight(1.6, size: Tokens.Font.n12)
                    .foregroundStyle(colors.text2)
            }

            CCard {
                HStack {
                    Text("覆盖")
                        .font(.system(size: Tokens.Font.n15, weight: .semibold))
                        .foregroundStyle(colors.text)
                    Spacer()
                    CBadge(text: "会丢现有数据", kind: .error)
                }
                Text("用备份替换本机错题、录入题和复习记录。导入前自动在缓存留底，失败会回滚。")
                    .font(.system(size: Tokens.Font.n12))
                    .cLineHeight(1.6, size: Tokens.Font.n12)
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lsubTop)
                CButton(text: inProgress ? "正在覆盖…" : "覆盖导入", variant: .dangerFilled,
                        loading: inProgress) { importBackup(.replace) }
                    .padding(.top, Tokens.Space.block)
            }

            CCard {
                HStack {
                    Text("合并")
                        .font(.system(size: Tokens.Font.n15, weight: .semibold))
                        .foregroundStyle(colors.text)
                    Spacer()
                    CTag(text: "保留本机", style: .solid)
                }
                Text("同一条记录保留本机版本，其余记录合并进来；不会删除本机数据。")
                    .font(.system(size: Tokens.Font.n12))
                    .cLineHeight(1.6, size: Tokens.Font.n12)
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lsubTop)
                CButton(text: inProgress ? "正在合并…" : "合并导入", loading: inProgress) { importBackup(.merge) }
                    .padding(.top, Tokens.Space.block)
            }

            if let error {
                Text(error)
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(colors.errfg)
                    .accessibilityLabel(error)
            }
            CButton(text: "取消", variant: .ghost, enabled: !inProgress, action: onCancel)
        }
    }

    private func importBackup(_ mode: BackupImportMode) {
        guard onStart() else { return }
        let task = Task.detached { () -> Result<String, String> in
            do {
                let summary = try service.importBackup(backup, mode: mode)
                return .success(summary.message)
            } catch let error as BackupError {
                switch error {
                case .database: return .failure("导入失败，本机数据已回滚，请重试。")
                case .fileIO: return .failure("导入失败，缓存留底或临时文件读写失败。")
                case .format: return .failure("导入失败，备份内容无效。")
                }
            } catch {
                return .failure("导入失败，本机数据已回滚，请重试。")
            }
        }
        Task { @MainActor in onFinish(await task.value) }
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
