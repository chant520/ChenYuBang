import CoreImage
import Foundation
import UIKit
import Vision

/// 一次性识别结果（跨线程传回主线程用；只含值类型）。
struct OcrOutcome: Sendable, Equatable {
    let stem: String?
    let options: [String: String]
    let rawLines: [String]
    let durationMs: Int

    var isEmpty: Bool {
        let trimmedStem = (stem ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmedStem.isEmpty && options.isEmpty
    }
}

/// P19 端侧识别（开发规范 v1.6 §2.3）：Vision `zh-Hans`、`.accurate`、全链路零联网。
/// 送识别前短边不足 1400px 等比上采样，长边超 4000px 再压回；失败按空结果返回，
/// 由页面走失败态三条退路。真机精度复测后置（B1），不以代理数字宣称达标。
enum VisionOcrEngine {
    static let minShortSide: CGFloat = 1400
    static let maxLongSide: CGFloat = 4000
    private static let minLineConfidence: Float = 0.5

    static func recognize(path: String) -> OcrOutcome {
        let started = Date()
        let lines = (try? recognizedLines(path: path)) ?? []
        let (stem, options) = OcrTextLayout.classify(lines)
        let elapsed = Int(Date().timeIntervalSince(started) * 1000)
        return OcrOutcome(
            stem: stem.isEmpty ? nil : stem,
            options: options,
            rawLines: lines,
            durationMs: elapsed
        )
    }

    /// 上采样后的目标尺寸：短边至少 `minShortSide`，长边超过 `maxLongSide` 时长边封顶。
    static func targetSize(width: CGFloat, height: CGFloat) -> CGSize {
        guard width > 0, height > 0 else { return CGSize(width: 1, height: 1) }
        let shortSide = min(width, height)
        var scale = shortSide < minShortSide ? minShortSide / shortSide : 1
        let longSide = max(width, height) * scale
        if longSide > maxLongSide {
            scale *= maxLongSide / longSide
        }
        return CGSize(width: max(1, (width * scale).rounded()), height: max(1, (height * scale).rounded()))
    }

    private static func recognizedLines(path: String) throws -> [String] {
        guard let image = UIImage(contentsOfFile: path), let cgImage = image.cgImage else {
            throw OcrEngineError.unreadableImage
        }
        let prepared = upscaled(cgImage)
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = ["zh-Hans"]
        request.usesLanguageCorrection = true
        try VNImageRequestHandler(cgImage: prepared, options: [:]).perform([request])
        let observations = request.results ?? []
        let lines = observations.compactMap { observation -> OcrLine? in
            guard let candidate = observation.topCandidates(1).first,
                  candidate.confidence >= minLineConfidence
            else { return nil }
            return OcrLine(text: candidate.string, confidence: candidate.confidence, box: observation.boundingBox)
        }
        return OcrTextLayout.rows(from: lines)
    }

    private static func upscaled(_ image: CGImage) -> CGImage {
        let width = CGFloat(image.width)
        let height = CGFloat(image.height)
        let target = targetSize(width: width, height: height)
        guard target.width != width || target.height != height else { return image }
        let ciImage = CIImage(cgImage: image)
            .transformed(by: CGAffineTransform(scaleX: target.width / width, y: target.height / height))
        let context = CIContext(options: [.useSoftwareRenderer: false])
        return context.createCGImage(ciImage, from: ciImage.extent) ?? image
    }
}

enum OcrEngineError: Error {
    case unreadableImage
}
