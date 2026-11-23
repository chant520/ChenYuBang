import SwiftUI
import UIKit
import CangyuData
import CangyuDesignSystem
import CangyuTokens

/// P10 纯渲染层（信息架构 v1.4 P10 ①–④ / 高保真稿 B P10）：
/// 页名与编辑删除入口、原图位、题干与四选项；⑤–⑩ 与底部删除见 `P10UserExampleBlocks`；
/// 编辑态表单见 `P10UserExampleEdit`、二次确认弹层见 `P10UserExampleDeleteConfirm`、视图模型见 `P10UserExampleViewModel`。
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
        .overlay {
            // 归属手选：整屏复用 P04 搜索（与 P20「手动挑成语」同一浮层、同一注入形态）。
            if state.showsIdiomPicker {
                actions.idiomPicker(actions.onPickIdiom, actions.onDismissIdiomPicker)
            }
        }
        .overlay {
            // 单条二次确认（开发规范 §8.1.1）：覆起后底部红行不再是可见层的那一处红。
            if state.confirmDelete {
                P10UserExampleDeleteConfirm(
                    isMistake: state.isMistake,
                    deleting: state.deleting,
                    onConfirm: actions.onConfirmDelete,
                    onDismiss: actions.onDismissDelete
                )
            }
        }
    }

    /// ①：返回 + 页名 + 编辑 / 删除（只读态）或取消 / 保存（编辑态）。
    /// 编辑态的返回箭头与「取消」同义：草稿不落库，直接回只读态。
    private var header: some View {
        let readonly = state.detail != nil && !state.loading && !state.failed
        return HStack(spacing: Tokens.Space.lmetaGap) {
            Button(action: state.editing ? actions.onCancelEdit : actions.onBack) {
                CIcon(.back, size: Tokens.Icon.n21, strokeWidth: 1.6, color: colors.text2)
                    .frame(width: Tokens.Space.hitMin, height: Tokens.Space.hitMin)
                    .contentShape(Rectangle())
            }
            .buttonStyle(CPressDimStyle())
            .accessibilityLabel(state.editing ? "取消编辑" : "返回")
            Text("录入题详情")
                .font(.system(size: Tokens.Font.n15, weight: .semibold))
                .foregroundStyle(colors.head)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            if state.editing {
                CButton(text: UserExampleEditCopy.cancel, variant: .ghost,
                        enabled: !state.saving, action: actions.onCancelEdit)
                    .fixedSize()
                CButton(text: UserExampleEditCopy.save, enabled: state.draft?.canSave ?? false,
                        loading: state.saving, action: actions.onSave)
                    .fixedSize()
            } else {
                CButton(text: UserExampleEditCopy.edit, variant: .ghost,
                        enabled: readonly, action: actions.onEdit)
                    .fixedSize()
                CButton(text: UserExampleEditCopy.delete, variant: .ghost,
                        enabled: readonly, action: actions.onDelete)
                    .fixedSize()
            }
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
            if let issue = state.issue {
                issueBar(issue)
            }
            imageSlot(detail)
            if let draft = state.draft {
                // 编辑态：原图位保留（只读、可放大），题面与 ⑤–⑩ 换成可编表单（§8.1.1）。
                P10UserExampleEditForm(draft: draft, saving: state.saving, actions: actions.edit)
            } else {
                stemCard(detail)
                P10UserExampleBlocks(state: state, detail: detail, actions: actions, colors: colors)
            }
        }
    }

    /// 校验提示走中性条（点「知道了」消失）、写库失败走红错误条，与 P20 同一口径。
    private func issueBar(_ issue: UserExampleIssue) -> some View {
        CBar(
            kind: issue.kind == .error ? .error : .info,
            title: issue.message,
            actionTitle: UserExampleEditCopy.gotIt,
            onAction: actions.onDismissIssue
        )
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

/// P10 录入题详情（二级页，从 P08 平铺行或 P09 列表点入；P05 数字入口在工作包 2.10 接）。
///
/// 编辑与删除（工作包 2.8）在本页同页切态：写库经注入的 `UserExampleWriting`，
/// 手选归属复用 P04 搜索浮层；成功保存或删除后先通知 app 层刷新来源页。
public struct P10UserExampleScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var model: UserExampleViewModel

    private let onBack: () -> Void
    private let onOpenIdiom: (String) -> Void
    private let libraryReader: IdiomLibraryReader

    @MainActor
    public init(
        id: String,
        reader: UserExampleReading,
        editor: UserExampleWriting,
        libraryReader: IdiomLibraryReader,
        dataDirectory: URL?,
        onBack: @escaping () -> Void,
        onOpenIdiom: @escaping (String) -> Void,
        onContentChanged: @escaping () -> Void = {},
        onDeleted: @escaping () -> Void = {}
    ) {
        _model = StateObject(wrappedValue: UserExampleViewModel(
            id: id,
            reader: reader,
            editor: editor,
            libraryReader: libraryReader,
            dataDirectory: dataDirectory,
            onContentChanged: onContentChanged,
            onDeleted: onDeleted
        ))
        self.onBack = onBack
        self.onOpenIdiom = onOpenIdiom
        self.libraryReader = libraryReader
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
                onEdit: model.startEdit,
                onDelete: model.requestDelete,
                onCancelEdit: model.cancelEdit,
                onSave: model.save,
                onDismissIssue: model.dismissIssue,
                onOpenIdiomPicker: { model.setIdiomPicker(visible: true) },
                onDismissIdiomPicker: { model.setIdiomPicker(visible: false) },
                onPickIdiom: model.pickIdiom,
                onConfirmDelete: model.confirmDelete,
                onDismissDelete: model.dismissDelete,
                edit: UserExampleEditActions(
                    onStem: model.setStem,
                    onOption: model.setOption,
                    onAnswer: model.setAnswerText,
                    onExamType: model.setExamType,
                    onYear: model.setYearText,
                    onReason: model.setReason,
                    onAddTag: model.addTag,
                    onRemoveTag: model.removeTag,
                    onRemoveIdiom: model.removeIdiom(id:),
                    onOpenIdiomPicker: { model.setIdiomPicker(visible: true) }
                ),
                idiomPicker: { onPick, onDismiss in
                    AnyView(P04SearchScreen(
                        reader: libraryReader,
                        onClose: onDismiss,
                        onOpenIdiom: onPick
                    ))
                }
            ),
            colors: colors,
            image: model.image
        )
        .task { await model.load() }
    }
}
