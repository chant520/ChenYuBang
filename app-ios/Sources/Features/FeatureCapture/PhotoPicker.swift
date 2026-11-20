import Combine
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

/// P17 相册结果：PHPicker 回调经后台读完文件后，在主线程写回页面。
@MainActor
final class PhotoPickerResult: ObservableObject {
    enum Outcome: Equatable {
        case idle
        case picked(URL)
        case failed
        case cancelled
    }

    @Published private(set) var outcome: Outcome = .idle

    func reset() {
        outcome = .idle
    }

    fileprivate func finish(_ url: URL?) {
        if let url {
            outcome = .picked(url)
        } else {
            outcome = .failed
        }
    }

    fileprivate func cancel() {
        outcome = .cancelled
    }
}

/// P17 相册入口：PHPicker 只读选中照片，不申请相册读取权限（与 Android PhotoPicker 同口径）。
/// 选完只写结果箱，由页面关闭 sheet——delegate 里不碰任何 UI。
struct PhotoPicker: UIViewControllerRepresentable {
    let result: PhotoPickerResult

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = 1
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(result: result)
    }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        private let result: PhotoPickerResult

        init(result: PhotoPickerResult) {
            self.result = result
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            let box = result
            guard let provider = results.first?.itemProvider else {
                Task { @MainActor in box.cancel() }
                return
            }
            guard provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) else {
                Task { @MainActor in box.finish(nil) }
                return
            }
            provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { url, _ in
                let copied = url.flatMap { CaptureImageStore.copyIntoCache($0) }
                Task { @MainActor in box.finish(copied) }
            }
        }
    }
}
