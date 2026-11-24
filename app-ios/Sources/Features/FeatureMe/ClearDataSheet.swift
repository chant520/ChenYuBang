import CangyuData
import CangyuDesignSystem
import CangyuTokens
import SwiftUI

public struct ClearDataSheet: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var counts: ClearDataCounts?

    private let service: UserDataCleaner
    private let initialCounts: ClearDataCounts?
    private let inProgress: Bool
    private let error: String?
    private let onStart: () -> Bool
    private let onFinish: (Result<String, Phase4MessageError>) -> Void

    public init(
        service: UserDataCleaner,
        inProgress: Bool,
        error: String?,
        counts: ClearDataCounts? = nil,
        onStart: @escaping () -> Bool,
        onFinish: @escaping (Result<String, Phase4MessageError>) -> Void
    ) {
        self.service = service
        self.inProgress = inProgress
        self.error = error
        self.initialCounts = counts
        _counts = State(initialValue: counts)
        self.onStart = onStart
        self.onFinish = onFinish
    }

    public var body: some View {
        let colors = CangyuTheme.colors(for: colorScheme)
        VStack(alignment: .leading, spacing: Tokens.Space.block) {
            CCard(soft: true) {
                CLabel("会清空")
                Text(impactText)
                    .font(.system(size: Tokens.Font.n13))
                    .cLineHeight(1.7, size: Tokens.Font.n13)
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lsubTop)
            }

            CCard(soft: true) {
                CLabel("不受影响")
                Text("成语库与例题库照常可用，考试范围设置保留")
                    .font(.system(size: Tokens.Font.n13))
                    .cLineHeight(1.7, size: Tokens.Font.n13)
                    .foregroundStyle(colors.text2)
                    .padding(.top, Tokens.Space.lsubTop)
            }

            if let error {
                CBar(kind: .error, title: error)
            }
            CButton(text: inProgress ? Phase4Copy.clearBusy : "确认清空",
                    variant: .dangerFilled, loading: inProgress) { clear() }
        }
        .onAppear { loadCounts() }
    }

    private var impactText: String {
        guard let counts else { return "正在读取学习数据条数" }
        return "错题 \(counts.mistakes) 道 · 录入题 \(counts.userExamples) 条 · "
            + "收藏 \(counts.favorites) 个 · 学习状态"
    }

    private func loadCounts() {
        guard counts == nil, initialCounts == nil else { return }
        let service = self.service
        let task = Task.detached { () -> ClearDataCounts? in try? service.counts() }
        Task { @MainActor in
            counts = await task.value
        }
    }

    private func clear() {
        guard onStart() else { return }
        counts = try? service.counts()
        let service = self.service
        let task = Task.detached { () -> Result<String, Phase4MessageError> in
            do {
                _ = try service.clear()
                return .success(Phase4Copy.clearSuccess)
            } catch {
                return .failure(Phase4MessageError(message: Phase4Copy.clearFailure))
            }
        }
        Task { @MainActor in onFinish(await task.value) }
    }
}
