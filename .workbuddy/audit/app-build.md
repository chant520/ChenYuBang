# app 模块与构建配置只读审计

审计范围：`app-android/app` 全部 21 个 Kotlin 文件、`app-android/*.gradle.kts`、`config/detekt/detekt.yml`、`settings.gradle.kts`、`gradle/libs.versions.toml`（不存在，见中-3）。未修改任何源码。体积数据来源：`app/build/outputs/apk/debug/app-debug.apk` = 42,440,611 字节。

## 高

### 高-1  release / debug 均未启用代码压缩，dex 是 42.4MB 的主要来源
- 定位：`app-android/app/build.gradle.kts:20-25`
- 问题：`release.isMinifyEnabled = false` 且未配置 `shrinkResources`；debug 同样使用默认不压缩配置。APK 内 `classes.dex` 16.5MB + `classes14.dex` 11.1MB + 其余 12 个 dex 约 3MB，dex 合计约 29.6MB；OCR 模型与 so 约 21.4MB（`ppocrv3_rec.nb` 10.8MB、`libc++_shared.so` 6.8MB、`libpaddle_lite_jni.so` 3.1MB、`ppocrv3_det.nb` 1.1MB）。
- 影响：debug 42.4MB 超 30MB 目标；release 体积同样失控，且无 shrink 的 release 仍携带大量未引用代码。
- 建议：release 开启 `isMinifyEnabled = true` + `shrinkResources = true` 并保留 `proguard-rules.pro`；若目标值针对 debug 包，则给 debug 单独开启 `isMinifyEnabled`（代价是构建变慢），否则 30MB 目标无法通过配置达成——OCR 资源 21.4MB 属离线硬约定不可删，需先明确目标是 release 还是 debug（待拍板）。

## 中

### 中-1  复习 ViewModel 按 instance 建 key，导航离开后不释放
- 定位：`app-android/app/src/main/kotlin/com/chenyubang/app/ReviewDestinations.kt:51`
- 问题：`viewModel(key = "review-queue-$instance", factory = factory)` 中 `instance` 取自 `entry.instance`，每次 `startReview` / `retryReview` 都通过 `append` 递增 `sequence` 生成新 key。Compose 的 `viewModel()` 在 Composable 离开组合时不会从 Activity 的 `ViewModelStore` 移除实例。
- 影响：每复习一轮就永久保留一个 `ReviewViewModel`（含队列状态），反复复习持续累积；进程存活期间内存只增不减。
- 建议：在 `ReviewQueueDestination` 中用 `DisposableEffect(instance)` 在 `onDispose` 里通过 `ViewModelProvider(LocalViewModelStoreOwner.current!!)` 按 key 移除；或改为 RouterShell 持有单一 review ViewModel、新轮次调用其 `load()` 重置，不再按 instance 建 key。

### 中-2  detekt 函数命名规则被全局放宽，等于取消检查
- 定位：`app-android/config/detekt/detekt.yml:6`
- 问题：`FunctionNaming.functionPattern: '[a-zA-Z][a-zA-Z0-9]*'` 同时允许小写与大写开头。注释说明目的是兼容 Composable 大写构件，但配置对所有模块、所有普通函数生效。
- 影响：普通 Kotlin 函数写成 `GetUser()` 也不会被 detekt 报出，静态检查门禁失效。
- 建议：恢复默认小写开头的 `functionPattern`，对 Compose 构件用 `@Suppress("FunctionNaming")` 或 detekt 的 `excludes`/`ignoreAnnotated` 按注解过滤，不全局改规则。

### 中-3  version catalog 缺失，依赖版本硬编码且跨模块重复
- 定位：`app-android/gradle/libs.versions.toml` 不存在；`app-android/app/build.gradle.kts:64-78`、`app-android/core-data/build.gradle.kts:51-62`、`app-android/feature-capture/build.gradle.kts:48-68` 等
- 问题：Compose BOM `2024.10.01`、lifecycle `2.8.7`、Room `2.6.1`、Robolectric `4.13`、Roborazzi `1.13.0` 等版本在 10 余个 `build.gradle.kts` 中重复字面量声明，根 `build.gradle.kts` 的 `plugins` 块又单独声明一遍插件版本。
- 影响：升级时漏改任一模块即产生版本错配（如 lifecycle-compose 与 lifecycle-ktx 漂移），且无单一来源可校验。
- 建议：新增 `gradle/libs.versions.toml` 集中声明版本与库坐标，各模块改用 `libs.*` 引用；根 `plugins` 块保留版本号但由 catalog 的 `versions` 引用。

### 中-4  启动恢复路径无单测
- 定位：`app-android/app/src/main/kotlin/com/chenyubang/app/LaunchBootstrap.kt:16-37`
- 问题：`loadInitialLaunch()` 承担 ZIP 恢复、首次范围写入、`refreshScopeLinks()` 异常吞掉等关键逻辑，`src/test` 下无对应测试文件。
- 影响：首次启动省份默认全选（项目硬口径）、中断提交修复、异常后能否进入主界面均无回归保护；改动 core-data 后无法验证启动链路。
- 建议：补 `LaunchBootstrapTest`，注入 fake coordinator/store/importer，覆盖：首次启动写入全选省份、`loaded.complete=true` 时不重复写、`refreshScopeLinks` 抛异常仍返回 preferences、`coordinator.bootstrap` 抛异常向上传播。

