import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens
import GRDB

/// 本机库的真实初始化及范围持久化；仅保存成功后发布新 preferences。
@MainActor
final class FirstLaunchState: ObservableObject {
    @Published private(set) var preferences: LaunchPreferences?
    @Published private(set) var initializationError: String?
    @Published private(set) var availableProvinces: [String] = []
    @Published private(set) var contentError = false
    /// P03 读库入口：初始化成功后才有值，页面据此决定是否可浏览。
    @Published private(set) var libraryReader: IdiomLibraryReader?
    /// P07 例题详情读库入口：与 libraryReader 同时就绪。
    @Published private(set) var exampleReader: ExampleLibraryReader?
    /// P20 保存入口：初始化成功后才有值；图片目录为 Application Support/ChenYuBang/images。
    @Published private(set) var captureSaver: UserExampleSaver?
    /// P08 / P09 错题与录入题读库入口：与 libraryReader 同时就绪。
    @Published private(set) var mistakeReader: MistakeLibraryReader?
    /// P10 编辑与删除写库入口（工作包 2.8）：与 captureSaver 同时就绪，共用 images 目录。
    @Published private(set) var userExampleEditor: UserExampleEditor?
    /// P11 / P12 复习队列读库与成绩落库入口（阶段 3）：与 mistakeReader 同时就绪。
    @Published private(set) var reviewReader: ReviewLibraryService?
    /// P15 / P22 备份导出与恢复入口（阶段 4）：与 mistakeReader 共用同一数据库队列。
    @Published private(set) var backupService: BackupService?
    @Published private(set) var dataCleaner: UserDataCleaner?
    @Published private(set) var currentPack: ValidatedPack?
    /// P10 原图位的解析基准：Application Support/ChenYuBang，库内存的是相对路径 `images/<id>.jpg`。
    @Published private(set) var dataDirectory: URL?
    private var store: ExamScopeStore?
    private var importer: PackImporter?

    func initialize() {
        guard preferences == nil else { return }
        initializationError = nil
        contentError = false
        do {
            let support = try FileManager.default.url(
                for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true
            )
            let directory = support.appendingPathComponent("ChenYuBang", isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let imagesDirectory = directory.appendingPathComponent("images", isDirectory: true)
            try FileManager.default.createDirectory(at: imagesDirectory, withIntermediateDirectories: true)
            let queue = try DatabaseQueue(path: directory.appendingPathComponent("chenyubang.sqlite").path)
            try ChenYuBangDatabase.makeMigrator().migrate(queue)
            let initializedImporter = PackImporter(queue: queue)
            guard let packageURL = Bundle.main.url(forResource: "pack-v1.0.0", withExtension: "zip") else {
                throw PackError.archive("Missing built-in pack")
            }
            let pack = try initializedImporter.validate(Data(contentsOf: packageURL))
            _ = try initializedImporter.importPack(pack)
            let initializedStore = ExamScopeStore(queue: queue)
            let loaded = try initializedStore.load()
            let provinces = try initializedStore.availableProvinces()
            if !loaded.complete {
                // P01 首次启动即按产品默认写入 exam_scope（国考 + 省考 + 事业编，省考省份默认全选）；
                // 库中已有范围行时 loaded.scope 就是既有值，原样写回，不覆盖用户设置。
                var scope = loaded.scope
                if scope == ExamScopeSelection() { scope.provinces = Set(provinces) }
                try initializedStore.save(scope: scope, complete: false)
                try initializedImporter.refreshScopeLinks(pack)
            }
            store = initializedStore
            importer = initializedImporter
            currentPack = pack
            availableProvinces = provinces
            libraryReader = IdiomLibraryReader(queue: queue)
            exampleReader = ExampleLibraryReader(queue: queue)
            captureSaver = UserExampleSaver(queue: queue, imagesDirectory: imagesDirectory)
            userExampleEditor = UserExampleEditor(queue: queue, dataDirectory: directory)
            mistakeReader = MistakeLibraryReader(queue: queue)
            reviewReader = ReviewLibraryService(queue: queue)
            backupService = BackupService(queue: queue)
            dataCleaner = UserDataCleaner(queue: queue)
            dataDirectory = directory
            preferences = loaded
        } catch is PackError {
            contentError = true
            initializationError = "内置题库初始化失败，请重试；联网获取尚未接入。"
        } catch {
            initializationError = "本机数据初始化失败，请重试。"
        }
    }

    func save(scope: ExamScopeSelection, complete: Bool) throws {
        guard let store, let importer, let currentPack else { throw LaunchError.notInitialized }
        try store.save(scope: scope, complete: complete)
        try importer.refreshScopeLinks(currentPack)
        preferences = LaunchPreferences(complete: complete || preferences?.complete == true, scope: scope)
    }

    func refreshAfterBackupImport() throws {
        guard let store, let importer, let currentPack else { throw LaunchError.notInitialized }
        try importer.refreshScopeLinks(currentPack)
        let loaded = try store.load()
        let provinces = try store.availableProvinces()
        preferences = loaded
        availableProvinces = provinces
    }

    private enum LaunchError: Error { case notInitialized }
}

/// P01 启动页（首次引导）。与 P14 设置页共用 `FeatureMe` 的同一控件、同一份数据
/// （信息架构 v1.4 决策 F / 1.9）：省考勾选且省份默认全选，省份仅为筛选维度；
/// 「进入」与「跳过」同效，都写 `exam_scope` 后进 P02。
struct FirstLaunchGate: View {
    @ObservedObject var launch: FirstLaunchState
    @ObservedObject var router: RouterState

    var body: some View {
        if let preferences = launch.preferences {
            ExamScopeScreen(
                mode: .onboarding,
                initial: preferences.scope,
                availableProvinces: launch.availableProvinces,
                onSave: { scope, complete in
                    try launch.save(scope: scope, complete: complete)
                    router.completeFirstLaunch()
                }
            )
        }
    }
}
