import SwiftUI
import CangyuTokens

/// 加载转圈（规范 5.0 加载统一口径 / 5.11 / §6）：
/// 14pt、描边 2pt、右边透明（即 270° 弧）、0.8s 匀速无限旋转。
/// 颜色随当前文字色（继承 foregroundStyle，等价 CSS currentColor）。
/// 系统 reduce motion 开启时静止（开发规范 §9.3）。
public struct CSpinner: View {
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @Environment(\.cangyuReduceMotionOverride) private var reduceMotionOverride
    @State private var angle: Double = 0

    public init() {}

    /// 系统 reduce-motion 或覆盖开关任一为真即静止（系统键只读，见 Theme.swift）。
    private var reduceMotion: Bool { reduceMotionOverride || systemReduceMotion }

    public var body: some View {
        Circle()
            // 270° 弧（规范 5.0 加载口径：右边透明 = 3/4 圆）。
            .trim(from: 0, to: 0.75)
            // 描边 2pt（规范 §6 尺寸-描边表：spinner 14 → 2）。
            .stroke(style: StrokeStyle(lineWidth: 2))
            // CSS 为 border-box：14px 含 2px 描边，故内缩描边的一半。
            .padding(1)
            .frame(width: Tokens.Icon.n14, height: Tokens.Icon.n14)
            .rotationEffect(.degrees(angle))
            .accessibilityHidden(true)
            .onAppear { updateSpin() }
            .onChange(of: reduceMotion) { _ in updateSpin() }
    }

    /// 0.8s 匀速无限旋转（规范 5.0）；reduce motion 时归零静止。
    private func updateSpin() {
        if reduceMotion {
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) { angle = 0 }
        } else {
            withAnimation(.linear(duration: 0.8).repeatForever(autoreverses: false)) {
                angle = 360
            }
        }
    }
}