### 中-5  core-ui 重复 apply 静态检查插件
- 定位：`app-android/core-ui/build.gradle.kts:5-6` 对比 `app-android/build.gradle.kts:21-23`
- 问题：根 `subprojects` 已对所有子模块统一 `apply(plugin = "org.jlleitschuh.gradle.ktlint")` 与 `detekt`，`core-ui` 又显式声明一次；其余 feature 模块按注释「由根统一 apply，本模块不另起一套」处理。
- 影响：破坏「统一生效」的单一来源；`core-ui` 的 detekt 配置可能与根 `config.setFrom` 时序不同，排查规则漂移时多一处干扰。
- 建议：删除 `core-ui/build.gradle.kts:5-6` 两行，仅保留 `roborazzi` 插件声明。

### 中-6  `append()` 无 presentation 约束，可构造非法路由状态
- 定位：`app-android/app/src/main/kotlin/com/chenyubang/app/RouterState.kt:124-129`
- 问题：`push()` 有 `require(route.presentation == Push || route == Search)` 校验，但 `append()` / `replaceTop()` 作为 `internal` 方法无 presentation 校验；`startReview` / `startSelfTest` / `startCapture` 均绕过 `push()` 直接 `append`。
- 影响：任意 internal 调用都能把 `Onboarding` / `Done` / `Sheet` 压入栈，而 `showTabs` 只看是否存在 FullScreen，非法状态可导致顶栏与页面错位且无断言。
- 建议：为全屏路由增加 `pushFullScreen(route)` 白名单入口并在此统一 `require(presentation == FullScreen && route in allowedSet)`，`append` 改为 `private`，仅由 `push` 与新入口调用。

## 低

### 低-1  非目标 ABI 的 so 被打进 APK
- 定位：`app/build/outputs/apk/debug/app-debug.apk` 内含 `lib/x86_64/libimage_processing_util_jni.so`、`lib/x86/libimage_processing_util_jni.so`、`lib/armeabi-v7a/libimage_processing_util_jni.so`（合计约 118KB）
- 问题：OCR 原生库只提供 arm64-v8a，但传递依赖带入的 `libimage_processing_util_jni.so` 含 x86/x86_64/armeabi-v7a 四套 ABI。
- 影响：安装包混入不可用 ABI，浪费体积并让 ABI 构成与分发口径不一致。
- 建议：在 `app/build.gradle.kts` 的 `defaultConfig` 加 `ndk { abiFilters += "arm64-v8a" }`，或在 `packaging` 中 exclude 其余 ABI 的该 so。

### 低-2  构建脚本版本注释指向过期规范
- 定位：`app-android/build.gradle.kts:1`、`app-android/app/build.gradle.kts:55`
- 问题：注释引用 `docs/开发规范-v1.6.md`，而 AGENTS.md 明确现行规范为 v1.10；`ocr/build.gradle.kts:27` 已正确引用 v1.10。
- 影响：按注释定位规范章节时取到旧口径，增加核对成本。
- 建议：将两处注释改为 `开发规范-v1.10.md` 并核对章节号是否仍对应。

### 低-3  路由分发的 else 兜底静默降级为占位页
- 定位：`app-android/app/src/main/kotlin/com/chenyubang/app/RouteContent.kt:65`、`AppRouteHost.kt:149`
- 问题：`RouteContent` 的 `else -> CaptureRouteContent(...)`，后者 `else -> RoutePlaceholder(...)`。新增 `Route` 若未在 `when` 中登记，既不报错也不落回占位页提示，只显示「页面待接入」。
- 影响：路由表新增条目与渲染分发不同步时，问题在运行时才以占位页形式暴露，难以定位遗漏分支。
- 建议：在 `RouteContent` 显式列出所有 capture 路由，`else -> error("Unhandled route: ${entry.route}")`；或补一条单测断言 `Route.entries` 全部被分发覆盖。

### 低-4  Queue 退出二次确认缺少 UI 层测试
- 定位：`app-android/app/src/main/kotlin/com/chenyubang/app/AppRouteHost.kt:148-156`
- 问题：`back` lambda 根据 `previous != Route.Summary` 决定是否弹 `ExitReviewOverlay`，现有 `RouterStateTest` 只覆盖纯状态 `back()` 与 `systemBackEnabled`，未覆盖 `confirmExit` 这一层。
- 影响：首轮退出二次确认、重试返回小结不丢进度这两条交互无回归保护。
- 建议：把 `confirmExit` 决策抽成纯函数 `shouldConfirmExit(router): Boolean` 并补单测，覆盖 Queue/首轮、Queue/来自 Summary、Done、SelfTest 四种输入。
