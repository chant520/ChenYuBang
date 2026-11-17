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
