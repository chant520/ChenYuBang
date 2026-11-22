# app-ios 工程骨架

口径来源：`docs/开发规范-v1.7.md`（§2.1 双端原生 / iOS SPM 不用 CocoaPods、§4.5 迁移、§13.1 构建）。

## 结构

```
app-ios/
├── Package.swift                 # SPM：CangyuTokens / CangyuDesignSystem / CangyuData / 测试依赖
├── ChenYuBang.xcodeproj/         # 已入库的 Xcode 工程（App target + SPM 包引用）
├── project.yml                   # XcodeGen 描述（仅作备用生成，CI 不依赖）
└── Sources/
    ├── Tokens/Tokens.swift       # 生成物，勿手改（python tools/gen_tokens.py 生成）
    ├── DesignSystem/Theme.swift
    ├── Data/Database.swift       # GRDB DatabaseMigrator v1（10 张表）
    └── App/
        ├── ChenYuBangApp.swift
        └── Info.plist            # 相机/相册权限文案
```

## 打开工程

`ChenYuBang.xcodeproj` 已入库，直接打开即可；依赖走 SPM（`Package.swift`），不用 CocoaPods。

```
open app-ios/ChenYuBang.xcodeproj
```

如需用 XcodeGen 重新生成工程描述，可按需执行 `cd app-ios && xcodegen generate`，但 CI 使用已入库的 xcodeproj，不依赖 XcodeGen。Xcode 16.x / Swift 6（见 `project.yml` 的 `xcodeVersion` / `SWIFT_VERSION`）。

## 校验

```
cd app-ios
swift build            # 只编三个库
xcodebuild -resolvePackageDependencies -project ChenYuBang.xcodeproj -scheme ChenYuBang
xcodebuild -project ChenYuBang.xcodeproj -scheme ChenYuBang \
  -destination 'generic/platform=iOS' build CODE_SIGNING_ALLOWED=NO
xcodebuild -project ChenYuBang.xcodeproj -scheme ChenYuBang \
  -destination 'platform=iOS Simulator,name=iPhone 15' test CODE_SIGNING_ALLOWED=NO
```

## 本轮验证状态（如实标注）

- ✅ 目录、SPM 包描述、XcodeGen 描述、Info.plist 权限文案、GRDB v1 迁移 SQL、已入库的 `ChenYuBang.xcodeproj/project.pbxproj` 的**文件已落地**。
- ❌ **未编译验证 / 需 Xcode 复核**：本机是 Windows，没有 Xcode / Swift 工具链。Swift 源码与 `project.pbxproj` 是静态骨架；pbxproj 仅做过括号平衡 / ID 引用完整性静态自检，**未经 Xcode 打开验证**（能否被 Xcode 正确识别需在 macOS + Xcode 16 上复核）。
- ❌ 快照测试 target 与基线图片尚未入库：CI 的 `xcodebuild test` 已接入 swift-snapshot-testing 依赖，但需后续补测试代码与基线后才会产生实际快照断言。
- ❌ GRDB 需首次 `swift package resolve` 联网拉取（CI 上跑；本机断网）。
- ✅ Vision OCR 适配器已写（`Sources/Features/FeatureCapture/VisionOcrEngine.swift`：`zh-Hans` / `.accurate` / 短边 ≥1400 上采样，零联网）；Windows 只做语法解析，编译与 XCTest 由 macOS CI 验证，真机识别精度复测后置（B1）。
- ❌ 备份包 zip 的读写、图片目录约定尚未实现（§4.3）。

## OCR 口径（勿改）

`VNRecognizeTextRequest` + `recognitionLanguages = ["zh-Hans"]` + `.accurate`，全端侧，不联网。
