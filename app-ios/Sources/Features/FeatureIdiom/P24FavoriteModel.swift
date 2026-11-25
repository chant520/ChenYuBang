import CangyuData
import Foundation

/// P24 收藏列表只读口子：页面只依赖协议，实现由 app 注入的 IdiomStateStore 承担。
public protocol FavoriteReading {
    func favoriteIdioms() throws -> [FavoriteIdiom]
}

extension IdiomStateStore: FavoriteReading {}

/// P24 收藏列表单行展示模型（与 Android FavoriteIdiomView 同口径）。
/// status 存储串缺值/非法值按未学投影，保证三态标记总有值。
public struct FavoriteIdiomView: Equatable, Sendable, Identifiable {
    public let idiomId: String
    public let word: String
    public let pinyin: String
    public let frequency: Int
    public let status: IdiomLearningStatus

    public var id: String { idiomId }

    public init(idiomId: String, word: String, pinyin: String, frequency: Int, status: IdiomLearningStatus) {
        self.idiomId = idiomId
        self.word = word
        self.pinyin = pinyin
        self.frequency = frequency
        self.status = status
    }

    public static func of(_ row: FavoriteIdiom) -> FavoriteIdiomView {
        FavoriteIdiomView(
            idiomId: row.idiomId,
            word: row.word,
            pinyin: row.pinyin,
            frequency: row.frequency,
            status: IdiomLearningStatus(rawValue: row.status) ?? .notStarted
        )
    }
}

/// P24 页面状态（与 Android P24FavoriteUiState 同口径）。
public struct P24FavoriteState: Equatable, Sendable {
    public var items: [FavoriteIdiomView] = []
    public var loading = true
    public var failed = false

    public var isEmpty: Bool { !loading && !failed && items.isEmpty }

    public init(items: [FavoriteIdiomView] = [], loading: Bool = true, failed: Bool = false) {
        self.items = items
        self.loading = loading
        self.failed = failed
    }
}

/// P24 ViewModel：只读 favoriteIdioms；取消收藏在 P05 顶部完成，返回按 revision 重新读取。
@MainActor
public final class P24FavoriteModel: ObservableObject {
    @Published public private(set) var state = P24FavoriteState()

    private let reader: FavoriteReading

    public init(reader: FavoriteReading) {
        self.reader = reader
    }

    public func load() {
        state = P24FavoriteState(loading: true)
        do {
            state = P24FavoriteState(items: try reader.favoriteIdioms().map(FavoriteIdiomView.of), loading: false)
        } catch {
            state = P24FavoriteState(loading: false, failed: true)
        }
    }
}
