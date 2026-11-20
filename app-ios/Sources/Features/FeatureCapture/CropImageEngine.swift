import CoreGraphics
import CoreImage
import Foundation
import UIKit

/// 跨线程传递的图片载体：`UIImage` 生成后只读，不入 UI；解码/导出在后台队列完成。
struct CropSource: @unchecked Sendable {
    let image: UIImage
}

/// P18 图片管线（纯本机，零联网）：读取 P17 的 JPEG、按内容给一次初始框、
/// 按旋转/微调/框选导出裁切图；导出图只保留框内内容，交给 P19 识别。
enum CropImageEngine {
    private static let maxDecodeDimension: CGFloat = 3200
    private static let analysisWidth = 360
    private static let analysisMinInk = 2
    private static let inkLuminanceDrop: CGFloat = 60
    private static let minInkLuminance: CGFloat = 40
    private static let fallbackBackground: CGFloat = 200
    private static let edgeInset: CGFloat = 0.06
    private static let contentMargin: CGFloat = 0.05
    private static let minContentFraction: CGFloat = 0.08
    private static let maxOutputDimension: CGFloat = 4096
    private static let jpegQuality: CGFloat = 0.9
    private static let quarterDegrees: CGFloat = 90

    static func load(path: String) -> CropSource? {
        guard let raw = UIImage(contentsOfFile: path), let cgImage = raw.cgImage else { return nil }
        let ciImage = CIImage(cgImage: cgImage)
            .oriented(forExifOrientation: exifOrientation(raw.imageOrientation))
        let longest = max(ciImage.extent.width, ciImage.extent.height)
        let scale = min(1, maxDecodeDimension / longest)
        let scaled = ciImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        let context = CIContext(options: [.useSoftwareRenderer: false])
        guard let rendered = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return CropSource(image: UIImage(cgImage: rendered))
    }

    /// 初始框建议：按画面亮度找出文字/纸张内容的包围盒（深色像素的行列投影），
    /// 再向内留 5% 余量。只是启发式建议，用户可拖动四角调整；识别只看导出图。
    static func suggest(_ source: CropSource) -> CGRect {
        let fallback = insetRect(CGRect(x: 0, y: 0, width: 1, height: 1), contentMargin)
        guard let image = source.image.cgImage else { return fallback }
        let width = analysisWidth
        let height = max(1, image.height * width / max(1, image.width))
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.translateBy(x: 0, y: CGFloat(height))
            context.scaleBy(x: 1, y: -1)
            context.interpolationQuality = .medium
            context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard drawn else { return fallback }
        let background = borderMedian(pixels, width: width, height: height)
        let threshold = max(background - inkLuminanceDrop, minInkLuminance)
        let ink = luminanceMask(pixels, threshold: threshold)
        let rect = inkBounds(ink, width: width, height: height)
        return insetRect(rect, contentMargin)
    }

