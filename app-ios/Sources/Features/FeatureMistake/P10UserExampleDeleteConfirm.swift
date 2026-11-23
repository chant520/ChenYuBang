import SwiftUI
import CangyuDesignSystem
import CangyuTokens

/// P10 编辑与删除给用户的单条提示。
///
/// 口径同 P20：输入校验（题干为空 / 归属不足一条）走中性信息条、点「知道了」消失；
/// 写库失败走红错误条（开发规范 §9.1 三级语义）。
public struct UserExampleIssue: Equatable, Sendable {
    public enum Kind: Equatable, Sendable {
        case info
        case error
    }

    public let kind: Kind
    public let message: String

    public init(kind: Kind, message: String) {
        self.kind = kind
        self.message = message
    }
}

/// 单条删除的二次确认弹层（开发规范 §8.1.1，视觉取设计规范 5.12 中部卡片）。
///
/// 标题与说明按「标错的／未标错的」两套逐字文案；「取消」由 `COverlay` 自带，
/// 确认按钮取 5.1 危险按钮样式，故本页唯一红动作在弹层覆起时仍是可见层唯一一处红。
public struct P10UserExampleDeleteConfirm: View {
    @Environment(\.colorScheme) private var colorScheme
    private let isMistake: Bool
    private let deleting: Bool
    private let onConfirm: () -> Void
    private let onDismiss: () -> Void

    public init(
        isMistake: Bool,
        deleting: Bool,
        onConfirm: @escaping () -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.isMistake = isMistake
        self.deleting = deleting
        self.onConfirm = onConfirm
        self.onDismiss = onDismiss
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        return COverlay(
            style: .dialog,
            title: isMistake ? UserExampleEditCopy.deleteMistakeTitle
                             : UserExampleEditCopy.deleteExampleTitle,
            onDismiss: onDismiss
        ) {
            Text(isMistake ? UserExampleEditCopy.deleteMistakeBody
                           : UserExampleEditCopy.deleteExampleBody)
                .font(.system(size: Tokens.Font.n12))
                .foregroundStyle(colors.text2)
                .fixedSize(horizontal: false, vertical: true)
            CButton(text: UserExampleEditCopy.delete, variant: .dangerFilled,
                    loading: deleting, action: onConfirm)
        }
    }
}
