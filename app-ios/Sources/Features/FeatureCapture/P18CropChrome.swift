import CangyuDesignSystem
import CangyuTokens
import SwiftUI

enum CropCorner: Int, CaseIterable {
    case leftTop
    case rightTop
    case leftBottom
    case rightBottom
}

/// P18 顶部条：返回 + 页名 + 固定步骤文案「第 2 步 / 共 5 步」。
struct CropScreenHeader: View {
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
                .accessibilityLabel("返回重拍")
                Text("裁切与摆正")
                    .font(.system(size: Tokens.Font.n15, weight: .bold))
                    .foregroundStyle(colors.head)
                Spacer(minLength: 0)
                Text("第 2 步 / 共 5 步")
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

/// P18 画布：图片按框内等比展示（可 90° 整转与微调），四角橙框拖拽。
struct CropCanvasView: View {
    let source: CropSource
    @Binding var crop: CGRect
    let quarterTurns: Int
    let fineDegrees: CGFloat
    let colors: TokenColors
    let onSizeChange: (CGSize) -> Void
    @State private var activeCorner: CropCorner?
    @State private var startCrop: CGRect?

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Tokens.dark.cam
                image(in: geo.size)
                overlay(in: geo.size)
            }
            .contentShape(Rectangle())
            .gesture(dragGesture(in: geo.size))
            .onAppear { onSizeChange(geo.size) }
            .onChange(of: geo.size) { onSizeChange($0) }
        }
        .clipShape(RoundedRectangle(cornerRadius: Tokens.Radius.r6))
    }

    private func image(in size: CGSize) -> some View {
        let imageWidth = CGFloat(source.image.cgImage?.width ?? 1)
        let imageHeight = CGFloat(source.image.cgImage?.height ?? 1)
        let scale = CropImageEngine.fitScale(
            imageWidth: imageWidth,
            imageHeight: imageHeight,
            container: size,
            quarter: quarterTurns
        )
        return Image(uiImage: source.image)
            .resizable()
            .frame(width: imageWidth * scale, height: imageHeight * scale)
            .rotationEffect(.degrees(Double(quarterTurns) * 90 + Double(fineDegrees)))
            .position(x: size.width / 2, y: size.height / 2)
    }

    private func overlay(in size: CGSize) -> some View {
        let rect = CropCorner.pixelRect(crop, in: size)
        return ZStack {
            Path { path in path.addRect(rect) }
                .stroke(colors.cropedge, lineWidth: Tokens.Space.cropEdge)
            handlesPath(in: rect)
                .stroke(colors.warnfg, style: StrokeStyle(lineWidth: Tokens.Space.cropHandleStroke, lineCap: .square))
        }
    }

    private func handlesPath(in rect: CGRect) -> Path {
        let length = Tokens.Space.cropHandle
        var path = Path()
        for corner in CropCorner.allCases {
            let point = corner.point(in: rect)
            let armX: CGFloat = corner == .leftTop || corner == .leftBottom ? 1 : -1
            let armY: CGFloat = corner == .leftTop || corner == .rightTop ? 1 : -1
            path.move(to: point)
            path.addLine(to: CGPoint(x: point.x + armX * length, y: point.y))
            path.move(to: point)
            path.addLine(to: CGPoint(x: point.x, y: point.y + armY * length))
        }
        return path
    }

    private func dragGesture(in size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if activeCorner == nil {
                    activeCorner = nearestCorner(to: value.startLocation, in: size)
                    startCrop = crop
                }
                if let corner = activeCorner, let start = startCrop {
                    crop = CropCorner.moved(
                        start,
                        corner: corner,
                        translation: value.translation,
                        in: size,
                        minSize: Tokens.Space.cropMin
                    )
                }
            }
            .onEnded { _ in
                activeCorner = nil
                startCrop = nil
            }
    }

    private func nearestCorner(to point: CGPoint, in size: CGSize) -> CropCorner? {
        let rect = CropCorner.pixelRect(crop, in: size)
        let best = CropCorner.allCases.min { first, second in
            distance(first.point(in: rect), point) < distance(second.point(in: rect), point)
        }
        guard let best, distance(best.point(in: rect), point) <= Tokens.Space.hitMin else { return nil }
        return best
    }

    private func distance(_ first: CGPoint, _ second: CGPoint) -> CGFloat {
        hypot(first.x - second.x, first.y - second.y)
    }
}

/// P18「摆正」微调面板：-15°~+15°，步进 0.5°，可复位（规范 §4.4）。
struct CropAdjustPanel: View {
    @Binding var fineDegrees: CGFloat
    let colors: TokenColors

    var body: some View {
        HStack(spacing: Tokens.Space.cropRowGap) {
            Text("微调")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
            Slider(value: $fineDegrees, in: -15...15, step: 0.5)
                .tint(colors.accent)
            Text(label)
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.text3)
            Text("复位")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(colors.accent)
                .contentShape(Rectangle())
                .onTapGesture { fineDegrees = 0 }
        }
        .padding(.top, Tokens.Space.cropAdjustTop)
        .padding(.bottom, Tokens.Space.cropAdjustBottom)
    }

    private var label: String {
        fineDegrees > 0 ? "+\(fineDegrees)°" : "\(fineDegrees)°"
    }
}

private extension CropCorner {
    func point(in rect: CGRect) -> CGPoint {
        switch self {
        case .leftTop: return CGPoint(x: rect.minX, y: rect.minY)
        case .rightTop: return CGPoint(x: rect.maxX, y: rect.minY)
        case .leftBottom: return CGPoint(x: rect.minX, y: rect.maxY)
        case .rightBottom: return CGPoint(x: rect.maxX, y: rect.maxY)
        }
    }

    static func pixelRect(_ crop: CGRect, in size: CGSize) -> CGRect {
        CGRect(
            x: crop.minX * size.width,
            y: crop.minY * size.height,
            width: crop.width * size.width,
            height: crop.height * size.height
        )
    }

    static func moved(
        _ crop: CGRect,
        corner: CropCorner,
        translation: CGSize,
        in size: CGSize,
        minSize: CGFloat
    ) -> CGRect {
        let minWidth = min(1, minSize / size.width)
        let minHeight = min(1, minSize / size.height)
        let offsetX = translation.width / size.width
        let offsetY = translation.height / size.height
        var moved = crop
        let left = (crop.minX + offsetX).clamped(to: 0...(crop.maxX - minWidth))
        let top = (crop.minY + offsetY).clamped(to: 0...(crop.maxY - minHeight))
        let right = (crop.maxX + offsetX).clamped(to: (crop.minX + minWidth)...1)
        let bottom = (crop.maxY + offsetY).clamped(to: (crop.minY + minHeight)...1)
        switch corner {
        case .leftTop:
            moved.origin.x = left
            moved.origin.y = top
        case .rightTop:
            moved.size.width = right - crop.minX
            moved.origin.y = top
        case .leftBottom:
            moved.origin.x = left
            moved.size.height = bottom - crop.minY
        case .rightBottom:
            moved.size.width = right - crop.minX
            moved.size.height = bottom - crop.minY
        }
        return moved
    }
}

private extension CGFloat {
    func clamped(to range: ClosedRange<CGFloat>) -> CGFloat {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
