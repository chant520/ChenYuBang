import SwiftUI
import UIKit
import CangyuData

/// 后台解码结果装箱（`UIImage` 生成后只读，不入 UI，同 `CropSource` 口径）。
private struct DecodedOriginal: @unchecked Sendable {
    let image: UIImage?
}

/// P10 页面状态与行为（读库失败＝橙条可重试，与 P08 / P09 同一口径）。
///
/// 2.8 起还管编辑态草稿与删除：写库走注入的 `UserExampleWriting`，成功后经
/// `onContentChanged` 让来源页（P05 / P08 / P09）刷新，删除另经 `onDeleted` 关闭本页。
@MainActor
public final class UserExampleViewModel: ObservableObject {
    @Published public private(set) var state = UserExampleState()
    /// ② 原图解码结果；与 `state.imageUnreadable` 合起来决定图片位形态。
    @Published public private(set) var image: UIImage?

    private let id: String
    private let reader: UserExampleReading
    private let editor: UserExampleWriting
    /// 掌握态独立写口子；按详情返回的最新 mistake.id 精确更新。
    private let stateWriter: MistakeStateWriting
    /// 手选归属回显用（按 id 取词形）与 P04 浮层同一读库入口。
    private let libraryReader: IdiomLibraryReader
    /// 本机数据根目录（`images/…` 相对路径的解析基准）；nil＝不尝试读图。
    private let dataDirectory: URL?
    /// 用户内容发生变化（保存成功 / 删除成功）；app 层据此刷新来源页列表。
    private let onContentChanged: () -> Void
    /// 删除成功后关闭本页、回来源页。
    private let onDeleted: () -> Void

    public init(
        id: String,
        reader: UserExampleReading,
        editor: UserExampleWriting,
        stateWriter: MistakeStateWriting,
        libraryReader: IdiomLibraryReader,
        dataDirectory: URL?,
        onContentChanged: @escaping () -> Void = {},
        onDeleted: @escaping () -> Void = {}
    ) {
        self.id = id
        self.reader = reader
        self.editor = editor
        self.stateWriter = stateWriter
        self.libraryReader = libraryReader
        self.dataDirectory = dataDirectory
        self.onContentChanged = onContentChanged
        self.onDeleted = onDeleted
    }

    public func load() async {
        state.loading = true
        state.failed = false
        state.issue = nil
        // 重新读取一律回只读态：草稿是内存态，不跨读库存活，避免拿着旧 detail 继续编。
        state.draft = nil
        state.showsIdiomPicker = false
        state.confirmDelete = false
        do {
            guard let detail = try reader.userExample(id: id) else {
                state.missing = true
                state.detail = nil
                image = nil
                state.loading = false
                return
            }
            state.missing = false
            state.detail = detail
            state.loading = false
            await loadImage(from: detail.imagePath)
        } catch {
            state.detail = nil
            state.loading = false
            state.failed = true
        }
    }

    /// 读图在后台队列解码，失败（文件丢失或解不出）只影响图片位，不影响题干与重做。
    private func loadImage(from relativePath: String?) async {
        image = nil
        state.imageUnreadable = false
        guard let relativePath, let dataDirectory else {
            state.imageUnreadable = relativePath != nil
            return
        }
        let url = dataDirectory.appendingPathComponent(relativePath)
        // UIImage 非 Sendable，与 P18 `CropSource` 同一口径：后台解码、装箱回主线程。
        let decoded = await Task.detached(priority: .userInitiated) {
            DecodedOriginal(image: UIImage(contentsOfFile: url.path))
        }.value
        image = decoded.image
        state.imageUnreadable = decoded.image == nil
    }

    public func toggleAnswer() {
        guard state.canToggleMask, !state.updatingReviewState else { return }
        state.revealed.toggle()
    }

