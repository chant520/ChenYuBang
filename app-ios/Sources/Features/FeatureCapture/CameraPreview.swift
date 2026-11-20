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
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }

    var previewLayer: AVCaptureVideoPreviewLayer? { layer as? AVCaptureVideoPreviewLayer }
}
