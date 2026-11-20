import Foundation

/// P17 拍照 / 选图的图片落本机缓存（零联网）：P18 裁切与后续识别读这个文件。
/// 与 Android CaptureImageStore 同口径——放缓存目录，拍照链路取消即整条丢弃，不做草稿。
enum CaptureImageStore {
    static func newCaptureFileURL() throws -> URL {
        try captureDirectory().appendingPathComponent("\(UUID().uuidString).jpg")
    }

    /// PHPicker 交付的是临时文件 URL，复制到缓存目录由本机持有。
    static func copyIntoCache(_ source: URL) -> URL? {
        guard let target = try? newCaptureFileURL() else { return nil }
        do {
            try FileManager.default.copyItem(at: source, to: target)
            return target
        } catch {
            return nil
        }
    }

    /// P18 导出的裁切 JPEG 落缓存；写盘失败返回 nil，由页面提示重试。
    static func writeJPEG(_ data: Data) -> URL? {
        guard let target = try? newCaptureFileURL() else { return nil }
        do {
            try data.write(to: target, options: .atomic)
            return target
        } catch {
            return nil
        }
    }

    private static func captureDirectory() throws -> URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let directory = base.appendingPathComponent("capture", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }
}
