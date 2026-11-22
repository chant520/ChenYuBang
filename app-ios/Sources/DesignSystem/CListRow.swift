import SwiftUI
import CangyuTokens

/// 列表容器（规范 5.13）：surface 底 + 1px line 描边 + 圆角 6pt + 溢出裁切。
/// 行底 1px line2 分隔由 CListRow.showsDivider 控制（末行置 false）。
public struct CList<Content: View>: View {
    @Environment(\.colorScheme) private var colorScheme

    private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(spacing: 0) {
            content
        }
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: Tokens.Radius.r6).fill(colors.surface))
        .overlay(
            RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                .strokeBorder(colors.line, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Tokens.Radius.r6))
    }
}

/// 列表行（规范 5.13，状态矩阵：默认 / 选中 / 禁用 / 带徽标）。
///
/// 行 padding 13pt 16pt；主词 14pt/600 + 拼音 11pt（baseline 对齐、gap 8pt）；
/// 副文 12pt/1.6 text2 上距 5pt；元信息 11pt text3 上距 6pt、gap 8pt；
/// 右侧文字 11pt text3 靠右（按页面 `.lt1 > .r`，挂在首行末尾，元信息行仍占满整行宽）。
/// 主词传 nil（`stem:` 便捷 init）即题干行：无 `.lt1`，副文顶格起排，
/// 对应全页稿 v2 P09 / P08 平铺视图的 `.lrow > .lsub[style=margin:0] + .lmeta`。
/// 选中：底 tintbg、主词 accent；禁用：底 disbg、全文 disfg（元信息由调用方挂中性标签）。
/// 整行可点（action 可选），按下按规范 5.0 亮度 ×0.9。
///
/// 容器口径（surface + line + r6 + 溢出裁切）由 CList 承担；行底 1px line2 分隔由
/// showsDivider 控制（末行置 false），与 Android CList / CListRow 对齐。
/// 元信息的 flex-wrap 换行在 iOS 15 无对应 Layout API，调用方需自行分组（见开发规范 §7 平台映射）。
public struct CListRow<Meta: View, Trailing: View>: View {
    @Environment(\.colorScheme) private var colorScheme

    /// 主词；nil 表示该行无主词行，从副文起排（规范 5.13 的题干行形态）。
    private let name: String?
    private let pinyin: String
    private let sub: String?
    private let selected: Bool
    private let disabled: Bool
    private let action: (() -> Void)?
    private let showsDivider: Bool
    private let meta: Meta
    private let trailing: Trailing

    /// - Parameters:
    ///   - name: 主词（14pt/600）；传 nil 表示无主词行，行从副文起排（题干行形态）。
    ///   - pinyin: 拼音（11pt text3，与主词 baseline 对齐）。
    ///   - sub: 可选副文（12pt/1.6 text2）。
    ///   - selected: 选中态（底 tintbg、主词 accent）。
    ///   - disabled: 禁用态（底 disbg、全文 disfg，不可点）。
    ///   - action: 整行点击回调；nil 时为静态行。
    ///   - showsDivider: 是否画行底 1px line2 分隔，默认 true（末行由容器置 false）。
    ///   - meta: 元信息内容（挂标签 / 徽标，规范 5.2 / 5.6）。
    ///   - trailing: 右侧内容（11pt text3、靠右，挂首行末尾）。
    public init(
        name: String?,
        pinyin: String = "",
        sub: String? = nil,
        selected: Bool = false,
        disabled: Bool = false,
        action: (() -> Void)? = nil,
        showsDivider: Bool = true,
        @ViewBuilder meta: () -> Meta,
        @ViewBuilder trailing: () -> Trailing
    ) {
        self.name = name
        self.pinyin = pinyin
        self.sub = sub
        self.selected = selected
        self.disabled = disabled
        self.action = action
        self.showsDivider = showsDivider
        self.meta = meta()
        self.trailing = trailing()
    }

    public var body: some View {
        if let action {
            Button(action: action, label: { rowContent })
                .buttonStyle(CPressDimStyle())
                .disabled(disabled)
                .contentShape(Rectangle())
        } else {
            rowContent
        }
    }