    /// 导出框内画面；quarterTurns 为 90° 整转，fineDegrees 为「摆正」微调。
    static func export(
        source: CropSource,
        quarterTurns: Int,
        fineDegrees: CGFloat,
        crop: CGRect,
        containerSize: CGSize
    ) -> Data? {
        guard let image = source.image.cgImage,
              crop.width > 0, crop.height > 0,
              containerSize.width > 0, containerSize.height > 0
        else { return nil }
        let imageWidth = CGFloat(image.width)
        let imageHeight = CGFloat(image.height)
        let scale = fitScale(
            imageWidth: imageWidth,
            imageHeight: imageHeight,
            container: containerSize,
            quarter: quarterTurns
        )
        let cropWidth = crop.width * containerSize.width * scale
        let cropHeight = crop.height * containerSize.height * scale
        let shrink = min(1, maxOutputDimension / max(cropWidth, cropHeight))
        let outputWidth = max(1, Int((cropWidth * shrink).rounded(.down)))
        let outputHeight = max(1, Int((cropHeight * shrink).rounded(.down)))
        guard let context = CGContext(
            data: nil,
            width: outputWidth,
            height: outputHeight,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        context.setFillColor(CGColor(gray: 1, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: outputWidth, height: outputHeight))
        context.translateBy(x: 0, y: CGFloat(outputHeight))
        context.scaleBy(x: 1, y: -1)
        let degrees = CGFloat(quarterTurns) * quarterDegrees + fineDegrees
        ExportTransform(
            origin: CGPoint(x: -crop.minX * containerSize.width * scale, y: -crop.minY * containerSize.height * scale),
            center: CGPoint(x: containerSize.width / 2 * scale, y: containerSize.height / 2 * scale),
            halfSource: CGPoint(x: -imageWidth / 2, y: -imageHeight / 2),
            degrees: degrees,
            scale: scale * shrink
        ).apply(to: context)
        context.draw(image, in: CGRect(x: 0, y: 0, width: imageWidth, height: imageHeight))
        guard let output = context.makeImage() else { return nil }
        return UIImage(cgImage: output).jpegData(compressionQuality: jpegQuality)
    }

    static func fitScale(imageWidth: CGFloat, imageHeight: CGFloat, container: CGSize, quarter: Int) -> CGFloat {
        let swapped = quarter % 2 != 0
        let displayWidth = swapped ? imageHeight : imageWidth
        let displayHeight = swapped ? imageWidth : imageHeight
        return min(container.width / displayWidth, container.height / displayHeight)
    }

    private struct ExportTransform {
        let origin: CGPoint
        let center: CGPoint
        let halfSource: CGPoint
        let degrees: CGFloat
        let scale: CGFloat

        func apply(to context: CGContext) {
            context.translateBy(x: origin.x, y: origin.y)
            context.translateBy(x: center.x, y: center.y)
            context.rotate(by: degrees * .pi / 180)
            context.scaleBy(x: scale, y: scale)
            context.translateBy(x: halfSource.x, y: halfSource.y)
        }
    }

    private static func exifOrientation(_ orientation: UIImage.Orientation) -> Int32 {
        switch orientation {
        case .up: return 1
        case .down: return 3
        case .left: return 8
        case .right: return 6
        case .upMirrored: return 2
        case .downMirrored: return 4
        case .leftMirrored: return 5
        case .rightMirrored: return 7
        @unknown default: return 1
        }
    }

    private static func luminanceMask(_ pixels: [UInt8], threshold: CGFloat) -> [Bool] {
        var mask = [Bool](repeating: false, count: pixels.count / 4)
        for index in mask.indices {
            let offset = index * 4
            let value = CGFloat(pixels[offset]) * 0.299
                + CGFloat(pixels[offset + 1]) * 0.587
                + CGFloat(pixels[offset + 2]) * 0.114
            mask[index] = value < threshold
        }
        return mask
    }

    private static func borderMedian(_ pixels: [UInt8], width: Int, height: Int) -> CGFloat {
        let step = max(1, width / 40)
        var samples: [CGFloat] = []
        for column in stride(from: 0, to: width, by: step) {
            samples.append(luminance(pixels, index: column))
            samples.append(luminance(pixels, index: (height - 1) * width + column))
        }
        for row in stride(from: 0, to: height, by: step) {
            samples.append(luminance(pixels, index: row * width))
            samples.append(luminance(pixels, index: row * width + width - 1))
        }
        samples.sort()
        return samples.isEmpty ? fallbackBackground : samples[samples.count / 2]
    }

    private static func luminance(_ pixels: [UInt8], index: Int) -> CGFloat {
        let offset = index * 4
        return CGFloat(pixels[offset]) * 0.299
            + CGFloat(pixels[offset + 1]) * 0.587
            + CGFloat(pixels[offset + 2]) * 0.114
    }

    private static func inkBounds(_ ink: [Bool], width: Int, height: Int) -> CGRect {
        let columnMin = max(analysisMinInk, width / 100)
        let rowMin = max(analysisMinInk, height / 100)
        var columns = [Int](repeating: 0, count: width)
        var rows = [Int](repeating: 0, count: height)
        for row in 0..<height {
            for column in 0..<width where ink[row * width + column] {
                columns[column] += 1
                rows[row] += 1
            }
        }
        guard let left = columns.firstIndex(where: { $0 > columnMin }),
              let right = columns.lastIndex(where: { $0 > columnMin }),
              let top = rows.firstIndex(where: { $0 > rowMin }),
              let bottom = rows.lastIndex(where: { $0 > rowMin }),
              right > left, bottom > top
        else { return CGRect(x: edgeInset, y: edgeInset, width: 1 - edgeInset * 2, height: 1 - edgeInset * 2) }
        return CGRect(
            x: CGFloat(left) / CGFloat(width),
            y: CGFloat(top) / CGFloat(height),
            width: CGFloat(right - left + 1) / CGFloat(width),
            height: CGFloat(bottom - top + 1) / CGFloat(height)
        )
    }

    private static func insetRect(_ rect: CGRect, _ margin: CGFloat) -> CGRect {
        let result = rect.insetBy(dx: rect.width * margin, dy: rect.height * margin)
            .intersection(CGRect(x: 0, y: 0, width: 1, height: 1))
        guard result.width >= minContentFraction, result.height >= minContentFraction else {
            return CGRect(x: edgeInset, y: edgeInset, width: 1 - edgeInset * 2, height: 1 - edgeInset * 2)
        }
        return result
    }
}
