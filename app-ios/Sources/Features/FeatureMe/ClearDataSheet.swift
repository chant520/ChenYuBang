import CangyuData
import CangyuDesignSystem
import CangyuTokens
import SwiftUI

public struct ClearDataSheet: View {
    @Environment(\.colorScheme) private var colorScheme

    private let service: UserDataCleaner
    private let inProgress: Bool
    private let error: String?
    private let onStart: () -> Bool
    private let onFinish: (Result<String, String>) -> Void
    private let onCancel: () -> Void

    public init(
        service: UserDataCleaner,
        inProgress: Bool,
        error: String?,
        onStart: @escaping () -> Bool,
        onFinish: @escaping (Result<String, String>) -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.service = service
        self.inProgress = inProgress
        self.error = error
        self.onStart = onStart
        self.onFinish = onFinish
        self.onCancel = onCancel
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            Text("这一步不可撤销。清掉的是你录的东西，不是题库本身。")
                .font(.system(size: Tokens.Font.n13))
                .cLineHeight(1.6, size: Tokens.Font.n13)
                .foregroundStyle(colors.text2)

            CCard(soft: true) {
                Text("会清空")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text2)
                Text("错题、录入题、收藏、学习状态与复习记录")
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(colors.text)
                    .padding(.top, Tokens.Space.lsubTop)
            }
            CCard(soft: true) {
                Text("不受影响")
                    .font(.system(size: Tokens.Font.n12))
                    .foregroundStyle(colors.text2)
                Text("成语库与例题库照常可用，考试范围设置与主题保留")
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(colors.text)
                    .padding(.top, Tokens.Space.lsubTop)
            }

            if let error {
                Text(error)
                    .font(.system(size: Tokens.Font.n13))
                    .foregroundStyle(colors.errfg)
                    .accessibilityLabel(error)
            }
            CButton(text: inProgress ? "正在清空…" : "确认清空", variant: .dangerFilled,
                    loading: inProgress) { clear() }
            CButton(text: "取消", variant: .ghost, enabled: !inProgress, action: onCancel)
        }
    }

    private func clear() {
        guard onStart() else { return }
        let task = Task.detached { () -> Result<String, String> in
            do {
                _ = try service.clear()
                return .success("学习数据已清空，题库、考试范围与主题仍保留。")
            } catch {
                return .failure("清空失败，本机数据未变更，请重试。")
            }
        }
        Task { @MainActor in onFinish(await task.value) }
    }
}
