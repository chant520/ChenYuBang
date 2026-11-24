import Foundation

public struct ChannelManifest: Equatable, Sendable {
    public let schemaVersion: Int
    public let packVersion: String
    public let builtAt: String
    public let counts: PackCounts
    public let sha256: String
    public let sizeBytes: Int
    public let packFile: String

    public func packURL(relativeTo latestURL: URL) throws -> URL {
        guard packFile == "pack-\(packVersion).zip" else {
            throw PackChannelError.manifestMismatch
        }
        return latestURL.deletingLastPathComponent().appendingPathComponent(packFile)
    }

    static func read(_ data: Data) throws -> ChannelManifest {
        guard data.count <= 64 * 1024 else { throw PackChannelError.invalidResponse }
        var reader = try PackJSONReader(data)
        let json = try reader.read()
        try PackSchema.channel.check(json, path: "channel")
        let object = try PackObject(json)
        guard try object.integer("schemaVersion") == 1 else {
            throw PackChannelError.unsupportedSchema
        }
        guard let countsValue = object.fields["counts"] else { throw PackError.schema("Missing counts") }
        let countsObject = try PackObject(countsValue)
        let counts = try PackCounts(
            idioms: countsObject.integer("idioms"), examples: countsObject.integer("examples")
        )
        let sizeBytes = try object.integer("sizeBytes")
        guard sizeBytes <= PackArchive.maximumInput else { throw PackChannelError.sizeMismatch }
        return try ChannelManifest(
            schemaVersion: object.integer("schemaVersion"),
            packVersion: object.string("packVersion"),
            builtAt: object.string("builtAt"),
            counts: counts,
            sha256: object.string("sha256"),
            sizeBytes: sizeBytes,
            packFile: object.string("packFile")
        )
    }
}

public enum PackChannelError: Error, Equatable, Sendable {
    case notConfigured
    case invalidResponse
    case unsupportedSchema
    case network
    case sizeMismatch
    case manifestMismatch
    case storage
    case database

    public var message: String {
        switch self {
        case .notConfigured: return "未配置题库更新地址"
        case .invalidResponse: return "题库更新响应无效"
        case .unsupportedSchema: return "题库版本不受支持，请升级 App"
        case .network: return "检查失败，请检查网络后重试"
        case .sizeMismatch: return "题库包大小不符，请重试"
        case .manifestMismatch: return "题库包校验失败（摘要或字段不符）"
        case .storage: return "题库版本文件保存失败，请重试"
        case .database: return "题库导入失败，请重试"
        }
    }
}

public struct PackChannelConfiguration: Equatable, Sendable {
    public let latestURL: URL?

    public init(latestURL: URL?) { self.latestURL = latestURL }

    public static let production = PackChannelConfiguration(latestURL: nil)
}

public enum PackVersionComparison {
    public static func compare(_ left: String, _ right: String) -> Int {
        let leftParts = parts(left)
        let rightParts = parts(right)
        for index in 0..<3 {
            let result = comparePart(
                leftParts.indices.contains(index) ? leftParts[index] : "0",
                rightParts.indices.contains(index) ? rightParts[index] : "0"
            )
            if result != 0 { return result }
        }
        return 0
    }

    private static func parts(_ version: String) -> [Substring] {
        version.split(separator: ".").map { $0.hasPrefix("v") ? $0.dropFirst() : $0 }
    }

    private static func comparePart(_ left: Substring, _ right: Substring) -> Int {
        let leftDigits = left.drop(while: { $0 == "0" })
        let rightDigits = right.drop(while: { $0 == "0" })
        if leftDigits.count != rightDigits.count {
            return leftDigits.count < rightDigits.count ? -1 : 1
        }
        if leftDigits == rightDigits { return 0 }
        return leftDigits.lexicographicallyPrecedes(rightDigits) ? -1 : 1
    }
}
