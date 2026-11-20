import CangyuDesignSystem
import CangyuTokens
import SwiftUI

/// P19 识别结果（信息架构 v1.4 §3，第 3 步；设计规范 §4.5）：对 P18 导出的裁切图跑 Vision 端侧识别，
/// 结果落到可编辑的题干与 A–D 选项；识别不到文字走失败态三条退路。
/// 返回回 P18 时框选保留（页面不销毁），P19→P20 返回时已改的字也保留。
struct P19OcrScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: P19OcrModel
    @State private var zoom = false

    private let imageURL: URL
    private let onBack: () -> Void
    private let onRetake: () -> Void
    private let onManual: () -> Void
    private let onNext: (String) -> Void

    @MainActor
    init(
        imageURL: URL,
        onBack: @escaping () -> Void,
        onRetake: @escaping () -> Void,
        onManual: @escaping () -> Void,
        onNext: @escaping (String) -> Void
    ) {
        _model = StateObject(wrappedValue: P19OcrModel())
        self.imageURL = imageURL
        self.onBack = onBack
        self.onRetake = onRetake
        self.onManual = onManual
        self.onNext = onNext
    }

    var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        ZStack {
            VStack(spacing: 0) {
                OcrScreenHeader(onBack: onBack)
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        OcrThumbRow(
                            thumbnail: model.thumbnail,
                            lineCount: model.lineCount,
                            colors: colors,
                            onZoom: { zoom = true }
                        )
                        Rectangle().fill(colors.line2).frame(height: 1)
                        Spacer().frame(height: Tokens.Space.ocrRowBottom)
                        content(colors)
                    }
                    .padding(.horizontal, Tokens.Space.pageX)
                    .padding(.top, Tokens.Space.ocrTop)
                    .padding(.bottom, Tokens.Space.pageX)
                }
            }
            .background(colors.page)
            if zoom, let image = model.thumbnail {
                OcrZoomLayer(image: image, colors: colors, onDismiss: { zoom = false })
            }
        }
        .task { await model.start(path: imageURL.path) }
    }

    @ViewBuilder
    private func content(_ colors: TokenColors) -> some View {
        if model.scanning {
            OcrScanningRow(colors: colors)
        } else if model.failed {
            OcrFailureView(colors: colors, onRetake: onRetake, onBack: onBack, onManual: onManual)
        } else {
            fields(colors)
        }
    }

    private func fields(_ colors: TokenColors) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            CLabel("题干")
            CInput(text: $model.stem, placeholder: "识别出的题干")
                .padding(.bottom, Tokens.Space.ocrInputGap)
            CLabel("选项")
            ForEach(P19OcrModel.optionKeys.indices, id: \.self) { index in
                CInput(text: optionBinding(index), placeholder: "选项 \(P19OcrModel.optionKeys[index])")
                    .padding(.bottom, Tokens.Space.ocrInputGap)
            }
            Text("识别有缺漏就在这里逐字补")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
                .padding(.bottom, Tokens.Space.ocrHintBottom)
            CButton(text: "重新识别", variant: .ghost) {
                Task { await model.rerun(path: imageURL.path) }
            }
            .padding(.top, Tokens.Space.ocrGhostTop)
            .padding(.bottom, Tokens.Space.ocrGhostBottom)
            CButton(text: "下一步") { onNext(model.composedText) }
        }
    }

    private func optionBinding(_ index: Int) -> Binding<String> {
        Binding(
            get: { index < model.options.count ? model.options[index] : "" },
            set: { value in
                if index < model.options.count {
                    model.options[index] = value
                }
            }
        )
    }
}
