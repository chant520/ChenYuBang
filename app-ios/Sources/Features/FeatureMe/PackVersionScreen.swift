import CangyuData
import CangyuDesignSystem
import CangyuTokens
import SwiftUI

enum PackVersionStatus: Equatable {
    case builtin
    case checking
    case downloading
    case updated
    case recoverableFailure
    case mock
}

struct PackVersionPresentation: Equatable {
    let status: PackVersionStatus
    let version: String
    let idiomCount: Int
    let exampleCount: Int
    var progress: Double = 0
    var reason: String?

    static func builtin(_ pack: ValidatedPack) -> PackVersionPresentation {
        PackVersionPresentation(status: .builtin, version: pack.version,
                                idiomCount: pack.counts.idioms, exampleCount: pack.counts.examples)
    }
}

public struct PackVersionScreen: View {
    @Environment(\.colorScheme) private var colorScheme

    private let presentation: PackVersionPresentation
    private let onBack: () -> Void
    private let onRetry: () -> Void
    private let onContinueOffline: () -> Void

    public init(pack: ValidatedPack, onBack: @escaping () -> Void) {
        presentation = .builtin(pack)
        self.onBack = onBack
        onRetry = {}
        onContinueOffline = {}
    }

    init(presentation: PackVersionPresentation, onBack: @escaping () -> Void = {},
         onRetry: @escaping () -> Void = {}, onContinueOffline: @escaping () -> Void = {}) {
        self.presentation = presentation
        self.onBack = onBack
        self.onRetry = onRetry
        self.onContinueOffline = onContinueOffline
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        ScrollView {
            VStack(alignment: .leading, spacing: Tokens.Space.block) {
                HStack(spacing: Tokens.Space.lt1Gap) {
                    Button(action: onBack) {
                        CIcon(.back, size: Tokens.Icon.n21, strokeWidth: 1.8, color: colors.text)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("返回")
                    Text("题库版本与更新")
                        .font(.system(size: Tokens.Font.n20, weight: .semibold))
                        .foregroundStyle(colors.text)
                        .accessibilityAddTraits(.isHeader)
                }

                if presentation.status == .mock {
                    mockVersionBlock(colors)
                } else {
                    builtinVersionBlock(colors)
                }
                updateArea()

                if presentation.status == .builtin {
                    Text("每次进入成语库页或例题页时自动尝试拉取一次；失败后可在本页重试或继续离线使用。")
                        .font(.system(size: Tokens.Font.n115))
                        .cLineHeight(1.65, size: Tokens.Font.n115)
                        .foregroundStyle(colors.text3)
                    Text("随安装包内置的题库作离线底座；内置包也没有时才用标注 TEST 的 mock 兜底。")
                        .font(.system(size: Tokens.Font.n115))
                        .cLineHeight(1.65, size: Tokens.Font.n115)
                        .foregroundStyle(colors.text3)
                }
                Text("只下载题库增量包，不改动本机学习记录。")
                    .font(.system(size: Tokens.Font.n115))
                    .cLineHeight(1.65, size: Tokens.Font.n115)
                    .foregroundStyle(colors.text3)
                if presentation.status == .builtin {
                    Text(Phase4Copy.offlineNotice)
                        .font(.system(size: Tokens.Font.n115))
                        .foregroundStyle(colors.text3)
                }
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.contentTop)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
    }

    private func builtinVersionBlock(_ colors: TokenColors) -> some View {
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            CCard {
                CLabel("当前题库版本")
                Text(presentation.version)
                    .font(.system(size: Tokens.Font.n30, weight: .bold))
                    .foregroundStyle(colors.head)
                    .padding(.top, Tokens.Space.lmetaTop)
                Text("内置版本，装好即可离线用")
                    .font(.system(size: Tokens.Font.n115))
                    .cLineHeight(1.65, size: Tokens.Font.n115)
                    .foregroundStyle(colors.text3)
                    .padding(.top, Tokens.Space.lsubTop)
            }

            CCard {
                CLabel("收录说明")
                (Text("成语 ") + Text(formatted(presentation.idiomCount)).bold()
                    + Text(" 条 · 真题 ") + Text(formatted(presentation.exampleCount)).bold() + Text(" 条"))
                    .font(.system(size: Tokens.Font.n13))
                    .cLineHeight(1.7, size: Tokens.Font.n13)
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lmetaTop)
                Text("覆盖国考 / 省考 / 事业编，按范围筛选后可用的条数会随之变化。")
                    .font(.system(size: Tokens.Font.n13))
                    .cLineHeight(1.7, size: Tokens.Font.n13)
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lsubTop)
                Text("条数为排版示意，实际以题库定稿为准。")
                    .font(.system(size: Tokens.Font.n115))
                    .cLineHeight(1.65, size: Tokens.Font.n115)
                    .foregroundStyle(colors.text3)
                    .padding(.top, Tokens.Space.lsubTop)
            }
        }
    }

    private func mockVersionBlock(_ colors: TokenColors) -> some View {
        CCard {
            CLabel("当前题库版本")
            Text(presentation.version)
                .font(.system(size: Tokens.Font.n30, weight: .bold))
                .foregroundStyle(colors.head)
                .padding(.top, Tokens.Space.lmetaTop)
            Text("成语 \(formatted(presentation.idiomCount)) 条 · 真题 \(formatted(presentation.exampleCount)) 条")
                .font(.system(size: Tokens.Font.n13))
                .foregroundStyle(colors.text2)
                .padding(.top, Tokens.Space.lsubTop)
            Text(presentation.reason ?? "题库未获取成功")
                .font(.system(size: Tokens.Font.n115))
                .cLineHeight(1.65, size: Tokens.Font.n115)
                .foregroundStyle(colors.text3)
                .padding(.top, Tokens.Space.lsubTop)
        }
    }

    @ViewBuilder
    private func updateArea() -> some View {
        switch presentation.status {
        case .builtin:
            CButton(text: "检查题库更新", action: onRetry)
            Text("这是全 App 唯一的主动联网入口之一")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(CangyuTheme.colors(for: colorScheme).text3)
            CCard {
                HStack {
                    CLabel("更新状态")
                    Spacer()
                    CTag(text: "已是最新版本")
                }
                Text("失败时会在这里显示「检查失败，请检查网络后重试」，版本号不变，可点重试。")
                    .font(.system(size: Tokens.Font.n115))
                    .cLineHeight(1.65, size: Tokens.Font.n115)
                    .foregroundStyle(CangyuTheme.colors(for: colorScheme).text3)
                    .padding(.top, Tokens.Space.lsubTop)
            }
        case .checking:
            CButton(text: Phase4Copy.checkingUpdate, loading: true) {}
        case .downloading:
            CButton(text: Phase4Copy.downloadingUpdate(presentation.progress), loading: true) {}
        case .updated:
            CLabel("更新状态")
            CTag(text: Phase4Copy.updateComplete)
            CButton(text: "检查题库更新", action: onRetry)
            Text("这是全 App 唯一的主动联网入口之一")
                .font(.system(size: Tokens.Font.n11))
                .foregroundStyle(CangyuTheme.colors(for: colorScheme).text3)
        case .recoverableFailure:
            CBar(kind: .warn, title: "检查失败，请检查网络后重试",
                 subtitle: "当前版本号不变，仍是 \(presentation.version)；题库本身可离线继续用。")
            HStack(spacing: Tokens.Space.lt1Gap) {
                CButton(text: "重试", action: onRetry)
                CButton(text: Phase4Copy.continueOffline, variant: .ghost, action: onContinueOffline)
            }
        case .mock:
            CButton(text: "重试", action: onRetry)
        }
    }

    private func formatted(_ value: Int) -> String {
        NumberFormatter.localizedString(from: NSNumber(value: value), number: .decimal)
    }
}
