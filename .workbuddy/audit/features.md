# Android 业务模块只读代码审计报告（features）

- 审计范围：`app-android/feature-idiom`(28 kt)、`feature-mistake`(25)、`feature-me`(25)、`feature-review`(18)、`feature-example`(11) 及其测试。
- 基准：产品功能规划 v1.7、信息架构 v1.5、设计规范 v1.6、开发规范 v1.10；硬约定：零联网、无账号、学习数据不上传。
- 方法：逐文件静态阅读 + 关键链路跨模块核对（core-data / app 接线仅作证据引用，非审计对象）。
- 分级：高＝违反已拍板口径或核心机制断裂；中＝可预期性能/稳定性/异常缺口；低＝测试缺口与局部重组/风格问题。
- 本报告只列有文件:行号证据、可操作的问题；未发现联网、账号、学习数据上传、B6/B7/B4/B5 越界实现等硬约定违规。

## 高

### H1 A5「我的录入题 N」入口与 P09 未接通，核心机制②断裂
- 定位：`feature-idiom/.../P05IdiomDetailScreen.kt:305`、`:349-373`、`:420-448`；`app/.../RouteContent.kt:83`；对照 `feature-mistake/.../P09IdiomMistakesScreen.kt:49-80`。
- 问题：成语详情页 A5 区仍是固定占位——"我在这个成语下录入了 0 道题"写死为 0、灰显不可点，`RelatedExamplesCard` 的"我录入的题"固定显示"还没在这个成语下录入题"，注释标"待阶段2 user_example 接入"（阶段2早已交付）。P05 的 `onOpenMistakes` 在 app 层被接到 `router.goToMistakes()`（P08 错题本），而非 `Route.IdiomMistakes`（P09）。P09 已完整实现按 `user_example_idiom` 展示该成语下全部录入题、路由也已注册，但用户从成语详情无任何入口可达。
- 影响：直接违反产品 v1.7 §3.1 A5、§4.1 核心必备、验收第 11 条「成语详情页显示 N、点进去看全部录入题」，用户无法从成语回看自己录入的题（含未标错），核心机制②失效。
- 建议：P05 ViewModel 增 `countUserExamples(idiomId)` 注入，补 A5 计数与"`N>0` 可点进 P09"状态；app 层将 P05 该入口改接 `router.push(Route.IdiomMistakes, idiomId)`（与 `RouteActions.kt:28` 同源）；删除占位注释，补 A5 入口联动测试。若属有意延后则由主控更新 v1.7 收口状态并拍板。

## 中

### M1 各类列表全量组合，无 Lazy 列表
- 定位：`feature-mistake/.../P08MistakeListScreen.kt:73-125` 与 `P08MistakeListRows.kt:120-146`、`P09IdiomMistakesScreen.kt:135-140`；`feature-idiom/.../P03LibraryScreen.kt:300-315`、`P06ExamplesScreen.kt:131-146`（feature-example）。
- 问题：所有长列表均为 `Column + verticalScroll + forEach/forEachIndexed` 一次性组合全部条目，无 `LazyColumn` 与 `key`。错题、录入题随用户拍照持续增长，无上界。
- 影响：条目增多后首次组合耗时、重组范围与内存占用线性上升，滑动易卡顿，违反验收第 1 条"滑动不卡顿"。
- 建议：P08/P09 用户数据列表优先改为 `LazyColumn` 并以 `mistake.id`/`userExample.id` 为 key；题库类列表（P03/P06）评assess数据量后可保留，但应有明确上限说明。

### M2 P08 分组错因聚合为 O(分组数×错题数) 嵌套遍历
- 定位：`feature-mistake/.../P08MistakeListViewModel.kt:82-94`。
- 问题：`groups.map` 内对每个分组再次 `mistakes.filter { ... }.flatMap`，且该 `combine` 在三路 Flow 任一发射时整体重算。分组数 G、错题数 M 时为 G×M 扫描。
- 影响：错题量上升后每次数据变动都触发平方级扫描，叠加 M1 的组合开销造成列表刷新卡顿。
- 建议：一次遍历 mistakes 按 idiomId 建 `Map<idiomId, List<tag>>`，再 O(1) 查表生成 `reasonLine`；或把聚合下沉到 core-data 查询。

