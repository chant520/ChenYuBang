# app-android 工程骨架

口径来源：`docs/开发规范-v1.7.md`（§2 技术栈、§4 数据层、§5 路由、§13 构建）。

## 结构

```
app-android/
├── settings.gradle.kts         # 10 个模块（见下）
├── build.gradle.kts            # AGP 8.7.2、Kotlin 2.0.21、KSP、serialization；ktlint/detekt 全模块生效
├── config/detekt/detekt.yml    # Compose 惯例覆写（函数命名 / 默认参数 / 简单 when）
├── .editorconfig               # ktlint Composable 大写命名豁免
├── gradle.properties
├── gradle/wrapper/             # Gradle 8.9，jar 与 gradlew/gradlew.bat 已就位
├── app/                        # 壳工程，MainActivity + 权限/FileProvider 声明
├── core-ui/                    # 主题、CButton、CEmptyState、生成的 Tokens.kt
├── core-data/                  # 10 张表、AppDatabase(version=1)、DbMigrations
├── ocr/                        # OcrEngine 契约 + 占位实现
└── feature-*/                  # idiom / example / mistake / review / me / capture（按页面簇切，互不依赖）
```

## 本机环境（2026-11-17 已持久化，与 CI 对齐）

- JDK：Temurin 17.0.21+9，`E:\IDE\Users\jdk-17.0.21+9`（用户级 JAVA_HOME / PATH 已写入）。
- Gradle：8.9，`E:\IDE\Users\gradle\gradle-8.9`（wrapper 同版本）。
- Android SDK：`E:\IDE\Users\Android`（ANDROID_HOME 已有；platforms 22/27/35/36，build-tools 含 35.0.0）。
- 旧终端若仍解析到 JDK 8，先 `export JAVA_HOME="E:/IDE/Users/jdk-17.0.21+9"` 再构建。

## 构建/校验命令

```
cd app-android
./gradlew :app:assembleDebug        # 出 APK
./gradlew test                      # 单测
./gradlew ktlintCheck detekt        # 静态检查（全模块聚合）
```

令牌 / schema / 基线回归（项目根目录）：

```
python tools/gen_tokens.py --check
python tools/validate_schema.py
.venv/Scripts/python tools/baseline.py --check   # baseline 依赖 Pillow，装在项目 .venv
```

## 本轮验证状态（如实标注）

- ✅ 目录、Gradle 配置、Manifest 权限、10 张表、迁移登记表、令牌引用的文件已落地。
- ✅ **编译验证通过**（2026-11-17）：`:app:assembleDebug` + `test` + `ktlintCheck` + `detekt` 全绿；产物 `app/build/outputs/apk/debug/app-debug.apk`（9,921,998 字节）。GitHub CI 亦全绿（含 `:app:lintDebug` / `:core-ui:verifyRoborazziDebug`）。
- ✅ 静态检查口径：生成的 `Tokens.kt` 不参与 ktlint/detekt（数值事实源是 tokens.json，避免生成物漂移）；`@Composable` 大写命名经 .editorconfig 与 detekt.yml 豁免。
- ❌ OCR 引擎未接入（PaddleOCR 模型与 AAR 待 OCR 实测后落地，见规范 §2.3）。
- ❌ Room FTS 索引未接入（§4.2 骨架只做了前缀 LIKE）。
- ✅ iOS 侧编译验证通过（2026-11-17）：GitHub 私有仓库 macos-15-intel CI，SwiftLint / SPM 解析 / 编译 / 单测（3 tests 0 failures）全绿（run 44060637298）。

## 页面接入顺序（规范 §5.1）

P02 推荐首页 → P08 错题列表 → P17–P21 拍照录题链路 → 其余。每接一页先用 `docs/design/baseline/` 的基线图对版。