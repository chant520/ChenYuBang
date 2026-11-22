import SwiftUI
import UIKit
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P10 纯渲染层（信息架构 v1.4 P10 ①–④ / 高保真稿 B P10）：
/// 页名与编辑删除占位、原图位、题干与四选项；⑤–⑩ 与底部删除见 `P10UserExampleBlocks`。
/// 区块 ⑥「正确答案 + 一句解析」本版不渲染（库内无来源，见信息架构 v1.4 版内修订第 ① 条）。
public struct P10UserExampleContent: View {
    private let state: UserExampleState
    private let actions: UserExampleActions
    private let colors: TokenColors
    /// ② 原图解码结果；nil 且 `imagePath` 有值＝尚未读出或读不出。
    private let image: UIImage?
    @State private var zoomed = false

    public init(
        state: UserExampleState,
        actions: UserExampleActions,
        colors: TokenColors,
        image: UIImage? = nil
    ) {
        self.state = state
        self.actions = actions
        self.colors = colors
        self.image = image
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                header
                content
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.top, Tokens.Space.contentTop)
            .padding(.bottom, Tokens.Space.block)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
        .overlay { if zoomed { zoomLayer } }
    }

    /// ①：返回 + 页名 + 编辑 / 删除（本版在位、置灰，动作属工作包 2.8）。
    private var header: some View {
        HStack(spacing: Tokens.Space.lmetaGap) {
            Button(action: actions.onBack) {
                CIcon(.back, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                    .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                    .contentShape(Rectangle())
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel("返回")
            Text("录入题详情")
                .font(.system(size: Tokens.Font.n15, weight: .semibold))
                .foregroundStyle(colors.head)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            CButton(text: "编辑", variant: .ghost, enabled: false, action: actions.onEdit)
                .fixedSize()
            CButton(text: "删除", variant: .ghost, enabled: false, action: actions.onDelete)
                .fixedSize()
        }
    }

    @ViewBuilder
    private var content: some View {
        if state.failed {
            CBar(
                kind: .warn,
                title: "录入题读取失败，请重试",
                subtitle: "只重新读取本机数据，不影响已录入的题目。",
                actionTitle: "重试",
                onAction: actions.onRetry
            )
        } else if state.loading {
            HStack {
                Spacer(minLength: 0)
                CSpinner()
                Spacer(minLength: 0)
            }
        } else if state.missing || state.detail == nil {
            CEmptyState(
                title: "没有找到这道录入题",
                description: "该题已不在本机题库中，可返回上一个页面换一道看。"
            )
        } else if let detail = state.detail {
            imageSlot(detail)
            stemCard(detail)
            P10UserExampleBlocks(state: state, detail: detail, actions: actions, colors: colors)
        }
    }

    /// ②：原图缩略图（可点放大）；未存图或文件读不出＝橙占位「原图已不在」（信息架构 v1.4 §5.4）。
    @ViewBuilder
    private func imageSlot(_ detail: UserExampleDetail) -> some View {
        let showZoom = { zoomed = true }
        if let image, detail.imagePath != nil {
            Button(action: showZoom) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity)
                    .frame(height: Tokens.Space.imgphH)
                    .background(RoundedRectangle(cornerRadius: Tokens.Radius.r6).fill(colors.surface))
                    .clipShape(RoundedRectangle(cornerRadius: Tokens.Radius.r6))
                    .overlay(
                        RoundedRectangle(cornerRadius: Tokens.Radius.r6)
                            .strokeBorder(colors.line, lineWidth: 1)
                    )
                    .contentShape(Rectangle())
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel("查看原图")
        } else if detail.imagePath == nil || state.imageUnreadable {
            // 信息架构 v1.4 §5.4 的影响列只把「仍可重做」给标错的，未标错的不能这么写。
            let subtitle = state.isMistake
                ? "文件可能被清理；题干与选项仍可查看与重做。"
                : "文件可能被清理；题干与选项仍可查看。"
            CImagePlaceholder(kind: .warn, title: "原图已不在", subtitle: subtitle)
        } else {
            CImagePlaceholder(kind: .skeleton)
        }
    }

    /// ③ 题干 + ④ 四个选项；重做态下选项可点选（缺项按「暂无」占位，不改写题面）。
    private func stemCard(_ detail: UserExampleDetail) -> some View {
        CCard {
            if detail.isMistake {
                HStack(spacing: Tokens.Space.lmetaGap) {
                    CBadge(text: "错题", kind: .error)
                    Spacer(minLength: 0)
                }
                .padding(.bottom, Tokens.Space.lmetaTop)
            }
            CLabel("题干")
            Text(detail.stem)
                .font(.system(size: Tokens.Font.n13, weight: .semibold))
                .foregroundStyle(colors.text)
                .padding(.top, Tokens.Space.lsubTop)
                .fixedSize(horizontal: false, vertical: true)
            ForEach(Array(MistakeBrowse.letters.enumerated()), id: \.offset) { index, letter in
                optionRow(index, letter, index < detail.options.count ? detail.options[index] : nil)
            }
        }
    }

    private func optionRow(_ index: Int, _ letter: String, _ option: String?) -> some View {
        let selected = state.rerolling && state.picked == index
        let label = HStack(alignment: .top, spacing: Tokens.Space.lmetaGap) {
            Text(letter)
                .font(.system(size: Tokens.Font.n13, weight: .semibold))
                .foregroundStyle(selected ? colors.accent : colors.text3)
            Text(option ?? MistakeBrowse.placeholder)
                .font(.system(size: Tokens.Font.n13))
                .foregroundStyle(colors.text2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        return Group {
            if state.rerolling {
                Button(action: { actions.onPickOption(index) }, label: {
                    label.contentShape(Rectangle())
                })
                .buttonStyle(CPressDimStyle())
                .accessibilityLabel("选项 \(letter)")
                .accessibilityValue(selected ? "已选中" : "")
            } else {
                label
            }
        }
        .padding(.vertical, Tokens.Space.ghostY)
        .padding(.horizontal, Tokens.Space.lrowX)
        .background(
            RoundedRectangle(cornerRadius: Tokens.Radius.r5)
                .fill(selected ? colors.tintbg : Color.clear)
        )
        .padding(.top, Tokens.Space.lmetaTop)
    }

    /// 原图放大层：点任意处关闭，不改变任何状态（与 P19 放大层同一口径）。
    private var zoomLayer: some View {
        ZStack {
            Color.black.opacity(0.85)
                .onTapGesture { zoomed = false }
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .padding(Tokens.Space.pageX)
                    .allowsHitTesting(false)
            }
        }
        .ignoresSafeArea()
        .accessibilityLabel("原图放大，点任意处关闭")
    }
}

/// 后台解码结果装箱（`UIImage` 生成后只读，不入 UI，同 `CropSource` 口径）。
private struct DecodedOriginal: @unchecked Sendable {
    let image: UIImage?
}

/// P10 页面状态与行为（读库失败＝橙条可重试，与 P08 / P09 同一口径）。
@MainActor
public final class UserExampleViewModel: ObservableObject {
    @Published public private(set) var state = UserExampleState()
    /// ② 原图解码结果；与 `state.imageUnreadable` 合起来决定图片位形态。
    @Published public private(set) var image: UIImage?

    private let id: String
    private let reader: UserExampleReading
    /// 本机数据根目录（`images/…` 相对路径的解析基准）；nil＝不尝试读图。
    private let dataDirectory: URL?

    public init(id: String, reader: UserExampleReading, dataDirectory: URL?) {
        self.id = id
        self.reader = reader
        self.dataDirectory = dataDirectory
    }

    public func load() async {
        state.loading = true
        state.failed = false
        do {
            guard let detail = try reader.userExample(id: id) else {
                state.missing = true
                state.detail = nil
                image = nil
                state.loading = false
                return
            }
            state.missing = false
            state.detail = detail
            state.loading = false
            await loadImage(from: detail.imagePath)
        } catch {
            state.detail = nil
            state.loading = false
            state.failed = true
        }
    }

    /// 读图在后台队列解码，失败（文件丢失或解不出）只影响图片位，不影响题干与重做。
    private func loadImage(from relativePath: String?) async {
        image = nil
        state.imageUnreadable = false
        guard let relativePath, let dataDirectory else {
            state.imageUnreadable = relativePath != nil
            return
        }
        let url = dataDirectory.appendingPathComponent(relativePath)
        // UIImage 非 Sendable，与 P18 `CropSource` 同一口径：后台解码、装箱回主线程。
        let decoded = await Task.detached(priority: .userInitiated) {
            DecodedOriginal(image: UIImage(contentsOfFile: url.path))
        }.value
        image = decoded.image
        state.imageUnreadable = decoded.image == nil
    }

    public func toggleAnswer() {
        state.revealed.toggle()
    }

    /// 重做入口只在非重做态显示；进入后清掉本轮选择，遮住首次作答。
    public func startReroll() {
        state.rerolling = true
        state.picked = nil
        state.checked = false
    }

    public func pick(_ index: Int) {
        guard state.rerolling, (0..<4).contains(index) else { return }
        state.picked = index
        state.checked = false
    }

    public func check() {
        guard state.picked != nil else { return }
        state.checked = true
    }

    public func exitReroll() {
        state.rerolling = false
        state.picked = nil
        state.checked = false
    }
}

/// P10 录入题详情（二级页，从 P08 平铺行或 P09 列表点入；P05 数字入口在工作包 2.10 接）。
public struct P10UserExampleScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: UserExampleViewModel

    private let onBack: () -> Void
    private let onOpenIdiom: (String) -> Void

    @MainActor
    public init(
        id: String,
        reader: UserExampleReading,
        dataDirectory: URL?,
        onBack: @escaping () -> Void,
        onOpenIdiom: @escaping (String) -> Void
    ) {
        _model = StateObject(
            wrappedValue: UserExampleViewModel(id: id, reader: reader, dataDirectory: dataDirectory)
        )
        self.onBack = onBack
        self.onOpenIdiom = onOpenIdiom
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        return P10UserExampleContent(
            state: model.state,
            actions: UserExampleActions(
                onBack: onBack,
                onRetry: { Task { await model.load() } },
                onToggleAnswer: model.toggleAnswer,
                onPickOption: model.pick,
                onStartReroll: model.startReroll,
                onCheck: model.check,
                onExitReroll: model.exitReroll,
                onOpenIdiom: onOpenIdiom,
                onEdit: {},
                onDelete: {}
            ),
            colors: colors,
            image: model.image
        )
        .task { await model.load() }
    }
}
