import CangyuDesignSystem
import CangyuTokens
import SwiftUI

/// P18 裁切与摆正（信息架构 v1.4 §3，第 2 步）：读取 P17 的图片，给一次内容边界建议框，
/// 四角可拖，支持 90° 旋转与 ±15° 摆正微调；「下一步」只导出框内内容，框外不参与识别。
struct P18CropScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: P18CropModel
    @State private var canvasSize = CGSize.zero
    @State private var showAdjust = false

    private let imageURL: URL
    private let onRetake: () -> Void
    private let onImageReady: (URL) -> Void

    @MainActor
    init(imageURL: URL, onRetake: @escaping () -> Void, onImageReady: @escaping (URL) -> Void) {
        _model = StateObject(wrappedValue: P18CropModel())
        self.imageURL = imageURL
        self.onRetake = onRetake
        self.onImageReady = onImageReady
    }

    var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(spacing: 0) {
            CropScreenHeader(onBack: onRetake)
            VStack(spacing: 0) {
                canvas(colors)
                Text("已自动给出题目边界，拖动四角可调整；只留这一道题，邻题去掉识别更准。")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text3)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, Tokens.Space.cropHintTop)
                    .padding(.bottom, Tokens.Space.cropHintBottom)
                actions(colors)
                if showAdjust {
                    CropAdjustPanel(fineDegrees: $model.fineDegrees, colors: colors)
                }
                Spacer().frame(height: Tokens.Space.cropRowBottom)
                if model.exportFailed {
                    Text("裁切失败，请重试或重拍")
                        .font(.system(size: Tokens.Font.n115))
                        .foregroundStyle(colors.warnfg)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.bottom, Tokens.Space.cropAdjustTop)
                }
                CButton(
                    text: model.exporting ? "正在裁切…" : "下一步 · 识别",
                    enabled: model.source != nil && canvasSize.width > 0,
                    loading: model.exporting,
                    action: { model.export(containerSize: canvasSize) }
                )
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.top, Tokens.Space.cropTop)
            .padding(.bottom, Tokens.Space.pageX)
        }
        .background(colors.page)
        .task { await model.load(path: imageURL.path) }
        .onChange(of: model.exportedURL) { url in
            guard let url else { return }
            model.consumeExportedURL()
            onImageReady(url)
        }
    }

    @ViewBuilder
    private func canvas(_ colors: TokenColors) -> some View {
        ZStack {
            if let source = model.source {
                CropCanvasView(
                    source: source,
                    crop: $model.crop,
                    quarterTurns: model.quarterTurns,
                    fineDegrees: model.fineDegrees,
                    colors: colors,
                    onSizeChange: { canvasSize = $0 }
                )
            } else {
                Tokens.dark.cam
                if model.loadFailed {
                    Text("图片读取失败，请返回重拍")
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(Tokens.dark.warnfg)
                } else {
                    Text("正在读取图片…")
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(Tokens.dark.camhint)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: Tokens.Radius.r6))
    }

    private func actions(_ colors: TokenColors) -> some View {
        let isEnabled = model.source != nil && !model.exporting
        return HStack(spacing: Tokens.Space.cropRowGap) {
            CButton(text: "旋转", variant: .ghost, enabled: isEnabled) {
                model.quarterTurns = (model.quarterTurns + 1) % 4
            }
            CButton(text: "摆正", variant: .ghost, enabled: isEnabled) {
                showAdjust.toggle()
            }
            CButton(text: "重拍", variant: .ghost, enabled: isEnabled, action: onRetake)
        }
        .foregroundStyle(colors.text2)
    }
}
