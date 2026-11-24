public struct Phase4MessageError: Error, Sendable {
    let message: String
}

enum Phase4Copy {
    static let exportSuccess = "导出完成"
    static let importSuccess = "导入完成"
    static let clearSuccess = "学习数据已清空"
    static let exportFailure = "导出失败，请重试"
    static let backupUnreadable = "备份无法读取，请更换文件后重试"
    static let importFailure = "导入失败，请检查备份文件后重试"
    static let importBusy = "正在导入"
    static let clearFailure = "清空失败，本机数据未变更"
    static let clearBusy = "正在清空"
    static let refreshFailure = "导入已完成，但考试范围刷新失败，请重启 App。"
    static let lastBackup = "暂无备份记录"
    static let checkingUpdate = "正在检查题库更新"
    static func downloadingUpdate(_ progress: Double) -> String {
        "正在下载题库更新（\(Int((progress * 100).rounded()))%）"
    }
    static let updateComplete = "题库已更新"
    static let continueOffline = "继续离线使用"
    static let testVersion = "测试数据（TEST）"
    static let offlineNotice = "题库已内置，可离线使用。"
}
