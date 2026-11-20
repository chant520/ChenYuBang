import AVFoundation
import Combine

/// P17 相机状态与行为：权限判定、会话启停、快门与相册结果落本机缓存。
/// 页面只读状态并调用方法，不直接碰 AVFoundation。
@MainActor
final class CaptureCameraModel: ObservableObject {
    enum CameraState: Equatable {
        case checking
        case ready
        case denied
        case unavailable
    }

    enum Hint: Equatable {
        case unavailable
        case pickFailed
        case captureFailed

        var message: String {
            switch self {
            case .unavailable: return "相机不可用，请改用相册"
            case .pickFailed: return "照片读取失败，请重试"
            case .captureFailed: return "拍照失败，请重试"
            }
        }

        var offersPicker: Bool {
            self != .captureFailed
        }
    }

    @Published private(set) var state: CameraState = .checking
    @Published private(set) var isCapturing = false
    @Published private(set) var hint: Hint?
    /// 快门产出：页面在 onChange 里交给 P18，再调用 consumeCapturedURL 清空。
    @Published private(set) var capturedURL: URL?

    let camera = CaptureCameraSession()
    private var pendingPhoto: PhotoWriteDelegate?
    private var sessionRunning = false
    private var didAskPermission = false

    /// 首次进入、从系统设置返回、从裁切页退回都会走这里：
    /// 已授权就启动会话，被拒就展示退路。
    func refreshPermission() async {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            await startSession()
        case .notDetermined:
            guard !didAskPermission else {
                state = .checking
                return
            }
            didAskPermission = true
            if await Self.requestAccess() {
                await startSession()
            } else {
                state = .denied
            }
        case .denied, .restricted:
            state = .denied
        @unknown default:
            state = .denied
        }
    }

    /// 快门：真实拍摄一张 JPEG 到缓存；结果经 capturedURL 回到页面。
    func takePhoto() {
        guard state == .ready, sessionRunning, !isCapturing,
              let url = try? CaptureImageStore.newCaptureFileURL()
        else { return }
        isCapturing = true
        hint = nil
        let delegate = PhotoWriteDelegate(url: url) { [weak self] result in
            Task { @MainActor in
                guard let self else { return }
                self.pendingPhoto = nil
                self.isCapturing = false
                if let result {
                    self.capturedURL = result
                } else {
                    self.hint = .captureFailed
                }
            }
        }
        pendingPhoto = delegate
        camera.capture(delegate: delegate)
    }

    func consumeCapturedURL() {
        capturedURL = nil
    }

    func notePickFailure() {
        hint = .pickFailed
    }

    func stop() {
        sessionRunning = false
        camera.stop()
    }

    private func startSession() async {
        guard !sessionRunning else {
            state = .ready
            return
        }
        let started = await camera.start()
        sessionRunning = started
        state = started ? .ready : .unavailable
        if !started { hint = .unavailable }
    }

    private static func requestAccess() async -> Bool {
        await withCheckedContinuation { continuation in
            AVCaptureDevice.requestAccess(for: .video) { granted in
                continuation.resume(returning: granted)
            }
        }
    }
}
