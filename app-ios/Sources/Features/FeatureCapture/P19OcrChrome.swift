import CangyuDesignSystem
import CangyuTokens
import SwiftUI
import UIKit

/// P19 顶部条：形态与 P18 相同（设计规范 §4.5「顶部条同 §4.4 形态」），文案固定「第 3 步 / 共 5 步」。
struct OcrScreenHeader: View {
    @Environment(\.colorScheme) private var colorScheme
    let onBack: () -> Void

    var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(spacing: 0) {
            HStack(spacing: Tokens.Space.camTopGap) {
                Button(action: onBack) {
                    CIcon(.back, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                        .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                        .contentShape(Rectangle())
                }
                .buttonStyle(CPressDimStyle())
                .accessibilityLabel("返回裁切")
                Text("识别结果")
                    .font(.system(size: Tokens.Font.n15, weight: .bold))
                    .foregroundStyle(colors.head)
                Spacer(minLength: 0)
                Text("第 3 步 / 共 5 步")
                    .font(.system(size: Tokens.Font.n11))
                    .foregroundStyle(colors.text3)
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.top, Tokens.Space.cropHeadTop)
            .padding(.bottom, Tokens.Space.cropHeadBottom)
            Rectangle().fill(colors.line).frame(height: Tokens.Space.cropEdge)
        }
        .background(colors.page)
    }
}

/// P19 缩略图行：44pt 裁切图缩略 + 「识别到 N 行」，点缩略图整屏放大但不改任何字段。
struct OcrThumbRow: View {
    let thumbnail: UIImage?
    let lineCount: Int
    let colors: TokenColors
    let onZoom: () -> Void

    var body: some View {
        HStack(spacing: Tokens.Space.ocrThumbGap) {
            ZStack {
                if let thumbnail {
                    Image(uiImage: thumbnail)
                        .resizable()
                        .scaledToFill()
                }
            }
            .frame(width: Tokens.Space.ocrThumb, height: Tokens.Space.ocrThumb)
            .background(colors.phbg)
            .clipShape(RoundedRectangle(cornerRadius: Tokens.Radius.r4))
            .overlay(
                RoundedRectangle(cornerRadius: Tokens.Radius.r4)
                    .strokeBorder(colors.phline, lineWidth: 1)
            )
            .contentShape(Rectangle())
            .onTapGesture { if thumbnail != nil { onZoom() } }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("裁切图，可点开放大")
            .accessibilityAddTraits(.isButton)
            Text("裁切图 · 可点开放大")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
            Spacer(minLength: 0)
            CTag(text: "识别到 \(lineCount) 行")
        }
        .padding(.bottom, Tokens.Space.ocrRowBottom)
    }
}

/// 识别中：14pt 转圈 + 「正在识别…」，不显示半成品字段。
struct OcrScanningRow: View {
    let colors: TokenColors

    var body: some View {
        HStack(spacing: Tokens.Space.ocrThumbGap) {
            CSpinner()
                .foregroundStyle(colors.accent)
            Text("正在识别…")
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text2)
        }
        .padding(.vertical, Tokens.Space.ocrFailGap)
    }
}

/// 失败态：橙只用于占位与警示条两处提示，主行动仍为绿（设计规范 §4.5）。
struct OcrFailureView: View {
    let colors: TokenColors
    let onRetake: () -> Void
    let onBack: () -> Void
    let onManual: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            CImagePlaceholder(kind: .warn, title: "图像已拿到，没有识别出文字")
            Spacer().frame(height: Tokens.Space.ocrFailGap)
            CBar(
                kind: .warn,
                title: "这张图没识别出文字",
                subtitle: "可以重拍、回上一步重新框选，或直接手动补字。"
            )
            Spacer().frame(height: Tokens.Space.ocrFailGap)
            VStack(spacing: Tokens.Space.ocrFailGap) {
                CButton(text: "重拍这张", action: onRetake)
                CButton(text: "回上一步重新框选", variant: .ghost, action: onBack)
                CButton(text: "手动补字", variant: .ghost, action: onManual)
            }
            Spacer().frame(height: Tokens.Space.ocrFailGap)
            CCard(soft: true) {
                Text(
                    "重拍这张：回到拍照页重新拍一张。\n"
                        + "回上一步重新框选：只框这一道题，邻题不要。\n"
                        + "手动补字：直接进入修正页，以空文本起填。"
                )
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text2)
                .fixedSize(horizontal: false, vertical: true)
            }
            Text("手动补字不影响已拍的图，之后还能再识别。")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
                .padding(.top, Tokens.Space.ocrNoteTop)
        }
    }
}

/// 裁切图放大层：点任意处关闭，不改变任何字段。
struct OcrZoomLayer: View {
    let image: UIImage
    let colors: TokenColors
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            colors.page.ignoresSafeArea()
            VStack(spacing: Tokens.Space.ocrThumbGap) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                Text("点击任意处关闭")
                    .font(.system(size: Tokens.Font.n11))
                    .foregroundStyle(colors.text3)
            }
            .padding(Tokens.Space.pageX)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onDismiss)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("裁切图放大，点击任意处关闭")
        .accessibilityAddTraits(.isButton)
    }
}