    /// 重做入口只在非重做态显示；进入后清掉本轮选择，遮住首次作答。
    public func startReroll() {
        guard state.isMistake, !state.rerolling, !state.editing, !state.updatingReviewState else { return }
        state.rerolling = true
        state.picked = nil
        state.checked = false
    }

    public func pick(_ index: Int) {
        guard state.rerolling, !state.updatingReviewState, (0..<4).contains(index) else { return }
        state.picked = index
        state.checked = false
    }

    public func check() {
        guard state.picked != nil, !state.updatingReviewState else { return }
        state.checked = true
    }

    public func exitReroll() {
        guard !state.updatingReviewState else { return }
        state.rerolling = false
        state.picked = nil
        state.checked = false
    }

    // MARK: - 手动掌握态（C9）

    /// 只写最新 mistake.id 的 state；成功（含重复写）后刷新 P10 并通知来源页重载。
    public func setReviewState(_ reviewState: MistakeReviewState) async {
        guard state.canChangeReviewState, let mistakeID = state.detail?.mistakeID else { return }
        state.issue = nil
        state.updatingReviewState = true
        defer { state.updatingReviewState = false }
        do {
            let result = try await stateWriter.setReviewState(mistakeID: mistakeID, state: reviewState)
            guard result == .updated else {
                state.issue = UserExampleIssue(kind: .error, message: UserExampleReviewStateCopy.notFound)
                return
            }
        } catch {
            state.issue = UserExampleIssue(kind: .error, message: UserExampleReviewStateCopy.updateFailed)
            return
        }
        onContentChanged()
        await load()
    }

    // MARK: - 编辑态（工作包 2.8 / C8）

    /// 同页切编辑态：草稿从当前 detail 起，进入前先退出重做态（开发规范 §8.1.1 两态互斥）。
    public func startEdit() {
        guard let detail = state.detail, !state.editing, !state.saving, !state.deleting,
              !state.updatingReviewState else { return }
        exitReroll()
        state.issue = nil
        state.draft = UserExampleDraft(detail: detail)
    }

    /// 取消编辑：草稿丢弃、浮层收起，未落库的内容不留痕。
    public func cancelEdit() {
        guard state.editing, !state.saving, !state.deleting, !state.updatingReviewState else { return }
        state.draft = nil
        state.showsIdiomPicker = false
        state.issue = nil
    }

    public func dismissIssue() {
        state.issue = nil
    }

    public func setStem(_ value: String) { mutate { $0.stem = value } }
    public func setAnswerText(_ value: String) { mutate { $0.answerText = value } }
    public func setExamType(_ value: String) { mutate { $0.examType = value } }
    public func setYearText(_ value: String) { mutate { $0.yearText = value } }
    public func setReason(_ value: String) { mutate { $0.reason = value } }

    public func setOption(_ index: Int, _ value: String) {
        guard (0..<4).contains(index) else { return }
        mutate { draft in
            while draft.options.count <= index { draft.options.append("") }
            draft.options[index] = value
        }
    }

    /// 标签口径同 P20：1–8 字、去重，越界或重复给提示并拒绝。
    @discardableResult
    public func addTag(_ raw: String) -> Bool {
        guard var draft = state.draft, !state.saving, !state.deleting, !state.updatingReviewState else {
            return false
        }
        let tag = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (1...8).contains(tag.count), !draft.tags.contains(tag) else {
            state.issue = UserExampleIssue(kind: .info, message: UserExampleEditCopy.tagRule)
            return false
        }
        draft.tags.append(tag)
        state.draft = draft
        return true
    }

    public func removeTag(_ tag: String) {
        mutate { $0.tags.removeAll { $0 == tag } }
    }

    /// 归属至少保留一条：删到只剩一条时给提示、不动草稿。
    public func removeIdiom(id idiomID: String) {
        guard var draft = state.draft, !state.saving, !state.deleting, !state.updatingReviewState else { return }
        guard draft.idioms.count > 1 else {
            state.issue = UserExampleIssue(kind: .info, message: UserExampleEditCopy.atLeastOne)
            return
        }
        draft.idioms.removeAll { $0.id == idiomID }
        state.draft = draft
    }

