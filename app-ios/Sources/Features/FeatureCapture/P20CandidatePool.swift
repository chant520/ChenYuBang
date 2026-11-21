import Foundation

/// P20 候选档位。三档互斥、一词一档，优先级：最匹配 > 同题出现 > 语义相近。
/// - manual：空态 P04 回填专用新文案标签，沿用 CCheckRow 的 trailing 样式，不新增 token。
/// - added：「加一个」从全库补入的词，不属于命中档位，不显示标签。
public enum MatchTier: Equatable, Sendable {
    case best
    case sameQuestion
    case semantic
    case manual
    case added

    public var label: String? {
        switch self {
        case .best: return "最匹配"
        case .sameQuestion: return "同题出现"
        case .semantic: return "语义相近"
        case .manual: return "手动选择"
        case .added: return nil
        }
    }
}

/// 一行候选。frequency 只用于「最匹配」并列判定，不写库。
public struct MatchCandidate: Equatable, Sendable {
    public let id: String
    public let word: String
    public let frequency: Int
    public let tier: MatchTier

    public init(id: String, word: String, frequency: Int, tier: MatchTier) {
        self.id = id
        self.word = word
        self.frequency = frequency
        self.tier = tier
    }
}

/// 候选生成的最小输入，由成语库记录投影而来（synonyms 保持库中的 JSON 字符串原样）。
/// 不依赖数据层类型，纯函数即可单测。
public struct MatchIdiom: Equatable, Sendable {
    public let id: String
    public let word: String
    public let synonyms: String
    public let frequency: Int

    public init(id: String, word: String, synonyms: String = "[]", frequency: Int) {
        self.id = id
        self.word = word
        self.synonyms = synonyms
        self.frequency = frequency
    }
}

public struct P20PoolResult: Equatable {
    public let candidates: [MatchCandidate]
    public let bestId: String?

    public init(candidates: [MatchCandidate], bestId: String?) {
        self.candidates = candidates
        self.bestId = bestId
    }
}

/// P20 候选生成的纯函数集合：全库词表扫描命中 + 三档判定 + JSON 降级。
///
/// 命中口径与 Android P20Matcher 完全一致：word 原样子串全词命中，synonyms 近似扩展补候选；
/// 不做 OCR 错字模糊容错（错字在 P19 补字兜底）。候选范围＝全库，不按考试范围过滤。
/// 中文题干下该口径与 idiom_fts 的 word 全词命中等价，且 synonyms 列不在 FTS 索引需应用层解析。
public enum P20Matcher {
    /// 展示窗口批长：高保真稿 C P20 一屏三条。
    public static let windowSize = 3
    public static let maxCandidates = 50

    public static let emptyTitle = "没匹配到成语，可手动选一个"
    public static let manualAction = "手动挑成语"
    public static let noMoreCandidates = "没有更多候选"
    public static let candidateLimit = "候选最多 50 条"
    public static let noText = "（无识别文字）"
    public static let readFailed = "题库读取失败，请重试"
    public static let atLeastOne = "至少保留一条归属"
    public static let tagRule = "标签需为 1–8 个字"
    public static let emptyStem = "题干为空，请返回识别结果补字后再保存"
    public static let noImage = "原图缺失，请返回重拍"
    public static let saveFailed = "保存失败，请重试"

    /// synonyms 是 JSON 字符串列，解析失败 / 缺失 / 非法一律降级空数组，不抛到 UI。
    public static func synonyms(of raw: String) -> [String] {
        guard let data = raw.data(using: .utf8),
              let array = try? JSONDecoder().decode([String].self, from: data)
        else { return [] }
        return array
    }

    /// 命中 → 去重 → 按 word 升序取前 50；bestId 在全部 word 命中词里 frequency 高优先、同频 word 升序取一条。
    /// 最匹配落在前 50 之外时用其替换末位并重新按 word 排序，保证展示窗口一定能勾到它。
    /// 一个词同时命中 word 与 synonyms 时只按最高档归位。
    public static func buildPool(text: String, idioms: [MatchIdiom]) -> P20PoolResult {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !idioms.isEmpty else {
            return P20PoolResult(candidates: [], bestId: nil)
        }
        var hits: [String: (idiom: MatchIdiom, wordHit: Bool)] = [:]
        var order: [String] = []
        for idiom in idioms {
            let wordHit = !idiom.word.isEmpty && text.contains(idiom.word)
            let synonymHit = !wordHit && synonyms(of: idiom.synonyms).contains {
                !$0.isEmpty && text.contains($0)
            }
            if (wordHit || synonymHit) && hits[idiom.id] == nil {
                hits[idiom.id] = (idiom, wordHit)
                order.append(idiom.id)
            }
        }
        let ordered: [(idiom: MatchIdiom, wordHit: Bool)] = order
            .compactMap { hits[$0] }
            .sorted { $0.idiom.word < $1.idiom.word }
        let best = ordered
            .filter { $0.wordHit }
            .sorted { lhs, rhs in
                if lhs.idiom.frequency != rhs.idiom.frequency {
                    return lhs.idiom.frequency > rhs.idiom.frequency
                }
                return lhs.idiom.word < rhs.idiom.word
            }
            .first
        let bestId = best?.idiom.id
        var pool = Array(ordered.prefix(maxCandidates))
        if let best, !pool.contains(where: { $0.idiom.id == best.idiom.id }) {
            pool[pool.count - 1] = best
            pool.sort { $0.idiom.word < $1.idiom.word }
        }
        let candidates = pool.map { entry -> MatchCandidate in
            let tier: MatchTier
            if entry.idiom.id == bestId {
                tier = .best
            } else if entry.wordHit {
                tier = .sameQuestion
            } else {
                tier = .semantic
            }
            return MatchCandidate(
                id: entry.idiom.id,
                word: entry.idiom.word,
                frequency: entry.idiom.frequency,
                tier: tier
            )
        }
        return P20PoolResult(candidates: candidates, bestId: bestId)
    }

    /// 「加一个」从全库未展示词中取一条附加候选；无档位标签。
    public static func addedCandidate(_ idiom: MatchIdiom) -> MatchCandidate {
        MatchCandidate(id: idiom.id, word: idiom.word, frequency: idiom.frequency, tier: .added)
    }

    /// 空态手动回填：覆盖候选区、默认勾选、标「手动选择」。
    public static func manualCandidate(_ idiom: MatchIdiom) -> MatchCandidate {
        MatchCandidate(id: idiom.id, word: idiom.word, frequency: idiom.frequency, tier: .manual)
    }
}
