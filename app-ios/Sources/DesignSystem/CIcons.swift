import SwiftUI
import CangyuTokens

/// 内联矢量图标（设计规范 §6）：一律 Path 自绘，viewBox 24 归一化缩放。
/// 禁 emoji、禁 SF Symbols（语义不精确）、禁图标字体（开发规范 §7 通用规则）。
/// 路径与试色稿 v0.10 / 构件状态基线板 v2 的同名 SVG 一致（规范 1.3 事实源）。
public enum CIcons {

    /// 图标语义。边长只取 Tokens.Icon 八档，描边宽度按规范 §6 尺寸-描边表由调用方给定。
    public enum Kind: String, CaseIterable, Sendable {
        case plus
        case check
        case warn
        case err
        case info
        case search
        case chevron
        case book
        /// P04 顶部关闭 / 输入框清空（高保真稿 A P04 顶部 X）。
        case close
        /// P05 详情返回（高保真稿 A P05 顶部返回）。
        case back
        /// P05 读音播放预留位（高保真稿 A P05 成语名右侧图标）。
        case sound
    }

    /// 生成按 size 缩放后的路径（viewBox 24 → size，规范 §6）。
    public static func path(_ kind: Kind, size: CGFloat) -> Path {
        let scale = size / 24
        return basePath(kind).applying(CGAffineTransform(scaleX: scale, y: scale))
    }

    /// viewBox 24 原始路径（坐标即 SVG d 属性的落地值）。
    /// 按图标族拆成两组，避免单函数分支过多（SwiftLint cyclomatic_complexity）。
    private static func basePath(_ kind: Kind) -> Path {
        switch kind {
        case .plus, .check, .warn, .err, .info, .search:
            return glyphPath(kind)
        case .chevron, .book, .close, .back, .sound:
            return navPath(kind)
        }
    }

    /// 基础语义图标（加号 / 勾选 / 警示 / 错误 / 信息 / 搜索）。
    private static func glyphPath(_ kind: Kind) -> Path {
        var path = Path()
        switch kind {
        case .plus:
            // M12 5v14M5 12h14
            path.move(to: CGPoint(x: 12, y: 5))
            path.addLine(to: CGPoint(x: 12, y: 19))
            path.move(to: CGPoint(x: 5, y: 12))
            path.addLine(to: CGPoint(x: 19, y: 12))
        case .check:
            // M5 12l4.5 4.5L19 7（规范 5.15 勾选标记）
            path.move(to: CGPoint(x: 5, y: 12))
            path.addLine(to: CGPoint(x: 9.5, y: 16.5))
            path.addLine(to: CGPoint(x: 19, y: 7))
        case .warn:
            // M12 4l8 15H4z + M12 9v4 + M12 16.5v.5
            path.move(to: CGPoint(x: 12, y: 4))
            path.addLine(to: CGPoint(x: 20, y: 19))
            path.addLine(to: CGPoint(x: 4, y: 19))
            path.closeSubpath()
            path.move(to: CGPoint(x: 12, y: 9))
            path.addLine(to: CGPoint(x: 12, y: 13))
            path.move(to: CGPoint(x: 12, y: 16.5))
            path.addLine(to: CGPoint(x: 12, y: 17))
        case .err:
            // circle cx12 cy12 r9 + M9 9l6 6M15 9l-6 6
            path.addEllipse(in: CGRect(x: 3, y: 3, width: 18, height: 18))
            path.move(to: CGPoint(x: 9, y: 9))
            path.addLine(to: CGPoint(x: 15, y: 15))
            path.move(to: CGPoint(x: 15, y: 9))
            path.addLine(to: CGPoint(x: 9, y: 15))
        case .info:
            // circle cx12 cy12 r9 + M12 11v5 + M12 8v.5
            path.addEllipse(in: CGRect(x: 3, y: 3, width: 18, height: 18))
            path.move(to: CGPoint(x: 12, y: 11))
            path.addLine(to: CGPoint(x: 12, y: 16))
            path.move(to: CGPoint(x: 12, y: 8))
            path.addLine(to: CGPoint(x: 12, y: 8.5))
        case .search:
            // circle cx11 cy11 r6.5 + M20 20l-4.6-4.6（P03 顶栏搜索）
            path.addEllipse(in: CGRect(x: 4.5, y: 4.5, width: 13, height: 13))
            path.move(to: CGPoint(x: 20, y: 20))
            path.addLine(to: CGPoint(x: 15.4, y: 15.4))
        default:
            break
        }
        return path
    }

