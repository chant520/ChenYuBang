# core-data / core-ui 只读代码审计报告

- 审计范围：`app-android/core-data`（73 kt）、`app-android/core-ui`（52 kt）
- 基线：`docs/开发规范-v1.10.md` §4.1/§4.5/§8.3/§8.4、`shared/schema/migrations.json`、`shared/tokens/tokens.json`
- 硬约定复核：零联网/无账号/学习数据不上传——`HttpPackUpdateSource` 仅用于题库清单与包下载（唯一联网口），备份另存走系统分享面板；`PackImporter`/`BackupWriter`/`ClearData` 均无网络调用，未见学习数据上传路径。`tokens.json` 经 `python tools/gen_tokens.py --check` 输出 `tokens ok: generated files match tokens.json`，`Tokens.kt` 未被手改。
- 分级原则：只列有代码证据、可操作的问题；同一问题不重复。各级条数未用满，宁缺毋滥。

---

## 高

### H1. v3 防御性回填缺少外键校验，可在用户表埋下悬空关联，使合并导入永久失败

- **定位**：`app-android/core-data/src/main/kotlin/com/chenyubang/core/data/DbMigrations.kt:55-60`；关联影响点 `BackupWriter.kt:73`、`BackupWriter.kt:150-165`、`BackupRows.kt:116-127`
- **问题**：回填 SQL 为
  `INSERT OR IGNORE INTO user_example_idiom SELECT m.userExampleId, mi.idiomId FROM mistake m JOIN mistake_idiom mi ON mi.mistakeId=m.id WHERE m.userExampleId IS NOT NULL`。
  只判 `userExampleId IS NOT NULL`，未校验该 id 在 `user_example` 中存在，也未校验 `mi.idiomId` 在 `idiom` 中存在。规范 §4.5 v3 方案要求回读校验"外键可解析"，本节实现未做到；对照 `BackupOrphans.resolveMistakes`（`BackupOrphans.kt:56`）明确把"userExampleId 指向不存在的录入题"当孤儿处理，迁移与导入的防御口径不一致。
- **影响**：若历史库存在悬空 `mistake.userExampleId`（旧备份导入、异常删除等路径可产生，`BackupOrphans` 已承认该数据形态），迁移会把悬空 `(userExampleId, idiomId)` 写入 `user_example_idiom`。`BackupWriter.merge` 不清理该表，而 `BackupWriter.verify` 的 `BackupRows.dangling(sqlite)==0` 断言会命中这些残留行 → 抛 `IllegalStateException` → 整单回滚。表现为：升级到 v3 且含此类历史数据的设备，合并备份导入 100% 失败，错误文案仅"导入写库失败，已回滚"，用户无法自行定位。清空数据（`ClearData` 先 `BackupRows.clear`）不受影响，但备份恢复这一唯一离线恢复通道被破坏。
- **建议**：回填加双重存在性条件——`EXISTS(SELECT 1 FROM user_example ue WHERE ue.id=m.userExampleId)` 与 `EXISTS(SELECT 1 FROM idiom i WHERE i.id=mi.idiomId)`；并在 `MIGRATION_2_3` 回读校验中断言 `SELECT COUNT(*) FROM user_example_idiom l LEFT JOIN user_example p ON p.id=l.userExampleId WHERE p.id IS NULL = 0`；为"悬空 userExampleId"补一条 v2→v3 造数据用例（现有 `UserExampleIdiomMigrationTest` 只测正常路径与中断回滚）。

---

## 中

### M1. review 孤儿判定使用"未过滤的 mistake id 集合"，合法备份被误判为写库失败

- **定位**：`BackupWriter.kt:78-90`（`references()` 的 `mistakeIds = localMistakes + backup.snapshot.mistakes.map { it.id }`）、`BackupOrphans.kt:31-35`、`BackupWriter.kt:73-75`、`BackupWriter.kt:163-164`
- **问题**：`resolve()` 先由 `resolveMistakes` 丢弃 `userExampleId` 悬空的错题，但随后用于 `resolveReviews` 的 `mistakeIds` 来自 `references.mistakeIds + mistakes.map{id}`，其中 `references.mistakeIds` 仍含**已被丢弃**的那条错题 id。于是"指向被丢弃错题的 review"不会按 §8.3「review.mistakeId 无对应错题 → 跳过并上报」被过滤，而被原样写入 `review_log`。
- **影响**：`BackupWriter.verify` 的 `dangling()==0` 随之失败 → 事务回滚 → `BackupImporter.restore` 抛 `DataError.Hard("导入写库失败，已回滚…")`。备份本身合法（含可修复孤儿），却被当成写库故障拒绝整单导入；`OrphanReport.reviewsWithoutMistake` 也不上报该条，用户与上层都拿不到真实孤儿数。`BackupOrphansTest` 与 `BackupImporterTest.allInvalidIdiomRefs…` 均未覆盖该交叉组合（测试里的 review 分别指向"保留的错题"和"根本不存在的 id"）。
- **建议**：在 `BackupOrphans.resolve()` 中以 `resolveMistakes` **实际保留**的 mistake id 集合（merge 再加本机 id）作为 `resolveReviews` 的 `knownMistakes`，不要把 `references.mistakeIds` 直接并入；补一条测试：备份含 `mistake(userExampleId=缺失)` + 指向该 mistake 的 review，断言整条导入成功且 review 被计入 `reviewsWithoutMistake`。

