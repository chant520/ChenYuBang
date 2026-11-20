import CangyuDesignSystem
import CangyuTokens
import SwiftUI
import UIKit

/// P17 拍照 / 相册选择（信息架构 v1.4 §3）：取景、快门、相册入口、取消；
/// 相机权限被拒时给「去系统设置 / 改用相册」两条退路（D 稿 P17）。
/// 产出仅为一张本机图片 URL，交给 P18 裁切；取消即整条丢弃。
struct P17CaptureScreen: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var model: CaptureCameraModel
    @StateObject private var pickerResult: PhotoPickerResult
    @State private var isPickerPresented = false

    private let isActive: Bool
    private let onCancel: () -> Void
    private let onImageReady: (URL) -> Void

    @MainActor
    init(isActive: Bool, onCancel: @escaping () -> Void, onImageReady: @escaping (URL) -> Void) {
        _model = StateObject(wrappedValue: CaptureCameraModel())
        _pickerResult = StateObject(wrappedValue: PhotoPickerResult())
        self.isActive = isActive
        self.onCancel = onCancel
        self.onImageReady = onImageReady
    }

    var body: some View {
        ZStack {
            Tokens.dark.cam.ignoresSafeArea()
            VStack(spacing: 0) {
                CaptureTopBar(onCancel: onCancel)
                content
            }
        }
        .task(id: isActive) {
            if isActive {
                await model.refreshPermission()
            } else {
                model.stop()
            }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active, isActive {
                Task { await model.refreshPermission() }
            }
        }
        .onDisappear { model.stop() }
        .sheet(isPresented: $isPickerPresented) {
            PhotoPicker(result: pickerResult)
        }
        .onChange(of: pickerResult.outcome) { outcome in
            switch outcome {
            case .idle:
                break
            case .picked(let url):
                isPickerPresented = false
                onImageReady(url)
            case .failed:
                isPickerPresented = false
                model.notePickFailure()
            case .cancelled:
                isPickerPresented = false
            }
        }
        .onChange(of: model.capturedURL) { url in
            guard let url else { return }
            model.consumeCapturedURL()
            onImageReady(url)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .ready:
            cameraBody
        case .checking:
            Spacer(minLength: 0)
        case .denied, .unavailable:
            CaptureDeniedPanel(
                unavailable: model.state == .unavailable,
                pickFailed: model.hint == .pickFailed,
                onOpenSettings: openSystemSettings,
                onPick: presentPicker,
                onCancel: onCancel
            )
        }
    }

    private var cameraBody: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .top) {
                CameraPreview(session: model.camera.session)
                if let hint = model.hint {
                    CaptureHintBanner(hint: hint, onPick: presentPicker)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            CaptureShutterRow(
                isCapturing: model.isCapturing,
                onPick: presentPicker,
                onShoot: model.takePhoto,
                onCancel: onCancel
            )
        }
    }

    private func presentPicker() {
        pickerResult.reset()
        isPickerPresented = true
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