    /// 导航类图标（右尖角 / 书本 / 关闭 / 返回 / 读音）。
    private static func navPath(_ kind: Kind) -> Path {
        var path = Path()
        switch kind {
        case .chevron:
            // M9 5l7 7-7 7（范围卡「改范围」的右尖角）
            path.move(to: CGPoint(x: 9, y: 5))
            path.addLine(to: CGPoint(x: 16, y: 12))
            path.addLine(to: CGPoint(x: 9, y: 19))
        case .book:
            // M4 4h6l2 2 2-2h6v16h-6l-2 2-2-2H4z + M12 6v16（P01 品牌块书本）
            path.move(to: CGPoint(x: 4, y: 4))
            path.addLine(to: CGPoint(x: 10, y: 4))
            path.addLine(to: CGPoint(x: 12, y: 6))
            path.addLine(to: CGPoint(x: 14, y: 4))
            path.addLine(to: CGPoint(x: 20, y: 4))
            path.addLine(to: CGPoint(x: 20, y: 20))
            path.addLine(to: CGPoint(x: 14, y: 20))
            path.addLine(to: CGPoint(x: 12, y: 22))
            path.addLine(to: CGPoint(x: 10, y: 20))
            path.addLine(to: CGPoint(x: 4, y: 20))
            path.closeSubpath()
            path.move(to: CGPoint(x: 12, y: 6))
            path.addLine(to: CGPoint(x: 12, y: 22))
        case .close:
            // M6 6l12 12M18 6l-12 12（P04 关闭 / 清空）
            path.move(to: CGPoint(x: 6, y: 6))
            path.addLine(to: CGPoint(x: 18, y: 18))
            path.move(to: CGPoint(x: 18, y: 6))
            path.addLine(to: CGPoint(x: 6, y: 18))
        case .back:
            // M15 5l-7 7 7 7（P05 返回）
            path.move(to: CGPoint(x: 15, y: 5))
            path.addLine(to: CGPoint(x: 8, y: 12))
            path.addLine(to: CGPoint(x: 15, y: 19))
        case .sound:
            // M4 13v6h6l10-10-6-6z + M9 5l6 6（P05 读音预留）
            path.move(to: CGPoint(x: 4, y: 13))
            path.addLine(to: CGPoint(x: 4, y: 19))
            path.addLine(to: CGPoint(x: 10, y: 19))
            path.addLine(to: CGPoint(x: 20, y: 9))
            path.addLine(to: CGPoint(x: 14, y: 3))
            path.closeSubpath()
            path.move(to: CGPoint(x: 9, y: 5))
            path.addLine(to: CGPoint(x: 15, y: 11))
        default:
            break
        }
        return path
    }
}

/// 描边图标视图：fill:none、线性描边（规范 §6）。
/// 颜色由调用方传 token（CangyuTheme.colors(for:)）；图标与文字同色，不另取第二种强调色。
public struct CIcon: View {
    private let kind: CIcons.Kind
    private let size: CGFloat
    private let strokeWidth: CGFloat
    private let color: Color
    private let lineCap: CGLineCap

    /// - Parameters:
    ///   - kind: 图标语义。
    ///   - size: 边长，取 Tokens.Icon 档位（26/24/21/18/15/14/13/12）。
    ///   - strokeWidth: 描边宽度，按规范 §6 尺寸-描边表（如 18→1.8、13→1.9、12→2）。
    ///   - color: token 色。
    ///   - lineCap: 通用 .ic 用 .square，其余构件 SVG 沿用默认 .butt（规范 §6）。
    public init(
        _ kind: CIcons.Kind,
        size: CGFloat,
        strokeWidth: CGFloat,
        color: Color,
        lineCap: CGLineCap = .butt
    ) {
        self.kind = kind
        self.size = size
        self.strokeWidth = strokeWidth
        self.color = color
        self.lineCap = lineCap
    }

    public var body: some View {
        CIconShape(kind: kind)
            .stroke(color, style: StrokeStyle(lineWidth: strokeWidth, lineCap: lineCap))
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// 把 viewBox 24 路径铺进 Shape 的 rect，避免 Path 直接作 View 时按包围盒居中产生偏移。
private struct CIconShape: Shape {
    let kind: CIcons.Kind

    func path(in rect: CGRect) -> Path {
        CIcons.path(kind, size: min(rect.width, rect.height))
    }
}
