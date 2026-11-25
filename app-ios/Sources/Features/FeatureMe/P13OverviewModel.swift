import CangyuData
import Foundation

/// P13 E2 五项统计只读口子：三计数取 IdiomStateStore，错题/待复习取 MistakeLibrary.overview。
public protocol OverviewReading {
    func countLearned() throws -> Int
    func countMastered() throws -> Int
    func countFavorite() throws -> Int
    func mistakeOverview() throws -> MistakeOverview
}

/// app 层装配：IdiomStateStore 承担三计数，MistakeLibraryReader 承担错题总览。
public struct ComposedOverviewReader: OverviewReading {
    private let stateStore: IdiomStateStore
    private let mistakeReader: MistakeLibraryReader

    public init(stateStore: IdiomStateStore, mistakeReader: MistakeLibraryReader) {
        self.stateStore = stateStore
        self.mistakeReader = mistakeReader
    }

    public func countLearned() throws -> Int { try stateStore.countLearned() }
    public func countMastered() throws -> Int { try stateStore.countMastered() }
    public func countFavorite() throws -> Int { try stateStore.countFavorite() }
    public func mistakeOverview() throws -> MistakeOverview { try mistakeReader.overview() }
}

/// E2 五项统计；全空自然为 0，不做成长体系（与 Android P13OverviewStats 同口径）。
public struct P13OverviewStats: Equatable, Sendable {
    /// 已学成语：idiom_state status IN (学过,已掌握)。
    public var learned = 0
    /// 收藏：idiom_state favorite=1，与 P24 列表同源。
    public var favorites = 0
    /// 错题总数：mistake 主表按 mistake.id 计。
    public var mistakes = 0
    /// 待复习：mistake.state='待复习'。
    public var pending = 0
    /// 已掌握：idiom_state status='已掌握'，非错题 C9 掌握态。
    public var mastered = 0

    public init(learned: Int = 0, favorites: Int = 0, mistakes: Int = 0, pending: Int = 0, mastered: Int = 0) {
        self.learned = learned
        self.favorites = favorites
        self.mistakes = mistakes
        self.pending = pending
        self.mastered = mastered
    }
}

/// P13 E2 统计 Model：只做只读聚合，不写任何数据；取数失败保留全 0。
@MainActor
public final class P13OverviewModel: ObservableObject {
    @Published public private(set) var stats = P13OverviewStats()

    private let reader: OverviewReading

    public init(reader: OverviewReading) {
        self.reader = reader
    }

    public func refresh() {
        do {
            let overview = try reader.mistakeOverview()
            stats = P13OverviewStats(
                learned: try reader.countLearned(),
                favorites: try reader.countFavorite(),
                mistakes: overview.total,
                pending: overview.pending,
                mastered: try reader.countMastered()
            )
        } catch {
            stats = P13OverviewStats()
        }
    }
}
