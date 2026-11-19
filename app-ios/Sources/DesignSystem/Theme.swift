import SwiftUI
import CangyuTokens

/// 颜色只从 Tokens 取；页面里禁止出现十六进制（规范 §6 / §12.2）。
public enum CangyuTheme {
    public static func colors(for scheme: ColorScheme) -> TokenColors {
        scheme == .dark ? Tokens.dark : Tokens.light
    }
}

public extension View {
    func cangyuBackground(_ scheme: ColorScheme) -> some View {
        background(CangyuTheme.colors(for: scheme).page)
    }
}

/// 动效降级覆盖（开发规范 §9.3）：SwiftUI 的 `.accessibilityReduceMotion` 是只读环境键，
/// 进程内无法写入，快照 / 预览需可写的同义开关；构件以「系统值 ∨ 覆盖值」判定是否减弱动效，
/// 生产环境默认 false，不改变系统无障碍语义。
private struct ReduceMotionOverrideKey: EnvironmentKey {
    static let defaultValue = false
}

public extension EnvironmentValues {
    var cangyuReduceMotionOverride: Bool {
        get { self[ReduceMotionOverrideKey.self] }
        set { self[ReduceMotionOverrideKey.self] = newValue }
    }
}
