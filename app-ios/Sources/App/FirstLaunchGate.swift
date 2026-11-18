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
    private var store: ExamScopeStore?
    private var importer: PackImporter?
    private var currentPack: ValidatedPack?

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
            store = initializedStore
            importer = initializedImporter
            currentPack = pack
            availableProvinces = provinces
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

    private enum LaunchError: Error { case notInitialized }
}

struct FirstLaunchGate: View {
    @ObservedObject var launch: FirstLaunchState
    @ObservedObject var router: RouterState

    var body: some View {
        if let preferences = launch.preferences {
            ExamScopeEditor(initial: preferences.scope, availableProvinces: launch.availableProvinces,
                            firstLaunch: true, onSave: { scope in
                try launch.save(scope: scope, complete: true)
                router.completeFirstLaunch()
            })
        }
    }
}

/// P01/P14 同一个编辑器；进入与跳过均提交当前选择，失败不重置。
struct ExamScopeEditor: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var selection: ExamScopeSelection
    @State private var showsProvinces = false
    @State private var saving = false
    @State private var errorMessage: String?
    private let firstLaunch: Bool
    private let onSave: (ExamScopeSelection) throws -> Void
    private let onCancel: (() -> Void)?
    private let provinces: [String]

    init(
        initial: ExamScopeSelection,
        availableProvinces: [String],
        firstLaunch: Bool,
        onSave: @escaping (ExamScopeSelection) throws -> Void,
        onCancel: (() -> Void)? = nil
    ) {
        _selection = State(initialValue: initial)
        self.firstLaunch = firstLaunch
        self.provinces = availableProvinces
        self.onSave = onSave
        self.onCancel = onCancel
    }

    var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                Text(firstLaunch ? "考试范围" : "考试范围设置")
                    .font(.system(size: Tokens.Font.n20, weight: .semibold))
                    .foregroundStyle(colors.text)
                    .accessibilityAddTraits(.isHeader)
                Text("选择适合你的考试范围，也可以全部取消。")
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(colors.text2)
                scopeRows
                if selection.provincial {
                    CButton(text: showsProvinces ? "收起省份" : "选择省份（可多选）", variant: .ghost, enabled: !saving) {
                        showsProvinces.toggle()
                    }
                    if showsProvinces { provinceRows }
                }
                Text(selection.description)
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(colors.text2)
                if let errorMessage {
                    Text(errorMessage)
                        .font(.system(size: Tokens.Font.n13))
                        .foregroundStyle(colors.errfg)
                        .accessibilityLabel(errorMessage)
                }
                CButton(text: saving ? "正在保存…" : (firstLaunch ? "进入" : "保存"), loading: saving, action: save)
                if firstLaunch {
                    CButton(text: "跳过", variant: .ghost, enabled: !saving, action: save)
                } else if let onCancel {
                    CButton(text: "取消", variant: .ghost, enabled: !saving, action: onCancel)
                }
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.contentTop)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
    }

    private var scopeRows: some View {
        VStack(spacing: 0) {
            CCheckRow(label: "国考", isOn: $selection.national, enabled: !saving)
            CCheckRow(label: "省考", isOn: $selection.provincial, enabled: !saving,
                      warningText: selection.provinceMissing ? "未选省份" : nil)
            CCheckRow(label: "事业编", isOn: $selection.institution, enabled: !saving)
            CCheckRow(label: "教师编", isOn: .constant(false), enabled: false,
                      trailingText: "暂不支持", showsDivider: false)
        }
    }

    private var provinceRows: some View {
        VStack(spacing: 0) {
            ForEach(provinces, id: \.self) { province in
                CCheckRow(label: province, isOn: Binding(
                    get: { selection.provinces.contains(province) },
                    set: { selected in
                        if selected {
                            selection.provinces.insert(province)
                        } else {
                            selection.provinces.remove(province)
                        }
                    }
                ), enabled: !saving, showsDivider: province != provinces.last)
            }
        }
    }

    private func save() {
        guard !saving else { return }
        saving = true
        errorMessage = nil
        defer { saving = false }
        do {
            try onSave(selection)
        } catch {
            errorMessage = "保存失败，请重试。当前选择已保留。"
        }
    }
}
