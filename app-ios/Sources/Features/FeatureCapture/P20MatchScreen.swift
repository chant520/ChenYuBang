import SwiftUI
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P20 成语匹配与修正（工作包 2.5；信息架构 P20 / 高保真稿 C P20 864–935 行）。
///
/// 识别文字回看（改文字回 P19）+ 多归属勾选（≥1）+ 换/加/删 + 零候选空态与 P04 手动回填，
/// 以及答案 / 错因 / 标签 / 是否做错 / 来源表单。保存由 `UserExampleSaver` 事务落库，
/// 成功后交回 `onSaved` 进 P21；失败留在本页，不清空输入。
/// 手选浮层在 P20 内部复用 P04 搜索内容，不动 router 栈（禁 openSearch）。
public struct P20MatchScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: P20MatchModel

    private let text: String
    private let imageURL: URL
    private let reader: IdiomLibraryReader
    private let saver: UserExampleSaver
    private let onBack: () -> Void
    private let onSaved: (SavedCapture) -> Void

    @MainActor
    public init(
        text: String,
        imageURL: URL,
        reader: IdiomLibraryReader,
        saver: UserExampleSaver,
        onBack: @escaping () -> Void,
        onSaved: @escaping (SavedCapture) -> Void
    ) {
        self.text = text
        self.imageURL = imageURL
        self.reader = reader
        self.saver = saver
        self.onBack = onBack
        self.onSaved = onSaved
        _model = StateObject(wrappedValue: P20MatchModel())
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        let actions = P20MatchActions(
            onEdit: onBack,
            onSelect: model.select,
            onCycle: model.cycle,
            onAdd: model.add,
            onToggleDelete: model.toggleDeleteMode,
            onDelete: model.remove,
            onOpenManual: { model.setManualPicker(visible: true) },
            onDismissManual: { model.setManualPicker(visible: false) },
            onManualPick: model.pickManual,
            onSave: save,
            onDismissInfo: model.dismissInfo,
            form: P20FormActions(
                onAnswer: model.setAnswerText,
                onReason: model.setReason,
                onAddTag: model.addTag,
                onRemoveTag: model.removeTag,
                onToggleMistake: model.toggleMistake,
                onExamType: model.setExamTypeText,
                onYear: model.setYearText,
                onDismissSaveError: model.dismissSaveError
            ),
            manualPicker: { onPick, onDismiss in
                AnyView(
                    P04SearchScreen(reader: reader, onClose: onDismiss, onOpenIdiom: onPick)
                )
            }
        )
        P20MatchContent(state: model.state, actions: actions, colors: colors)
            .task {
                let library = reader
                let matches = await Task.detached(priority: .userInitiated) {
                    (try? library.allIdiomsForMatch())?.map(MatchIdiom.init(record:))
                }.value
                await MainActor.run {
                    model.bootstrap(text: text, idioms: matches ?? [])
                    if matches == nil { model.showInfo(P20Matcher.readFailed) }
                }
            }
    }

    /// 本地校验先行：至少一条归属、原图仍在、题干非空；失败留在本页并给可操作提示。
    private func save() {
        let state = model.state
        guard state.hasChecked else {
            model.showInfo(P20Matcher.atLeastOne)
            return
        }
        guard FileManager.default.fileExists(atPath: imageURL.path) else {
            model.showInfo(P20Matcher.noImage)
            return
        }
        let layout = OcrTextLayout.classify(text.components(separatedBy: .newlines))
        let stem = layout.stem.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !stem.isEmpty else {
            model.showInfo(P20Matcher.emptyStem)
            return
        }
        let form = state.form
        let input = CaptureSaveInput(
            imageURL: imageURL,
            ocrText: text,
            stem: stem,
            optionA: option(layout.options, "A"),
            optionB: option(layout.options, "B"),
            optionC: option(layout.options, "C"),
            optionD: option(layout.options, "D"),
            myAnswer: form.answer,
            isMistake: form.isMistake,
            reason: form.isMistake ? form.reason.trimmingCharacters(in: .whitespacesAndNewlines) : "",
            tagNames: form.isMistake ? form.tags : [],
            examType: form.examType,
            year: form.year,
            idioms: state.checked.map { SavedIdiom(id: $0.id, word: $0.word) }
        )
        model.beginSave()
        let saver = self.saver
        let onSaved = self.onSaved
        Task {
            let saved = await Task.detached(priority: .userInitiated) {
                try? saver.save(input)
            }.value
            if let saved {
                model.finishSave()
                onSaved(saved)
            } else {
                model.failSave()
            }
        }
    }

    private func option(_ options: [String: String], _ key: String) -> String? {
        let value = options[key]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? nil : value
    }
}

private extension MatchIdiom {
    init(record: IdiomRecord) {
        self.init(
            id: record.id,
            word: record.word,
            synonyms: record.synonyms,
            frequency: record.frequency
        )
    }
}

