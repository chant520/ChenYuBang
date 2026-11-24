import CangyuData
import SwiftUI

struct BackupOverlayContent: View {
    @ObservedObject var router: RouterState
    @ObservedObject var launch: FirstLaunchState
    let route: AppRoute

    var body: some View {
        if route == .importMode {
            if let service = launch.backupService, let backup = router.pendingBackup {
                ImportModeSheet(
                    backup: backup,
                    service: service,
                    inProgress: router.backupImportInProgress,
                    error: router.backupImportError,
                    onStart: router.beginBackupImport,
                    onFinish: { result in
                        switch result {
                        case .success(let message):
                            do {
                                try launch.refreshAfterBackupImport()
                                router.finishBackupImport(result: .success(message))
                            } catch {
                                router.finishBackupImport(result: .success(
                                    Phase4Copy.refreshFailure
                                ))
                            }
                        case .failure(let error):
                            router.finishBackupImport(result: .failure(error))
                        }
                    }
                )
            } else {
                PendingPageText(text: "未读到待导入备份，请返回后重新选择文件。")
            }
        } else if let service = launch.dataCleaner {
            ClearDataSheet(
                service: service,
                inProgress: router.clearInProgress,
                error: router.clearError,
                onStart: router.beginClearData,
                onFinish: router.finishClearData
            )
        } else {
            PendingPageText(text: "本机数据库尚未就绪，无法清空数据。")
        }
    }
}
