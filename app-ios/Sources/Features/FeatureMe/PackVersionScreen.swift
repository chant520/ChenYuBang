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
                Text("题库版本与更新")
                    .font(.system(size: Tokens.Font.n20, weight: .semibold))
                    .foregroundStyle(colors.text)
                    .accessibilityAddTraits(.isHeader)
                CButton(text: "返回", variant: .ghost, action: onBack)

                CCard {
                    Text("当前题库版本")
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(colors.text2)
                    Text("v\(pack.version)")
                        .font(.system(size: Tokens.Font.n30, weight: .bold))
                        .foregroundStyle(colors.text)
                        .padding(.top, Tokens.Space.lmetaTop)
                    Text("内置版本，装好即可离线用")
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(colors.text3)
                        .padding(.top, Tokens.Space.lsubTop)
                }

                CCard {
                    Text("收录说明")
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(colors.text2)
                    Text("成语 \(formatted(pack.counts.idioms)) 条，真题 \(formatted(pack.counts.examples)) 条")
                        .font(.system(size: Tokens.Font.n13))
                        .foregroundStyle(colors.text)
                        .padding(.top, Tokens.Space.lmetaTop)
                    Text("覆盖国考 / 省考 / 事业编，按范围筛选后可用的条数会随之变化。")
                        .font(.system(size: Tokens.Font.n12))
                        .cLineHeight(1.6, size: Tokens.Font.n12)
                        .foregroundStyle(colors.text2)
                        .padding(.top, Tokens.Space.lsubTop)
                    Text("条数以当前题库定稿为准。")
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(colors.text3)
                        .padding(.top, Tokens.Space.lsubTop)
                }

                CButton(text: "检查题库更新", enabled: false) {}
                Text("当前为离线题库，暂未接入联网检查。")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text3)

                CCard(soft: true) {
                    HStack {
                        Text("更新状态")
                            .font(.system(size: Tokens.Font.n12))
                            .foregroundStyle(colors.text2)
                        Spacer()
                        CTag(text: "离线题库", style: .neutral)
                    }
                    Text("只下载题库增量包，不改动本机学习记录。")
                        .font(.system(size: Tokens.Font.n12))
                        .foregroundStyle(colors.text3)
                        .padding(.top, Tokens.Space.lsubTop)
                }
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
