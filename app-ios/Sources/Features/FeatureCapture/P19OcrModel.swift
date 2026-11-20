import Combine
import Foundation
import UIKit

/// P19 识别页状态：缩略图与 Vision 识别都在本页生命周期内完成，识别结果落到可编辑字段。
/// 页面只展示引擎真实返回的行；识别为空走失败态，不制造文本。
@MainActor
final class P19OcrModel: ObservableObject {
    static let optionKeys = ["A", "B", "C", "D"]

    @Published var stem = ""
    @Published var options = ["", "", "", ""]
    @Published private(set) var scanning = true
    @Published private(set) var failed = false
    @Published private(set) var lineCount = 0
    @Published private(set) var thumbnail: UIImage?

    private var attempt = 0
    private var recognized = false

    /// 首次进入触发一次识别；字段与识别结果随后由本对象保管（P19→P20 返回不重跑）。
    func start(path: String) async {
        if thumbnail == nil {
            thumbnail = UIImage(contentsOfFile: path)
        }
        guard !recognized else { return }
        await run(path: path)
    }

    func rerun(path: String) async {
        recognized = false
        await run(path: path)
    }

    /// 题干 + 已填选项拼成识别结果文本；全部为空时返回空串，由路由走手动补字入口。
    var composedText: String {
        var lines: [String] = []
        let trimmedStem = stem.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedStem.isEmpty { lines.append(trimmedStem) }
        for (index, key) in Self.optionKeys.enumerated() where index < options.count {
            let value = options[index].trimmingCharacters(in: .whitespacesAndNewlines)
            if !value.isEmpty { lines.append("\(key). \(value)") }
        }
        return lines.joined(separator: "\n")
    }

    private func run(path: String) async {
        attempt += 1
        let token = attempt
        scanning = true
        let outcome = await Task.detached(priority: .userInitiated) {
            VisionOcrEngine.recognize(path: path)
        }.value
        guard token == attempt else { return }
        scanning = false
        recognized = true
        lineCount = outcome.rawLines.count
        failed = outcome.isEmpty
        guard !outcome.isEmpty else { return }
        stem = outcome.stem ?? ""
        options = Self.optionKeys.map { outcome.options[$0] ?? "" }
    }
}
