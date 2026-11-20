import CoreGraphics
import Foundation

/// 一行识别文本；`box` 为 Vision 归一化坐标（原点左下），仅用于行合并排序。
struct OcrLine: Equatable {
    let text: String
    let confidence: Float
    let box: CGRect
}

/// 识别结果的文本布局：竖直重叠 ≥50% 视为同一行、行内从左到右拼接，
/// 再以 A–D 标记切成「题干 + 选项」。纯函数，不依赖 Vision，CI 单测直接覆盖。
enum OcrTextLayout {
    private static let overlapRatio: CGFloat = 0.5

    private static let markerKeys: [Character: String] = [
        "A": "A", "B": "B", "C": "C", "D": "D",
        "a": "A", "b": "B", "c": "C", "d": "D",
        "Ａ": "A", "Ｂ": "B", "Ｃ": "C", "Ｄ": "D",
        "ａ": "A", "ｂ": "B", "ｃ": "C", "ｄ": "D"
    ]

    private static let markerSeparators: Set<Character> = [
        "．", ".", "、", ":", "：", ",", "，", ")", "）"
    ]

    /// 同一行被切成多段时合并：先按上沿从高到低扫，再逐行从左到右拼接。
    static func rows(from lines: [OcrLine]) -> [String] {
        var grouped: [[OcrLine]] = []
        for line in lines.sorted(by: { $0.box.maxY > $1.box.maxY }) {
            if let index = grouped.lastIndex(where: { sameRow($0, line) }) {
                grouped[index].append(line)
            } else {
                grouped.append([line])
            }
        }
        return grouped
            .sorted { first, second in
                (first.map(\.box.maxY).max() ?? 0) > (second.map(\.box.maxY).max() ?? 0)
            }
            .map { row in
                row.sorted { $0.box.minX < $1.box.minX }.map(\.text).joined()
            }
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    /// 以 A–D 选项标记起头的行为分界：标记前归题干，标记后的无标记行并入上一个选项（长选项折行）。
    static func classify(_ lines: [String]) -> (stem: String, options: [String: String]) {
        var stemLines: [String] = []
        var options: [String: String] = [:]
        var current: String?
        for raw in lines {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !line.isEmpty else { continue }
            if let marker = marker(of: line) {
                options[marker.key] = marker.body
                current = marker.key
            } else if let key = current {
                options[key] = (options[key] ?? "") + line
            } else {
                stemLines.append(line)
            }
        }
        return (stemLines.joined(separator: "\n"), options)
    }

    private static func sameRow(_ row: [OcrLine], _ line: OcrLine) -> Bool {
        guard let top = row.map(\.box.maxY).max(),
              let bottom = row.map(\.box.minY).min()
        else { return false }
        let overlap = min(top, line.box.maxY) - max(bottom, line.box.minY)
        let shorter = min(top - bottom, line.box.height)
        return shorter > 0 && overlap >= shorter * overlapRatio
    }

    private static func marker(of line: String) -> (key: String, body: String)? {
        guard let first = line.first, let key = markerKeys[first] else { return nil }
        var rest = line.dropFirst().drop(while: { $0 == " " || $0 == "\u{3000}" })
        guard let separator = rest.first, markerSeparators.contains(separator) else { return nil }
        rest = rest.dropFirst()
        return (key, rest.trimmingCharacters(in: .whitespacesAndNewlines))
    }
}
