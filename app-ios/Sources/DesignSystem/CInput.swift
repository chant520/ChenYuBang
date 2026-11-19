import SwiftUI
import CangyuTokens

/// 输入框状态（规范 5.3）。命名与 Android 侧对齐。
public enum CInputState: String, CaseIterable, Sendable {
    /// 默认：inputbg / 1px line / 文字 text / 占位 text3。
    case `default`
    /// 聚焦：描边 accent + 外圈 2pt tintbg 柔光；由 @FocusState 自动呈现。
    case focus
    /// 校验错误：描边 errline / 文字 errfg + 框下错误提示；由外部传入。
    case error
    /// 禁用：disbg / disfg / 1px disline，padding 10px 12px、字号 12.5px。
    case disabled
}

/// 输入框（规范 5.3，状态矩阵：默认 / 聚焦 / 校验错误 / 禁用）。
/// 三态几何一致（r5、min-height 42pt、padding 11pt 12pt、字号 12pt），
/// 切换只改描边与文字色，不引起布局跳动；聚焦柔光画在框外，同样不占布局。
public struct CInput: View {
    @Environment(\.colorScheme) private var colorScheme
    @Binding private var text: String
    @FocusState private var isFocused: Bool

    private let state: CInputState
    private let placeholder: String?
    private let errorHint: String?
    private let autoFocus: Bool
    private let clearAction: (() -> Void)?

    /// - Parameters:
    ///   - text: 文本绑定。
    ///   - state: 外部状态；.focus 也可由真实聚焦自动触发，.disabled / .error 以外部为准。
    ///   - placeholder: 占位文案（色 text3，规范 5.3）。
    ///   - errorHint: 校验错误提示，仅 .error 态渲染于框下（11.5px/1.55 errfg）。
    ///   - autoFocus: P04 搜索进页自动聚焦（信息架构 v1.3 P04 ①）。
    ///   - clearAction: 非空时显示输入框内清空按钮（P04 顶部清空）。
    public init(
        text: Binding<String>,
        state: CInputState = .default,
        placeholder: String? = nil,
        errorHint: String? = nil,
        autoFocus: Bool = false,
        clearAction: (() -> Void)? = nil
    ) {
        self._text = text
        self.state = state
        self.placeholder = placeholder
        self.errorHint = errorHint
        self.autoFocus = autoFocus
        self.clearAction = clearAction
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        // 错误提示上距 4px（构件板 v2 `.errhint` margin-top:4px，规范 5.3 框下提示）。
        VStack(alignment: .leading, spacing: 4) {
            field(colors)
            if effectiveState == .error, let errorHint {
                Text(errorHint)
                    .font(.system(size: Tokens.Font.n115))
                    .cLineHeight(1.55, size: Tokens.Font.n115)
                    .foregroundStyle(colors.errfg)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func field(_ colors: TokenColors) -> some View {
        let fontSize = effectiveState == .disabled ? Tokens.Font.n125 : Tokens.Font.n12
        let padY = effectiveState == .disabled ? Tokens.Space.inputDisY : Tokens.Space.inputY
        return ZStack(alignment: .leading) {
            if text.isEmpty, let placeholder {
                Text(placeholder)
                    .font(.system(size: fontSize))
                    .foregroundStyle(colors.text3)
                    .accessibilityHidden(true)
            }
            TextField("", text: $text)
                .textFieldStyle(.plain)
                .font(.system(size: fontSize))
                .foregroundStyle(textColor(colors))
                .focused($isFocused)
                .disabled(state == .disabled)
                .modifier(COptionalAccessibilityLabel(label: placeholder))
        }
        .padding(.vertical, padY)
        .padding(.horizontal, Tokens.Space.inputX)
        .frame(minHeight: Tokens.Space.inputMinH, alignment: .leading)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Tokens.Radius.r5).fill(background(colors)))
        .background {
            if effectiveState == .focus {
                // 外圈 2pt tintbg 柔光（规范 5.3）：等效 CSS box-shadow 0 0 0 2px，
                // 画在框外不占布局，圆角随描边外扩 2pt。
                RoundedRectangle(cornerRadius: Tokens.Radius.r5 + 2)
                    .fill(colors.tintbg)
                    .padding(-2)
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: Tokens.Radius.r5)
                .strokeBorder(border(colors), lineWidth: 1)
        )
        .overlay(alignment: .trailing) {
            if let clearAction, !text.isEmpty, state != .disabled {
                Button(action: clearAction) {
                    CIcon(.close, size: Tokens.Icon.n14, strokeWidth: 1.6, color: colors.text3)
                        .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                        .contentShape(Rectangle())
                }
                .buttonStyle(CPressDimStyle())
                .accessibilityLabel("清空")
            }
        }
        .onAppear {
            if autoFocus {
                isFocused = true
            }
        }
    }

    /// 外部状态与真实聚焦的合成：禁用 / 错误以外部为准，聚焦由 @FocusState 自动呈现。
    private var effectiveState: CInputState {
        if state == .disabled {
            return .disabled
        }
        if state == .error {
            return .error
        }
        if isFocused || state == .focus {
            return .focus
        }
        return .default
    }

    private func background(_ colors: TokenColors) -> Color {
        effectiveState == .disabled ? colors.disbg : colors.inputbg
    }

    private func border(_ colors: TokenColors) -> Color {
        switch effectiveState {
        case .default:
            return colors.line
        case .focus:
            return colors.accent
        case .error:
            return colors.errline
        case .disabled:
            return colors.disline
        }
    }

    private func textColor(_ colors: TokenColors) -> Color {
        switch effectiveState {
        case .default, .focus:
            return colors.text
        case .error:
            return colors.errfg
        case .disabled:
            return colors.disfg
        }
    }
}

/// 占位文案存在时给输入框一个语义标签（开发规范 §9.3）；无占位则不覆盖系统默认。
private struct COptionalAccessibilityLabel: ViewModifier {
    let label: String?

    func body(content: Content) -> some View {
        if let label {
            content.accessibilityLabel(label)
        } else {
            content
        }
    }
}
