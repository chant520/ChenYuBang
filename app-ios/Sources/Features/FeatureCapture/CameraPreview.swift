import AVFoundation
import CangyuTokens
import SwiftUI
import UIKit

/// P17 取景层：AVCaptureVideoPreviewLayer 直接铺满，FILL_CENTER 等效裁切。
struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> CameraPreviewUIView {
        let view = CameraPreviewUIView()
        view.previewLayer?.session = session
        view.previewLayer?.videoGravity = .resizeAspectFill
        view.previewLayer?.backgroundColor = UIColor(Tokens.dark.cam).cgColor
        return view
    }

    func updateUIView(_ uiView: CameraPreviewUIView, context: Context) {}
}

final class CameraPreviewUIView: UIView {
    // 覆写 UIKit 的 class var 只能用 class（static 不可覆写），不是可改 static 的场景。
    // swiftlint:disable:next static_over_final_class
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }

    var previewLayer: AVCaptureVideoPreviewLayer? { layer as? AVCaptureVideoPreviewLayer }
}
