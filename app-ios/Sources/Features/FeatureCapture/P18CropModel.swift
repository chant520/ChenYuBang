import Combine
import CoreGraphics
import Foundation

/// P18 裁切页状态：图片解码、初始框建议与导出都在后台完成，导出结果回主线程交出 URL。
/// 与 P17 同口径——页面只消费真实文件，不制造识别或裁切结果。
@MainActor
final class P18CropModel: ObservableObject {
    @Published private(set) var source: CropSource?
    @Published private(set) var loadFailed = false
    @Published private(set) var exporting = false
    @Published private(set) var exportFailed = false
    @Published private(set) var exportedURL: URL?
    @Published var crop = CGRect(x: 0.06, y: 0.06, width: 0.88, height: 0.88)
    @Published var quarterTurns = 0
    @Published var fineDegrees: CGFloat = 0

    func load(path: String) async {
        guard source == nil, !loadFailed else { return }
        let loaded = await Task.detached(priority: .userInitiated) {
            CropImageEngine.load(path: path)
        }.value
        guard let loaded else {
            loadFailed = true
            return
        }
        source = loaded
        crop = CropImageEngine.suggest(loaded)
    }

    func export(containerSize: CGSize) {
        guard let source, !exporting, containerSize.width > 0, containerSize.height > 0 else { return }
        exporting = true
        exportFailed = false
        let quarterTurns = quarterTurns
        let fineDegrees = fineDegrees
        let crop = crop
        Task {
            let result = await Task.detached(priority: .userInitiated) {
                guard let data = CropImageEngine.export(
                    source: source,
                    quarterTurns: quarterTurns,
                    fineDegrees: fineDegrees,
                    crop: crop,
                    containerSize: containerSize
                ) else { return nil as URL? }
                return CaptureImageStore.writeJPEG(data)
            }.value
            exporting = false
            if let result {
                exportedURL = result
            } else {
                exportFailed = true
            }
        }
    }

    func consumeExportedURL() {
        exportedURL = nil
    }
}
