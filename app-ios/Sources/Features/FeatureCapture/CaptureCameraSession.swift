import AVFoundation
import Foundation

/// P17 取景会话：AVCaptureSession 的配置与启停只在 sessionQueue 串行执行；
/// 跨队列只传递串行队列封闭的会话对象与不可变输出，故以 @unchecked Sendable 标记。
final class CaptureCameraSession: @unchecked Sendable {
    let session = AVCaptureSession()
    private let photoOutput = AVCapturePhotoOutput()
    private let sessionQueue = DispatchQueue(label: "com.chenyubang.capture.session")
    private var isConfigured = false

    /// 配置后启动会话；返回是否已在取景。硬件初始化在后台队列完成，不占主线程。
    func start() async -> Bool {
        await withCheckedContinuation { continuation in
            sessionQueue.async {
                let configured = self.configureIfNeeded()
                if configured && !self.session.isRunning {
                    self.session.startRunning()
                }
                continuation.resume(returning: configured && self.session.isRunning)
            }
        }
    }

    func stop() {
        sessionQueue.async {
            if self.session.isRunning {
                self.session.stopRunning()
            }
        }
    }

    /// 快门：真实拍摄一张 JPEG，写入 delegate 指定的缓存文件。
    func capture(delegate: AVCapturePhotoCaptureDelegate) {
        photoOutput.capturePhoto(with: AVCapturePhotoSettings(), delegate: delegate)
    }

    private func configureIfNeeded() -> Bool {
        if isConfigured { return true }
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device)
        else { return false }
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        session.sessionPreset = .photo
        guard session.canAddInput(input), session.canAddOutput(photoOutput) else { return false }
        session.addInput(input)
        session.addOutput(photoOutput)
        applyPortraitRotation()
        isConfigured = true
        return true
    }

    /// App 只支持竖屏（Info.plist），照片连接固定写成竖拍方向。
    private func applyPortraitRotation() {
        guard let connection = photoOutput.connection(with: .video) else { return }
        if #available(iOS 17.0, *) {
            if connection.isVideoRotationAngleSupported(90) {
                connection.videoRotationAngle = 90
            }
        } else {
            connection.videoOrientation = .portrait
        }
    }
}

/// 快门回调：拍下即把 JPEG 写到缓存文件，不在回调里碰 UI 状态。
final class PhotoWriteDelegate: NSObject, AVCapturePhotoCaptureDelegate {
    private let url: URL
    private let onFinish: @Sendable (URL?) -> Void

    init(url: URL, onFinish: @escaping @Sendable (URL?) -> Void) {
        self.url = url
        self.onFinish = onFinish
    }

    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        guard error == nil,
              let data = photo.fileDataRepresentation(),
              (try? data.write(to: url, options: .atomic)) != nil
        else {
            onFinish(nil)
            return
        }
        onFinish(url)
    }
}
