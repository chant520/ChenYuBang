struct Phase4MessageError: Error, Sendable {
    let message: String
}

// 高保真稿未给出的阶段 4 文案；收口前必须由用户逐字拍板后替换。
enum Phase4PendingCopy {
    static let exportSuccess = "导出完成（待拍板）"
    static let importSuccess = "导入完成（待拍板）"
    static let clearSuccess = "学习数据已清空（待拍板）"
    static let exportFailure = "导出失败（待拍板），请重试。"
    static let importFailure = "导入失败（待拍板），请检查备份文件后重试。"
    static let importStartFailure = "当前页面无法开始导入（待拍板），请重试。"
    static let importBusy = "正在导入（待拍板）"
    static let clearFailure = "清空失败（待拍板），本机数据未变更。"
    static let clearBusy = "正在清空（待拍板）"
    static let refreshFailure = "导入已完成，但考试范围刷新失败（待拍板），请重启 App。"
    static let lastBackup = "时间待记录（待拍板）"
    static let offlineNotice = "联网检查将在阶段 5 接入（待拍板）"
}