### M2. 备份数组内 id 唯一性未在 Android 端校验，重复 id 延迟到写库阶段才失败

- **定位**：`BackupSchema.kt:29-142`（`shape`/`array` 只校验字段与元素，无跨元素去重）、`BackupRows.kt:19-29`、`BackupRows.kt:37-45`、`BackupRows.kt:48-55`、`BackupImporter.kt:62-73`
- **问题**：`validate_schema.py`（规范 §4.5 fixtures 口径）会拒绝重复 id 的备份，但端侧 `BackupSchema` 没有对应检查；`BackupRows.mistake/userExample/review` 用 `INSERT INTO`（Room 实体为 ABORT 语义），没有 `OR IGNORE`/`OR REPLACE`。
- **影响**：一份含重复 mistake/userExample/review id 的损坏备份能通过 `BackupImporter.validate` 的 schema、sha256、counts 三关，直到事务内插入时抛 `SQLiteException` 才回滚；用户看到的是"导入写库失败"而非"备份损坏，请更换文件"，且覆盖模式已生成安全副本、P22 已完成，体验割裂。重复 `idiomIds` 虽被 `INSERT OR IGNORE` 吸收，但 kept 数组里的重复元素会虚增 `counts` 展示口径之外的写入尝试。
- **建议**：在 `BackupImporter.verify()` 增加 id 唯一性检查（`mistakes`/`userExamples`/`reviews` 按 `id`，`idiomStates` 按 `idiomId`），命中即抛 `DataError.Hard("备份文件字段异常或校验失败")`；同时按同样规则清理各条 `idiomIds` 内的重复值，使端侧校验与 `validate_schema.py` 对齐。

### M3. 覆盖导入的安全副本放在 cacheDir 且秒级命名，与错误提示的承诺不符

- **定位**：`BackupImporter.kt:78-93`（`File(context.cacheDir, SAFETY_PREFIX + fileStamp() + ".zip")`）、`BackupImporter.kt:69-72`（错误文案"请用自动留存的备份副本恢复"）
- **问题**：安全副本写入 `cacheDir`，系统低存储时可随时清理；文件名用本地时间秒级（`BackupTime.fileStamp()`，`BackupModels.kt:32`），同一秒内的两次覆盖导入会相互覆盖。
- **影响**：事务失败或后续需要回滚时，提示引导用户去找的副本可能已不存在或只剩第二次的文件，用户实际无法按提示恢复；这与"覆盖前留安全副本"的兜底设计相矛盾。
- **建议**：改写到 `filesDir/backups/` 等非清理目录，文件名加毫秒或 UUID 后缀避免同秒覆盖；保留文件上限与清理策略需产品确认后再定。若坚持 cacheDir，则把错误文案改为不承诺具体副本可用。

---

## 低

### L1. v3 回填只覆盖标错录入题，未标错的历史录入题仍无成语归属

- **定位**：`DbMigrations.kt:55-60`（回填源仅 `mistake` + `mistake_idiom`）、`MistakeQueryDao.kt:89-96`（P09 按 `user_example_idiom` 过滤）
- **问题**：v2 时代未标错的纯录入题没有 `mistake` 行，其 `user_example_idiom` 归属无法从 `mistake_idiom` 推导；v3 回填只处理 `mistake.userExampleId` 非空的行，这些题升级后关联表为空。规范 §4.5 v3 方案本身只要求回填标错题，因此这是**规范边界**而非实现偏离。
- **影响**：v2 存量未标错录入题在升级后不会出现在 P09「某成语下的录入题」（`userExamplesByIdiom`），用户需手动编辑补归属。现有迁移测试未覆盖该场景。
- **建议**：不改迁移口径（避免自行加规则）；在发布说明/帮助文案注明该边界，并在 P09 空态提供"去录入/编辑归属"的明确入口；若需自动回填，须先由产品拍板判定规则。

### L2. 组件库绕过 `ChenYuBangTheme(darkTheme=...)` 直接读系统深色模式，主题状态未向下传递

