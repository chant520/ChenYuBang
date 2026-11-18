import Foundation
import GRDB

/// idiom 表记录，字段对齐规范 §4.1。pitfalls / synonyms / antonyms 在库中是 JSON 文本。
/// 搜索结果与重建校验都用它承载（FetchableRecord 按列名解码）。
public struct IdiomRecord: FetchableRecord, Decodable {
    public let id: String
    public let word: String
    public let pinyin: String
    public let initials: String
    public let sense: String
    public let origin: String?
    public let color: String
    public let pitfalls: String
    public let synonyms: String
    public let antonyms: String
    public let frequency: Int
}