### M3 A6/A7 状态写入无异常兜底，DAO 失败会冒泡崩溃协程
- 定位：`feature-idiom/.../P05IdiomDetailViewModel.kt:71-89`。
- 问题：`updateStatus`/`updateFavorite` 在 `viewModelScope.launch` 内直接 `writeStatus`/`writeFavorite`，无 try-catch、无 CancellationException 处理、无失败态；其余同类 ViewModel 均有 try-catch。写库抛异常时协程异常向上冒泡且 UI 状态不刷新。
- 影响：阶段6已交付的自评/收藏在写失败时无提示、界面显示旧值，与 P24/P13 计数可能不一致；异常未捕获存在稳定性风险。
- 建议：参照 P10 `changeReviewState` 补 try-catch（单独传播 CancellationException）+ 失败提示态，写失败时回滚或给出可重试反馈；补单测。

### M4 页面硬编码尺寸，违反令牌硬约定
- 定位：`feature-mistake/.../P10UserExampleScreen.kt:241`、`:258`、`:265`、`:270`、`:274`。
- 问题：返回图标 `.size(21.dp)`（已有 `Tokens.Icon.n21=21` 却写字面量），编辑/删除/保存/取消按钮 `Modifier.width(64.dp)` 为规范外自造数值。
- 影响：违反"禁止页面硬编码尺寸/自造规范外数值"硬约定；令牌调整时此处不联动，深色/多dpi一致性差。
- 建议：21.dp 改 `Tokens.Icon.n21.dp`；64.dp 按钮宽度在 tokens.json 新增具名令牌并重新生成后引用，勿在页面写字面量。

### M5 单成语复习计数状态与 DB 副作用滞留在 Composable 层
- 定位：`feature-idiom/.../P05IdiomDetailScreen.kt:64-71`、`:96-105`（app 层同类：`ReviewDestinations.kt:93-126`）。
- 问题：`pendingCount` 用 `remember { mutableStateOf }` + `rememberCoroutineScope` + `Mutex` 在 Composable 中持有并发原语并直接查库；点击时再查一次数据库。
- 影响：状态与副作用未提升进 ViewModel，配置变更/重组会新建 Mutex 与 scope，并发写与在途请求管理脆弱；业务逻辑与 UI 耦合，难测。
- 建议：将 `pendingCount` 与重查逻辑迁入 `P05IdiomDetailViewModel`，用 `MutableStateFlow` + 单 `Job` 取消旧请求替代 Mutex；Composable 只订阅状态。

## 低

### L1 测试缺口：P08 列表状态聚合与 A5 入口无单测守护
- 定位：`feature-mistake/.../P08MistakeListViewModel.kt:66-102`（对应测试目录仅有 `*SnapshotTest`）；`feature-idiom/.../P05IdiomDetailReviewEntryTest.kt` 仅覆盖复习入口。
- 问题：`buildState` 的分组/错因聚合、"只显标错题"在 feature 层无 ViewModel 单测；P05 未测 A5 计数与跳转，故 H1 长期未被发现。
- 建议：补 P08 `buildState` 纯逻辑单测（分组数、reasonLine、tagNames 去重）与 P05 A5 计数→P09 路由的 Compose/单测。

### L2 测试缺口：P15 导入状态机无 ViewModel 单测
- 定位：`feature-me/.../P15BackupViewModel.kt:155-217`；测试目录仅 `P15BackupSnapshotTest.kt`、`P22ImportModeSnapshotTest.kt`。
- 问题：`pickImport`→`confirmImport` 的 `pendingBackup` 生命周期、校验失败停在 P15、取消/放弃不碰库、成功回调 `onFinished(true)` 等分支无单测覆盖，仅有视觉快照。
- 建议：补 ViewModel 单测，注入假 importer 验证各分支的 banner/pendingBackup 与回调，尤其"校验失败不进 P22""discardImport 不写库"。

### L3 P07 高亮每次重组重建 AnnotatedString
- 定位：`feature-example/.../P07ExampleDetailScreen.kt:313`、`:327-363`。
- 问题：`hits` 已用 `remember(text, wordIndex)` 缓存，但 `annotatedText(...)` 是普通函数，在组合期每次重组都重新 `build AnnotatedString`/`withLink`。
- 影响：题干/选项/解析较短，当前影响小；但点击高亮或状态刷新会触发无谓重建。
- 建议：用 `remember(text, hits, colors, onOpenIdiom)` 缓存 `AnnotatedString`，稳定 lambda 后再复用。

## 待拍板项
- H1 A5 入口：请主控确认是遗漏还是有意延后；若延后需更新 v1.7 阶段6收口状态，避免口径与实现长期背离。