- **定位**：`ChenYuBangTheme.kt:14-18`；`CList.kt:42`、`CListRow.kt:107`、`CSegmented.kt:38`、`CTabBar.kt:30`、`CEmptyState.kt:39`、`CInput.kt:76`、`COverlay.kt:35` 等多数构件
- **问题**：主题函数接收 `darkTheme` 并据此构造 `MaterialTheme`，但每个构件仍各自调用 `isSystemInDarkTheme()` 取 Tokens 色组，参数没有通过 CompositionLocal 传递。快照测试靠同时改 `LocalConfiguration.uiMode` 才保持两者一致（`CListRowSnapshotTest.kt:36-41`、`CEmptyStateSnapshotTest.kt:40-48`），等于用测试环境掩盖了该耦合。
- **影响**：任何显式指定 `darkTheme`（深浅切换偏好、嵌入非跟随系统的容器、单测/预览）的场景，构件配色与 `MaterialTheme.colorScheme` 会分裂；后续若加手动主题切换，需逐文件返工。
- **建议**：在 `ChenYuBangTheme` 注入 `LocalColorSet = c` 之类的 CompositionLocal，构件统一 `LocalColorSet.current`，与 MaterialTheme 共用同一个 `darkTheme` 决策点。

### L3. `CSegmentedControl` 以 index 作 key，items 变动时错位复用组合状态

- **定位**：`CSegmented.kt:49-55`（`items.forEachIndexed { index, label -> key(index) { ... remember { MutableInteractionSource() } ... } }`）
- **问题**：`items: List<String>` 是可变参数，`key(index)` 只用位置标识组合项；`remember` 的 `MutableInteractionSource` 与按压态绑定在位置而非 label 上。
- **影响**：若调用方在运行中插入、删除或重排分段项，Compose 会把旧位置的按压/交互状态复用到新 label；当前项目分段项固定时无感，但构件作为通用组件库不具备稳定性保证。
- **建议**：改为 `key(label)`，或让入参携带稳定 id；至少在 KDoc 注明 `items` 须为稳定有序列表。

### L4. `PackUpdateCoordinator` 自建协程作用域无关闭接口

- **定位**：`PackUpdateCoordinator.kt:40-41`（`CoroutineScope(Dispatchers.IO + SupervisorJob())`、`inFlight: Deferred<...>`）
- **问题**：协调器内部持有长生命周期 scope 与 `inFlight`，类未实现 `close()`/`cancel()`，也不接收外部 scope。
- **影响**：协调器被替换或页面/容器销毁后，`performCheck()` 里的下载与导入仍可能继续并在 `_state` 上写值；若 scope 链路上捕获了外部引用，会延迟到协程自然结束才释放。当前无用户可观测故障，属资源生命周期隐患。
- **建议**：注入外部 `CoroutineScope`（如应用级 scope）或提供 `fun close()` 取消内部 scope 并清空 `inFlight`；在 `continueOffline()` 之外统一处理取消后的状态复位。

---

## 明确排除（无问题，勿重复提报）

- 12 表实体、索引与 `@Database(version=3)`：`Entities.kt:13-152`、`AppDatabase.kt:227-245` 与 `migrations.json` v1/v2/v3 登记一致；`MIGRATION_1_2`/`MIGRATION_2_3` 只增表增列，无破坏性变更。
- 导入隔离：`PackContent.clear/refreshScopes`、`PackImporter.import` 只操作 `idiom`/`example`/`idiom_scope`/`example_idiom`/`idiom_fts`；备份导入与清空只操作用户表，互不越界（规范 §4.6）。
- ZIP 严格校验：`BackupArchive`/`PackArchive` 的 EOCD、重复路径、CRC、压缩/展开上限、data descriptor 校验完备。
- 序列化健壮性：`PackJson`（重复键、NFC 碰撞、深度上限、孤立代理项、整数规范化）、`BackupDecode`/`BackupSchema` 的可选字段兼容与空 `idiomIds` 口径符合 v1.10 变更 1。
- 事务与协程：所有写路径都在 `withTransaction`/`@Transaction` 或手动事务内，`withContext(Dispatchers.IO)` 覆盖 DB 与文件操作；`BackupImporter`/`PackImporter` 用 `Mutex` 串行化导入，`PackUpdateCoordinator` 用 `Deferred` 去重并发 check。
- Tokens 生成文件：`gen_tokens.py --check` 通过，无手改。
- 快照覆盖：21 个快照测试覆盖全部 public 构件；`CInput` 含默认/错误/禁用/聚焦/带槽 × 深浅，`CButton` 含 5 变体 × 4 态 × 深浅，`COverlay` 含 dialog/sheet × 深浅；无实质覆盖缺口。
- 错误态/空态：`ReviewScoring.judge` 对"未记录首次作答/无选项"如实返回不可比对且不落库；`CEmptyState` 结构与信息架构 5.4 一致；`DataError.Hard/Recoverable` 与红/橙分级一致。
