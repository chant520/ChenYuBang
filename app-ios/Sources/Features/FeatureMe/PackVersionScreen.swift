import CangyuData
import CangyuDesignSystem
import CangyuTokens
import SwiftUI

public struct PackVersionScreen: View {
    @Environment(\.colorScheme) private var colorScheme

    private let pack: ValidatedPack
    private let onBack: () -> Void

    public init(pack: ValidatedPack, onBack: @escaping () -> Void) {
        self.pack = pack
        self.onBack = onBack
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

                CCard {
                    CLabel("当前题库版本")
                    Text("v\(pack.version)")
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
                    (Text("成语 ") + Text(formatted(pack.counts.idioms)).bold()
                        + Text(" 条 · 真题 ") + Text(formatted(pack.counts.examples)).bold() + Text(" 条"))
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

                Text("只下载题库增量包，不改动本机学习记录。")
                    .font(.system(size: Tokens.Font.n115))
                    .cLineHeight(1.65, size: Tokens.Font.n115)
                    .foregroundStyle(colors.text3)
                Text(Phase4PendingCopy.offlineNotice)
                    .font(.system(size: Tokens.Font.n115))
                    .foregroundStyle(colors.text3)
            }
            .padding(.horizontal, Tokens.Space.pageX)
            .padding(.vertical, Tokens.Space.contentTop)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.page)
    }

    private func formatted(_ value: Int) -> String {
        NumberFormatter.localizedString(from: NSNumber(value: value), number: .decimal)
    }
}
