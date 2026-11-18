// swift-tools-version:5.9
import PackageDescription

// 双端原生：iOS 侧用 SPM，不用 CocoaPods（规范 §2.1 / §13.1）。
// 注意：这里只描述可复用的三个库；App 壳由已入库的 ChenYuBang.xcodeproj 管理。
let package = Package(
    name: "ChenYuBangKit",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "CangyuTokens", targets: ["CangyuTokens"]),
        .library(name: "CangyuDesignSystem", targets: ["CangyuDesignSystem"]),
        .library(name: "CangyuData", targets: ["CangyuData"])
    ],
    dependencies: [
        // GRDB.swift 固定 6.x（规范 §13.1）
        .package(url: "https://github.com/groue/GRDB.swift.git", from: "6.29.0"),
        // swift-snapshot-testing 固定 1.x 并锁定小版本，供 UI 快照比对（规范 §2.1 / §13.1）
        .package(url: "https://github.com/pointfreeco/swift-snapshot-testing.git", exact: "1.17.0"),
        .package(url: "https://github.com/weichsel/ZIPFoundation.git", exact: "0.9.19")
    ],
    targets: [
        .target(name: "CangyuTokens", path: "Sources/Tokens"),
        .target(
            name: "CangyuDesignSystem",
            dependencies: ["CangyuTokens"],
            path: "Sources/DesignSystem"
        ),
        .target(
            name: "CangyuData",
            dependencies: [
                .product(name: "GRDB", package: "GRDB.swift"),
                .product(name: "ZIPFoundation", package: "ZIPFoundation")
            ],
            path: "Sources/Data"
        )
    ]
)
