import CangyuDesignSystem
import CangyuTokens
import SwiftUI

/// P17 顶部条：关闭 + 页名「拍照」，底色随主题页色（相机区固定深色）。
struct CaptureTopBar: View {
    @Environment(\.colorScheme) private var colorScheme
    let onCancel: () -> Void

    var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        HStack(spacing: Tokens.Space.camTopGap) {
            Button(action: onCancel) {
                CIcon(.close, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                    .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                    .contentShape(Rectangle())
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel("取消拍照")
            Text("拍照")
                .font(.system(size: Tokens.Font.n15))
                .foregroundStyle(colors.head)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Tokens.Space.pageX)
        .padding(.top, Tokens.Space.contentTop)
        .padding(.bottom, Tokens.Space.camTopY)
        .background(colors.page.ignoresSafeArea(edges: .top))
    }
}

/// P17 取景区顶部的失败提示（相机不可用 / 相册读取失败 / 拍照失败）。
struct CaptureHintBanner: View {
    let hint: CaptureCameraModel.Hint
    let onPick: () -> Void

    var body: some View {
        HStack(spacing: Tokens.Space.camTopGap) {
            Text(hint.message)
                .font(.system(size: Tokens.Font.n115))
                .foregroundStyle(Tokens.dark.warnfg)
            if hint.offersPicker {
                Text("改用相册")
                    .font(.system(size: Tokens.Font.n115))
                    .foregroundStyle(Tokens.dark.camfg)
                    .contentShape(Rectangle())
                    .onTapGesture(perform: onPick)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Tokens.Space.pageX)
        .padding(.top, Tokens.Space.block)
    }
}

/// P17 快门行：相册入口 / 快门环 / 取消，左右对称排布（C 稿 P17）。
struct CaptureShutterRow: View {
    let isCapturing: Bool
    let onPick: () -> Void
    let onShoot: () -> Void
    let onCancel: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            galleryButton
            Spacer(minLength: 0)
            shutterButton
            Spacer(minLength: 0)
            cancelLabel
        }
        .padding(.horizontal, Tokens.Space.camRowX)
        .padding(.top, Tokens.Space.camRowTop)
        .padding(.bottom, Tokens.Space.camRowBottom)
        .background(Tokens.dark.cam)
    }

    private var galleryButton: some View {
        Button(action: onPick) {
            CIcon(.gallery, size: Tokens.Icon.n20, strokeWidth: 1.6, color: Tokens.dark.camfg)
                .frame(width: Tokens.Space.camGallery, height: Tokens.Space.camGallery)
                .overlay(
                    RoundedRectangle(cornerRadius: Tokens.Radius.r5)
                        .stroke(Tokens.dark.camstroke, lineWidth: 1)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(CPressDimStyle())
        .accessibilityLabel("从相册选择")
    }

    private var shutterButton: some View {
        Button(action: onShoot) {
            Circle()
                .stroke(Tokens.dark.camfg, lineWidth: Tokens.Space.camRing)
                .frame(width: Tokens.Space.camShutter, height: Tokens.Space.camShutter)
                .overlay(
                    Circle()
                        .fill(Tokens.dark.camfill)
                        .frame(width: Tokens.Space.camShutterIn, height: Tokens.Space.camShutterIn)
                )
        }
        .buttonStyle(CPressDimStyle())
        .disabled(isCapturing)
        .accessibilityLabel("拍照")
    }

    private var cancelLabel: some View {
        Text("取消")
            .font(.system(size: Tokens.Font.n11))
            .foregroundStyle(Tokens.dark.camdim)
            .frame(width: Tokens.Space.camGallery)
            .contentShape(Rectangle())
            .onTapGesture(perform: onCancel)
            .accessibilityLabel("取消拍照")
            .accessibilityAddTraits(.isButton)
    }
}

/// P17 权限被拒 / 相机不可用态：说明卡 + 去系统设置 + 改用相册 + 取消（D 稿 P17）。
struct CaptureDeniedPanel: View {
    let unavailable: Bool
    let pickFailed: Bool
    let onOpenSettings: () -> Void
    let onPick: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            if unavailable {
                Text(CaptureCameraModel.Hint.unavailable.message)
                    .font(.system(size: Tokens.Font.n115))
                    .foregroundStyle(Tokens.dark.warnfg)
                    .padding(.bottom, Tokens.Space.camDenyGap)
            }
            card
            Spacer(minLength: 0)
            actions
        }
        .frame(maxWidth: .infinity)
    }

    private var card: some View {
        VStack(spacing: 0) {
            CIcon(.cameraOff, size: Tokens.Space.camDenyIcon, strokeWidth: 1.6, color: Tokens.dark.warnfg)
            Text("需要相机权限才能拍照")
                .font(.system(size: Tokens.Font.n15))
                .foregroundStyle(Tokens.dark.head)
                .padding(.top, Tokens.Space.camDenyHint)
            Text("系统里这项权限被关掉了。可以先去开启，也可以先用相册里的照片。")
                .font(.system(size: Tokens.Font.n115))
                .foregroundStyle(Tokens.dark.text2)
                .multilineTextAlignment(.center)
                .padding(.top, Tokens.Space.camDenyTextGap)
        }
        .padding(.horizontal, Tokens.Space.camDenyPadX)
        .padding(.vertical, Tokens.Space.camDenyPadY)
        .frame(width: Tokens.Space.camDenyW)
        .background(
            RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                .fill(Tokens.dark.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                .stroke(Tokens.dark.line, lineWidth: 1)
        )
    }

    private var actions: some View {
        VStack(spacing: 0) {
            CButton(text: "去系统设置", action: onOpenSettings)
            ghostButton
                .padding(.top, Tokens.Space.camDenyGap)
            Text(pickFailed ? "照片读取失败，请重试" : "取消可回上一页")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(Tokens.dark.camhint)
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
                .padding(.top, Tokens.Space.camDenyHint)
                .onTapGesture(perform: onCancel)
        }
        .padding(.horizontal, Tokens.Space.pageX)
        .padding(.top, Tokens.Space.camRowTop)
        .padding(.bottom, Tokens.Space.camRowBottom)
        .background(Tokens.dark.cam)
    }

    /// D 稿 P17「改用相册」：白 30% 描边 + 白 85% 文字的幽灵按钮，底色是相机黑。
    private var ghostButton: some View {
        Button(action: onPick) {
            Text("改用相册")
                .font(.system(size: Tokens.Font.n135))
                .foregroundStyle(Tokens.dark.camfg)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Tokens.Space.ghostY)
                .background(
                    RoundedRectangle(cornerRadius: Tokens.Radius.r5)
                        .stroke(Tokens.dark.camghostline, lineWidth: 1)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(CPressDimStyle())
    }
}