    private var rowContent: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        // 行底分隔线计入行盒高度（CSS border-bottom 口径），故用 VStack 而非 overlay。
        return VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                if let name {
                    // 首行：主词 + 拼音（+ 可选右侧文字），对齐页面 `.lt1`（flex / baseline / gap 8）。
                    HStack(alignment: .firstTextBaseline, spacing: Tokens.Space.lt1Gap) {
                        Text(name)
                            .font(.system(size: Tokens.Font.n14, weight: .semibold))
                            // letter-spacing .01em（规范 5.13 `.lname`）。
                            .tracking(Tokens.Font.n14 * 0.01)
                            .foregroundStyle(nameColor(colors))
                        Text(pinyin)
                            .font(.system(size: Tokens.Font.n11))
                            .foregroundStyle(disabled ? colors.disfg : colors.text3)
                        if Trailing.self != EmptyView.self {
                            // 页面 `.r { margin-left:auto }`：右侧文字挂在首行末尾。
                            Spacer(minLength: 0)
                            trailing
                                .font(.system(size: Tokens.Font.n11))
                                .foregroundStyle(disabled ? colors.disfg : colors.text3)
                                // 右侧文字优先占位、主词换行；AX3 下不裁切（开发规范 §9.3）。
                                .layoutPriority(1)
                        }
                    }
                }
                if let sub {
                    Text(sub)
                        .font(.system(size: Tokens.Font.n12))
                        .cLineHeight(1.6, size: Tokens.Font.n12)
                        .foregroundStyle(disabled ? colors.disfg : colors.text2)
                        // 无主词行时副文即首行，页面写作 `style="margin:0"`（全页稿 v2 P09）。
                        .padding(.top, name == nil ? 0 : Tokens.Space.lsubTop)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if Meta.self != EmptyView.self {
                    HStack(spacing: Tokens.Space.lmetaGap) {
                        meta
                    }
                    .font(.system(size: Tokens.Font.n11))
                    .foregroundStyle(disabled ? colors.disfg : colors.text3)
                    .padding(.top, Tokens.Space.lmetaTop)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(.vertical, Tokens.Space.lrowY)
            .padding(.horizontal, Tokens.Space.lrowX)
            .frame(maxWidth: .infinity, alignment: .leading)
            if showsDivider {
                Rectangle()
                    .fill(colors.line2)
                    .frame(height: 1)
            }
        }
        .background(rowBackground(colors))
        .accessibilityElement(children: .combine)
    }

    /// 禁用优先于选中（二者都是行底覆盖，规范未定义叠加态）；默认透明，由 CList 提供 surface 底。
    private func rowBackground(_ colors: TokenColors) -> Color {
        if disabled {
            return colors.disbg
        }
        return selected ? colors.tintbg : Color.clear
    }

    private func nameColor(_ colors: TokenColors) -> Color {
        if disabled {
            return colors.disfg
        }
        return selected ? colors.accent : colors.text
    }
}

extension CListRow where Trailing == EmptyView {
    /// 题干行（规范 5.13 无主词形态）：题干按副文样式呈现，其下挂元信息。
    /// P08「按录入时间 / 按考试类型」与 P09 录入题列表用此形态。
    public init(
        stem: String,
        selected: Bool = false,
        disabled: Bool = false,
        action: (() -> Void)? = nil,
        showsDivider: Bool = true,
        @ViewBuilder meta: () -> Meta
    ) {
        self.init(
            name: nil,
            sub: stem,
            selected: selected,
            disabled: disabled,
            action: action,
            showsDivider: showsDivider,
            meta: meta,
            trailing: { EmptyView() }
        )
    }

    /// 无右侧内容的列表行。
    public init(
        name: String,
        pinyin: String,
        sub: String? = nil,
        selected: Bool = false,
        disabled: Bool = false,
        action: (() -> Void)? = nil,
        showsDivider: Bool = true,
        @ViewBuilder meta: () -> Meta
    ) {
        self.init(
            name: name,
            pinyin: pinyin,
            sub: sub,
            selected: selected,
            disabled: disabled,
            action: action,
            showsDivider: showsDivider,
            meta: meta,
            trailing: { EmptyView() }
        )
    }
}

extension CListRow where Meta == EmptyView, Trailing == EmptyView {
    /// 只有主词与拼音（可选副文）的列表行。
    public init(
        name: String,
        pinyin: String,
        sub: String? = nil,
        selected: Bool = false,
        disabled: Bool = false,
        action: (() -> Void)? = nil,
        showsDivider: Bool = true
    ) {
        self.init(
            name: name,
            pinyin: pinyin,
            sub: sub,
            selected: selected,
            disabled: disabled,
            action: action,
            showsDivider: showsDivider,
            meta: { EmptyView() },
            trailing: { EmptyView() }
        )
    }
}
