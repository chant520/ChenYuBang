import Foundation

/// P20 表单编辑与保存状态；与候选状态机分文件，避免单文件过长。
extension P20MatchModel {
    public func setAnswerText(_ raw: String) {
        state.form.answerText = String(raw.uppercased().filter { "ABCD".contains($0) }.prefix(1))
        state.form.saveError = nil
    }

    public func setReason(_ value: String) {
        guard state.form.isMistake else { return }
        state.form.reason = value
    }

    /// 标签 1–8 字、不重复；返回是否加入，页面据此清空草稿。
    @discardableResult
    public func addTag(_ raw: String) -> Bool {
        guard state.form.isMistake else { return false }
        let tag = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (1...8).contains(tag.count), !state.form.tags.contains(tag) else {
            state.info = P20Matcher.tagRule
            return false
        }
        state.form.tags.append(tag)
        state.info = nil
        return true
    }

    public func removeTag(_ tag: String) {
        state.form.tags.removeAll { $0 == tag }
    }

    public func toggleMistake() {
        state.form.isMistake.toggle()
        if !state.form.isMistake {
            state.form.reason = ""
            state.form.tags = []
        }
        state.form.saveError = nil
    }

    /// 来源信息可空：空白输入落 nil，其余原样保留（高保真稿 C P20 来源行）。
    public func setExamTypeText(_ raw: String) {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        state.form.examType = value.isEmpty ? nil : value
        state.form.saveError = nil
    }

    public func setYearText(_ raw: String) {
        state.form.yearText = String(raw.filter { $0.isNumber }.prefix(4))
        state.form.saveError = nil
    }

    public func beginSave() {
        state.form.saving = true
        state.form.saveError = nil
    }

    public func finishSave() {
        state.form.saving = false
    }

    public func failSave(_ message: String = P20Matcher.saveFailed) {
        state.form.saving = false
        state.form.saveError = message
    }

    public func dismissSaveError() {
        state.form.saveError = nil
    }
}
