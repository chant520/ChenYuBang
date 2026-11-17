import SwiftUI

/// 按下反馈（设计规范 5.0，v1.3 收编）：可点构件按下时整体亮度 ×0.9，松开即恢复；
/// 不改布局、不另取色 token。CSS 口径为 filter:brightness(.9)，iOS 用 colorMultiply 等效压暗。
/// 按钮 / 列表行 / 分段项 / 勾选行 / FAB 共用此风格。
struct CPressDimStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        // Color(white: 0.9) 即规范 5.0 的压暗系数；1.0 是 colorMultiply 的单位元（未按下不压暗）。
        // 二者都不是设计色值，故不走 token（硬规则的唯一例外）。
        configuration.label
            .colorMultiply(Color(white: configuration.isPressed ? 0.9 : 1.0))
    }
}
