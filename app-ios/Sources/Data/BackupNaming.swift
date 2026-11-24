import Foundation

extension Backup {
    private static let stampFormatter: DateFormatter = dayFormatter(with: "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private static let nameFormatter: DateFormatter = dayFormatter(with: "yyyyMMdd-HHmmss")

    static func stamp(_ date: Date) -> String {
        stampFormatter.string(from: date)
    }

    static func fileName(_ date: Date, prefix: String = "backup-") -> String {
        "\(prefix)\(nameFormatter.string(from: date)).zip"
    }

    static func normalized(appVersion: String) -> String {
        let parts = appVersion.split(separator: ".").prefix(3)
        guard parts.count == 3, parts.allSatisfy({ !$0.isEmpty && $0.allSatisfy(\.isNumber) }) else {
            return "0.0.0"
        }
        return parts.map(String.init).joined(separator: ".")
    }

    static var bundleAppVersion: String {
        (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "0.0.0"
    }

    static var bundleBuild: Int {
        Int((Bundle.main.infoDictionary?["CFBundleVersion"] as? String) ?? "") ?? 1
    }

    private static func dayFormatter(with format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = format
        return formatter
    }
}
