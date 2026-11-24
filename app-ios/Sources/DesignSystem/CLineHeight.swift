import SwiftUI
import UIKit

extension View {
    /// CSS line-height 倍率的等效实现（规范 3.1 行高列 / 5.4 / 5.5 / 5.13）。
    /// SwiftUI 的 lineSpacing 只加「行间距」，这里补足到 字号 × 倍率 的行盒高度，
    /// 并把差值的一半作为上下 padding（等效 CSS 半行距），单行与多行高度都与稿一致。
    /// 字号与行高取规范值；UIFont 只提供系统字体的自然行高，不引入任何设计数值。
    public func cLineHeight(_ ratio: CGFloat, size: CGFloat) -> some View {
        let natural = UIFont.systemFont(ofSize: size).lineHeight
        let extra = max(0, size * ratio - natural)
        return lineSpacing(extra).padding(.vertical, extra / 2)
    }
}