    public func setIdiomPicker(visible: Bool) {
        guard state.editing, !state.saving, !state.deleting, !state.updatingReviewState else { return }
        state.showsIdiomPicker = visible
    }

    /// 手动增补归属：排末尾（`user_example_idiom` 无 order 列，页面顺序即写入顺序）。
    public func pickIdiom(id idiomID: String) {
        guard state.editing, !state.saving, !state.deleting, !state.updatingReviewState else { return }
        state.showsIdiomPicker = false
        guard var draft = state.draft else { return }
        guard !draft.idioms.contains(where: { $0.id == idiomID }) else { return }
        guard let word = try? libraryReader.detail(id: idiomID)?.word else {
            state.issue = UserExampleIssue(kind: .info, message: P20Matcher.readFailed)
            return
        }
        draft.idioms.append(LinkedIdiom(id: idiomID, word: word))
        state.draft = draft
    }

    /// 保存：字段级非法已由「保存」置灰挡掉，题干为空与归属不足一条在点击时给提示。
    public func save() {
        guard let draft = state.draft, !state.saving, !state.deleting, !state.updatingReviewState,
              draft.canSave else { return }
        guard !draft.trimmedStem.isEmpty else {
            state.issue = UserExampleIssue(kind: .info, message: UserExampleEditCopy.emptyStem)
            return
        }
        guard !draft.idioms.isEmpty else {
            state.issue = UserExampleIssue(kind: .info, message: UserExampleEditCopy.atLeastOne)
            return
        }
        let input = UserExampleEditInput(
            id: id,
            stem: draft.trimmedStem,
            options: draft.options,
            myAnswer: draft.answer,
            examType: blankToNil(draft.examType),
            year: draft.year,
            reason: draft.isMistake ? blankToNil(draft.reason) : nil,
            tagNames: draft.isMistake ? draft.tags : [],
            idioms: draft.idioms
        )
        state.saving = true
        defer { state.saving = false }
        do {
            try editor.saveEdit(input)
        } catch {
            state.issue = UserExampleIssue(kind: .error, message: UserExampleEditCopy.saveFailed)
            return
        }
        state.draft = nil
        onContentChanged()
        Task { await load() }
    }

    // MARK: - 删除（工作包 2.8 / C8）

    /// 顶部「删除」与底部红行同一入口：只起二次确认，实际分支在确认里按标错／未标错判。
    public func requestDelete() {
        guard state.detail != nil, !state.editing, !state.saving, !state.deleting,
              !state.updatingReviewState else { return }
        state.issue = nil
        state.confirmDelete = true
    }

    public func dismissDelete() {
        guard !state.deleting, !state.updatingReviewState else { return }
        state.confirmDelete = false
    }

    /// 标错的＝移出错题本（删错题标记行，题与原图都留）；未标错的＝删整题连本机原图。
    public func confirmDelete() {
        guard let detail = state.detail, !state.deleting, !state.saving, !state.updatingReviewState else { return }
        state.deleting = true
        defer {
            state.deleting = false
            state.confirmDelete = false
        }
        do {
            if detail.isMistake {
                try editor.deleteMistake(userExampleID: detail.id)
            } else {
                try editor.deleteUserExample(id: detail.id)
            }
        } catch {
            state.issue = UserExampleIssue(kind: .error, message: UserExampleEditCopy.deleteFailed)
            return
        }
        onContentChanged()
        onDeleted()
    }

    private func mutate(_ change: (inout UserExampleDraft) -> Void) {
        guard var draft = state.draft, !state.saving, !state.deleting, !state.updatingReviewState else { return }
        change(&draft)
        state.draft = draft
    }
}

private func blankToNil(_ value: String) -> String? {
    let text = value.trimmingCharacters(in: .whitespacesAndNewlines)
    return text.isEmpty ? nil : text
}
