import Foundation
import CangyuData

/// 复习判分与文案（P11 判分区，开发规范 v1.8 第 8.2 节）。
///
/// 录入错题没有正确答案与解析列，判分口径沿用 P10 重做：与首次「我的答案」
/// 不同＝答对、相同＝答错。判分区逐字沿用 P10 重做串并带选项字母
/// `与首次作答一致（X）`／`与首次作答不同：首次 X，本次 Y`／`本题未记录你的首次作答，无法比对（本次选了 X）`，
/// 另附一句「本题未记录正确答案」（与 Android `ReviewScoring` 同口径）；
/// feature 之间不互相依赖，字符串由本模块持有。
public enum ReviewJudgeOutcome: Equatable, Sendable {
    case same(picked: String)
    case different(first: String, picked: String)
    case unknownFirst(picked: String)
}

/// P11 / P12 取值（纯函数）。
public enum ReviewBrowse {
    public static let letters = ["A", "B", "C", "D"]

    /// 判分区口径说明一行（第 8.2 节写死的规范串，把「没有正确答案」如实写在页面上）。
    public static let judgeNote = "本题未记录正确答案"
    /// 题目一个选项都没录时的说明（如实说明、不伪造判分）。
    public static let noOptionsNote = "本题未记录选项，无法作答比对。"

    /// 判分：`first` / `picked` 均为 A–D 合法值才可比对；首次未记录或非法时给
    /// 「无法比对」结果，调用方据此不落 `review_log`（第 8.2 节）。
    public static func judge(first: String?, picked: String?) -> ReviewJudgeOutcome? {
        guard let picked, letters.contains(picked) else { return nil }
        guard let first, letters.contains(first) else { return .unknownFirst(picked: picked) }
        return first == picked ? .same(picked: picked) : .different(first: first, picked: picked)
    }

    /// 判分区结果串：三条逐字沿用第 8.2 节的 P10 重做串并带选项字母。
    public static func resultText(_ outcome: ReviewJudgeOutcome) -> String {
        switch outcome {
        case .same(let picked):
            return "与首次作答一致（\(picked)）"
        case .different(let first, let picked):
            return "与首次作答不同：首次 \(first)，本次 \(picked)"
        case .unknownFirst(let picked):
            return "本题未记录你的首次作答，无法比对（本次选了 \(picked)）"
        }
    }
}

/// 本轮范围（信息架构 v1.4 决策 E：两种来源共用 P11 / P12，页内「本轮范围」一行区分）。
public struct ReviewScope: Equatable, Sendable {
    /// 单成语模式的成语 id；nil＝全局队列。
    public let idiomID: String?
    /// 单成语模式的成语名（页内展示）。
    public let word: String?

    public init(idiomID: String?, word: String?) {
        self.idiomID = idiomID
        self.word = word
    }

    public static func global() -> ReviewScope { ReviewScope(idiomID: nil, word: nil) }
    public static func idiom(id: String, word: String) -> ReviewScope {
        ReviewScope(idiomID: id, word: word)
    }

    /// 本轮范围一行：「全部待复习」／「只看『某成语』」。
    public var line: String {
        if let word, idiomID != nil { return "只看『\(word)』" }
        return "全部待复习"
    }
}

/// 一轮复习的内存小结：P11 交出、P12 渲染（`review_log` 无轮次概念，数字不按库重算）。
/// 无法比对的题只计题量，不进 `answered` 也不进对错（开发规范第 8.2 节）。
public struct ReviewRoundOutcome: Equatable, Sendable {
    public let total: Int
    public let answered: Int
    public let correct: Int

    public init(total: Int, answered: Int, correct: Int) {
        self.total = total
        self.answered = answered
        self.correct = correct
    }

    /// 答错＝与首次作答相同的题数。
    public var wrong: Int { max(answered - correct, 0) }
}

/// P11 / P12 读库与落库口子：页面只依赖协议，实现由 app 注入。
public protocol ReviewQueueReading {
    func queue(idiomID: String?) throws -> [ReviewQueueItem]
    func pendingCount(idiomID: String) throws -> Int
    func idiomsInRound(mistakeIDs: [String]) throws -> [LinkedIdiom]
    func record(mistakeID: String, result: String, reviewedAt: String) throws
}

extension ReviewLibraryService: ReviewQueueReading {}

/// 复习落库时间戳：ISO8601 UTC 秒级串（与 `UserExampleSaver` 写库口径一致）。
enum ReviewTimestamp {
    static func now() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter.string(from: Date())
    }
}
