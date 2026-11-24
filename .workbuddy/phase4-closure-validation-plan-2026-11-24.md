# 阶段 4 · 数据主权四屏 收口轮验证计划（P15 / P16 / P22 / P23）

生成日期：2026-11-24 · 角色：qa-engineer（严过关）
性质：**验证计划，仅规划不执行**。本轮（用户 2026-11-24 拍板）只交功能代码；Android 门禁、单测、快照、真机实测全部**未执行**，统一压到功能收口后一轮跑。
覆盖提交：core-data `2b49545` / `fa038bd` / `f7abd4d`；UI 接线 `d4c6a77` / `d0b54a5`。
事实源：`.workbuddy/android-4.1-4.4-backup-2026-11-23.md` 第六节；`.workbuddy/phase4-visual-contract-2026-11-24.md` 第 6 / 7 节。

---

## 1. 收口轮门禁命令（本轮未执行）

### 1.1 Android 门禁（项目根 `app-android/`）
`cd app-android && ./gradlew :app:assembleDebug test ktlintCheck detekt :app:lintDebug`
- 判定：BUILD SUCCESSFUL、exit 0；`test` 各套件 0 失败；ktlint / detekt / lintDebug 无新增违规。
- 记录：退出码、套件数 / 用例数 / 失败数、日志绝对路径。

### 1.2 共享三组（项目根）
- `python tools/gen_tokens.py --check` — tokens 与生成物一致，exit 0。
- `python tools/validate_schema.py` — backup-data / backup-manifest schema 校验通过，exit 0。
- `.venv/Scripts/python tools/baseline.py --check` — 基线脚本一致，exit 0。
- 记录：三条命令各自退出码 + 日志路径。

### 1.3 快照 verify（见第 3 节）
`cd app-android && ./gradlew :feature-me:verifyRoborazziDebug`

> 状态：以上 1.1 / 1.2 / 1.3 本轮**全部未执行**；仅列命令与判定口径，收口轮一次到位。

---

## 2. 单测断言点（开发规范第 4.5 节 + 开工清单第 69 行；本轮未执行，仅列断言）

测试落点建议：`app-android/core-data/src/test/kotlin/com/chenyubang/core/data/Backup*Test.kt`（沿用现有 Pack*Test 同目录）。

| # | 断言点 | 实现文件 / 类 | 预期结果 |
| --- | --- | --- | --- |
| 1 | 合并同 id 保留本机 | `BackupWriter.kt` `BackupWriter.merge`（localKeys / incoming 过滤）；`BackupImporter.kt` `restore(MERGE)` | 同 id 的 mistake / userExample / review / idiomState / scope 一律留本机值，导入侧重复项不叠加；`verify` 条数 = 本机 + 实际写入 |
| 2 | 覆盖失败回滚 | `BackupImporter.kt` `restore(OVERWRITE)`（safetyCopy + withTransaction）；`BackupWriter.kt` `overwrite` | 写库中途异常 → Room 整体回滚、用户表保持导入前状态、抛 `DataError.Hard`；cacheDir 留有 `backup-before-import-*.zip` 安全副本 |
| 3 | sha256 不匹配拒绝 | `BackupImporter.kt` `BackupImporter.verify` | 篡改 data.json 后重算 sha 不符 → 抛硬错误、不写库、不进 P22 |
| 4 | schemaVersion 超版拒绝 | `BackupImporter.kt` `BackupImporter.verify`（`version > SCHEMA_VERSION`） | 高于当前支持版本 → 抛硬错误、不写库、不进 P22；低版本 / 相等放行 |
| 5 | counts 五项与实际条数一致 | `BackupExporter.kt` `emit`/`snapshot.counts()`；`BackupDocument.kt` `counts`/`declaredCounts`/`manifest`；`BackupImporter.kt` `verify` | manifest.counts 恰含 mistakes/userExamples/reviews/idiomStates/scopes 五项，各等于 data.json 对应数组实际条数；不一致即硬错误拒绝 |
| 6 | P10 原图占位 | `BackupRows.kt` `BackupRows.mistake`/`userExample`（imagePath 写 `null`）；`BackupDecode.kt` `optionalString("imagePath")` | 导入后 mistake.imagePath 与 user_example.imagePath 均为 NULL；P10 原图位显示橙占位「原图已不在」，不阻断入库 |
| 7 | 旧备份缺 userExamples 兼容 | `BackupSchema.kt` `data`/`counts`（userExamples 设 optional）；`BackupDecode.kt` `userExamples`（`.orEmpty()`） | schemaVersion=1、无 userExamples 键的旧备份可正常解析与校验，视为空数组，不报字段异常 |
| 8 | 覆盖导入 exam_scope 整体替换 | `BackupWriter.kt` `overwrite`（`DELETE FROM exam_scope` + `syncProvinces`） | 覆盖后 exam_scope 行与备份完全一致、无旧范围残留、同键复用 id；`settings.examScope.provinces` 与备份省考省份同步 |
| 9 | 孤儿上报 | `BackupOrphans.kt` `BackupOrphans.resolve` | mistake.userExampleId 悬空 → 跳过错题并上报；idiomIds 悬空 → 只跳过关联、保留整条并上报；review.mistakeId 悬空 → 跳过并上报；返回 `OrphanReport` 四类计数 |

- 补充回归：`ClearData.kt` `ClearData.clear` — 只清六张用户表、保留 idiom/example/exam_scope/idiom_scope/example_idiom 与 `settings.theme`，写 `lastClearedAt`，事务内 `dangling == 0`。
- 补充状态：`PackVersion.kt` `load`/`check`/`mockState`/`recoverable`/`remoteState` 与 `PackUpdateSource.kt` `fetch` — 三级兜底顺序不颠倒；mock 条数单独字段不混写；校验失败保留版本 + 可重试；仅 HTTPS、超 32MB 拒绝。

---

## 3. 快照计划（本轮未执行，仅规划）

- 范围：P15 `P15BackupScreen` / P16 `P16PackScreen` / P22 `P22ImportModeSheet` / P23 `P23ClearDialog` 四屏，各浅 / 深两套。
- 新增测试落点：`app-android/feature-me/src/test/kotlin/com/chenyubang/feature/me/P15BackupSnapshotTest.kt` 等（参照 `P14ExamScopeSnapshotTest.kt` 的 `captureRoboImage` / `@Config(sdk=34, qualifiers=w360dp-h1280dp-xhdpi)` / `ChenYuBangTheme(darkTheme=night)` 结构）。
- 命名：保留 `.1` 后缀（同名多帧仍写 `.1`，不改名丢帧）。
- record 轮**不作证据**：仅生图，不纳入通过判定。
- 收口轮 verify 必须**真绿**：`:feature-me:verifyRoborazziDebug` BUILD SUCCESSFUL，逐帧 unchanged，recorded/added/changed 均为 0；缺基线会红。
- 残留 PNG 清理：verify 后 `git status --porcelain` 应干净，src 下无 verify 残留新 PNG，有则删除。
- 审阅纪律：改动后先审阅再 record/verify，不覆盖既有基线图。

---

## 4. 静态 / 手工与真机后置项（本轮整体后置，不挂真机待办）

- 杀进程重启（验收 14）：备份/导入/清空后杀进程重启，状态与条数持久化正确。
- 文件面板手测（验收 16–18）：选文件 / 另存网盘走系统面板，面板空态跟随系统、App 不自造第二套。
- 系统分享仅到面板、零上传：入口只调系统分享，不接网盘账号、不写相册、无网络上传（对照 `BackupExporter.exportTo`）。
- P22 硬错误不进 sheet：`BackupImporter.validate` 抛硬错误（超版 / sha 不符 / 字段异常）→ `P15BackupViewModel` 走 `P22_REJECT_TITLE_PLACEHOLDER` 红硬错误条，不弹 `P22ImportModeSheet`、无「稍后再试」退路文案。
- 静态核对：一处红约束（P22 覆盖红徽标、P23 红填充各为各自屏唯一红）；文案 / 几何深浅一致，仅令牌值不同；图标无 emoji。

---

## 5. 占位串替换清单（来自视觉契约第 6 节；收口轮前须用户拍板逐字替换，未替换不宣称完成）

| 视觉契约编号 | 待拍板文案 | 实现占位常量 / 落点 | 文件 |
| --- | --- | --- | --- |
| 1 | P15 导出成功条「条数格式」 | `P15_EXPORT_SUCCESS_TITLE_PLACEHOLDER`、`p15ExportCountsPlaceholder()` | `P15BackupViewModel.kt` |
| 2 | P15 导出失败红硬错误条 | `P15_EXPORT_FAILURE_TITLE_PLACEHOLDER` | `P15BackupViewModel.kt` |
| 3 | P22 schemaVersion 超版 / sha256 不符硬错误拒绝态 | `P22_REJECT_TITLE_PLACEHOLDER` | `P15BackupViewModel.kt` |
| 4 | P16 更新中 / 下载中带进度 | `P16_CHECKING_PLACEHOLDER` | `P16PackScreen.kt` |
| 5 | P16 更新成功（非「版本相等」） | `P16_UPDATE_OK_PLACEHOLDER` | `P16PackScreen.kt` |
| 10 | P15「最近一次备份时间」值位 | `P15_LAST_BACKUP_VALUE_PLACEHOLDER` | `P15BackupViewModel.kt` |
| 附（结果态） | P15 导入写库失败红硬错误条 | `P15_IMPORT_FAILURE_TITLE_PLACEHOLDER` | `P15BackupViewModel.kt` |
| 附（结果态） | P15 导入完成结果条（条数 / orphans / 安全副本格式） | `P15_IMPORT_DONE_TITLE_PLACEHOLDER`、`p15ImportDetailPlaceholder()` | `P15BackupViewModel.kt` |
| 附（结果态） | P23 清空失败提示 | `P23_CLEAR_FAILURE_PLACEHOLDER` | `P23ClearViewModel.kt` |

- 用户拍板（第 7 节）：编号 1–5、10 统一用占位串落地；占位串须命名 / 注释显式标「待拍板」，不复用近邻稿串填空（禁挪用「检查失败，请检查网络后重试」「正在保存…」「已是最新版本」）。
- mock 三串已定稿（2026-11-24 用户澄清）：「测试数据（TEST）」/ mock 实际条数 /「题库未获取成功」为正式文案，实现按此落地，不属缺稿占位；仅「是否补独立高保真屏」仍待拍板（第 6 节）。
- 替换纪律：只替文字，颜色 / 字号仍走 0.1 / 0.2 令牌，禁硬编码；全部替换前相关态不宣称完成。

---

## 6. 风险与待拍板

- P16 mock 态无独立高保真屏（第 6 节编号 6）：D-v2 覆盖表无 mock 行；三串文案已定稿（测试数据（TEST）/ mock 实际条数 / 题库未获取成功，非缺稿占位），仅「是否补一屏独立高保真屏」待拍板。
- P16 退路条数（编号 7）：开发规范 v1.8 第 590 行要求 2–3 条，D-v2 E18 仅一张「重试前可以确认」卡（网络 + 下载中断两句 + 注记）；是否满足 2–3 条待确认。
- P23 影响卡文案差异（编号 8）：稿面「错题 12 道 · 收藏 4 个 · 学习状态」vs 清单「录入题（含标错的错题）、收藏、学习状态」；稿面未点名「录入题」，待拍板（见 `P23ClearDialog.kt` 影响卡）。
- 孤儿 idiomIds 过滤后可能为空 vs minItems=1：`BackupOrphans.resolve` 按「不丢弃整条」保留记录但 idiomIds 变空数组，与 `BackupSchema` 的 `array(uuid, 1)` 及「归属至少一条」冲突；`BackupOrphans.kt` 类注释已标待拍板——放宽 schema 或补归属，二选一由主控拍板。
- P15「导出按钮」形态（编号 9）：开工清单称「导出按钮」，C-v2 主态是两条 `.lrow` 入口（存本地 / 系统分享另存网盘），无 `.btn`；是否另加按钮待确认。
- P16 版本号 / 条数为排版示意（编号 11）：稿面 v1.0.0、1,286 / 512 与定稿真实值（59 成语 / 11 真题）不同；实现取真实值、不写死示意值（已在稿注明，非矛盾）。

---

## 7. 收口轮通过口径（判定用，非本轮结论）

- 门禁 1.1 / 1.2 三组 + 快照 verify 全绿、exit 0、日志归档。
- 第 2 节 9 项断言全部通过，补充回归 / 状态项不回退。
- 第 5 节占位串全部替换为拍板逐字串；未替换项逐一标注、相关态不宣称完成。
- 第 6 节待拍板项获主控 / 用户明确结论后方可定稿。
- 本轮（功能代码阶段）以上均**未执行 / 未达成**，不得在对外交付中宣称验证通过。

## 8. 收口轮执行结果（2026-11-24，用户派发执行门禁/单测/快照，不做真机）

- Android 门禁命令 `:app:assembleDebug test ktlintCheck detekt :app:lintDebug`：先因环境竞争（Robolectric 下载锁、Gradle 测试子进程超时、Kotlin 守护进程抢目录）与本次新增代码静态违规失败；串行 `--max-workers=1` 重跑后 BUILD SUCCESSFUL，exit 0，日志 `.workbuddy/evidence/phase4-android-gate-final4-2026-11-24.log`。
- 共享三组：`gen_tokens --check` / `validate_schema.py` / `baseline.py --check` 均 exit 0；tokens 一致、6 schema 与备份包 counts/sha256 通过、90 屏基线与 manifest 一致；日志 `.workbuddy/evidence/phase4-{gentokens,validate-schema,baseline}-2026-11-24.log`。
- 单测：全项目 505 项，failed 0、skipped 0；core-data 109、feature-me 13 全绿。均为既有测试，第 2 节新增 9 项断言尚无对应测试文件。
- 快照：`:feature-me:verifyRoborazziDebug` BUILD SUCCESSFUL、exit 0、无残留 PNG；仅覆盖既有 P14 深浅两张基线，四屏快照测试与基线文件在仓库中不存在。
- 修复提交 `990c8e4` fix(P15)：ktlint 格式 13 处 + detekt 违规 19 处（core-data 8、app 2、feature-me 7 + 文件函数数联动），全部改实现；校验口径与占位串语义未变。
- 未执行：真机实测（按用户要求）、四屏专项单测与四屏快照基线（仓库无对应文件，非门禁失败）、第 5 节占位串替换（须用户拍板）、第 6 节待拍板项。
- 结论：门禁/既有单测/既有快照全绿；新增逻辑的专项覆盖仍缺，不宣称阶段 4 完成验证。

### 8.1 缺口补齐执行（2026-11-24 追加派发，用户要求：补 9 项断言 + 四屏八张快照，保留现有全绿）

- 备份专项单测：新增 core-data 测试 6 文件（BackupFixture / BackupDb / BackupDocumentTest / BackupImporterTest / BackupOrphansTest / BackupClearAndPackTest）+ 测试资源包 1 份；12 个 @Test 覆盖第 2 节 9 项断言 + ClearData / PackVersion 2 项补充回归 + 合并范围补充，全绿。此前「Backup 测试 0 个」缺口已消解。
- 四屏快照：新增 P15Backup / P16Pack / P22ImportMode / P23ClearDialog 四个 SnapshotTest，各浅深一套共 8 张基线；P14 两张既有基线未改。为注入稳定 state，feature-me 四屏 main 文件各加 internal Content 重载，Route 改为调该重载，不改视觉/文案/占位串/业务逻辑。
- record 轮：exit 0，8 张基线生成；按计划不作通过证据。逐图审阅：QA 逐张审 8 张，主控抽查 P15 浅 / P22 深 / P23 深 / P16 浅 4 张，确认深浅两套、完整渲染、无空白错位、无 emoji、P16 非 TEST 态、P23 红色仅确认按钮；P22/P23 首轮因 overlay 双 root 截屏失败，已修测试写法并重录。
- 命名：本次无同名多帧，未触发 .1 后缀；保留 .1 口径不变，不改名丢帧。
- 重跑 verify：`:feature-me:verifyRoborazziDebug` BUILD SUCCESSFUL、exit 0、unchanged，added / changed 均为 0；git status 无 verify 残留新 PNG，src 下无临时产物。
- 全量门禁重跑（补测后）：`:app:assembleDebug test ktlintCheck detekt :app:lintDebug` BUILD SUCCESSFUL，日志 .workbuddy/evidence/phase4-full-gate-after-backup-2026-11-24.log。全项目单测 debug 525 项 + release 525 项 = 1050 项，failed 0、skipped 0；ktlint / detekt / lintDebug 全绿。
- 提交：989a118 `test(backup): 补备份专项单测与四屏深浅快照`，23 文件（1027 行新增），标题带 scope、trailer Generated-by: deepseek；只含测试资产与为可测性新增的 Content 重载，未碰 .codebuddy/* 与其它脏文件。
- QA 报中危缺陷已于 2026-11-24 定性为技术缺陷并修复（非业务待拍板）：用户核实 kotlinx.serialization 的 JsonNull 继承 JsonPrimitive 且 content 为字符串 "null"，故 PackSchema.optionalString 对 JSON null 错误返回 "null"，影响所有可空字段而非仅 province。修复提交 b5478ad fix(backup)：optionalString 加 takeIf { it !is JsonNull }，只改一处；已补 nullable 字段解码 / NULL 同键合并 / NULL 同键覆盖复用 id 三类测试并重跑门禁与 verify，见第 8.2 节。
- 未执行 / 未变：真机实测（按用户要求不做）、占位串替换与第 6 节待拍板项（用户本轮明确不在补测范围，仍按收口计划处理）、既有 505 项测试与 P14 基线未动。
- 结论：第 8 节原「新增逻辑专项覆盖仍缺」缺口已补齐；门禁 / 1050 项单测 / 10 张快照 verify 全绿，新增逻辑已有专项覆盖。占位串与待拍板项仍挂起，不宣称占位文案已完成。

## 9. iOS 收口执行（2026-11-24，主控）

- 共享三组门禁重跑通过，UTF-8 日志 `.workbuddy/evidence/phase4-ios-shared-gates-2026-11-24.log`。
- 全量录制轮 `45362501808` 覆盖 453 项；与旧基线精确差集为 7 张共享弹层漂移、8 张阶段 4 新增、0 缺失。15 张均逐张审阅，发现 P16 版本号重复前缀，提交 `790aac2` 修正。
- P16 定向重录轮 `45369344420` 的 8 项均仅有 Record mode 预期提示；两张 P16 审阅通过，其余六张与已审阅图逐字节相同。P16 两图提交 `d790d38`。
- 当前本地基线与重录产物精确一致：246 张、missing 0、extra 0、changed 0；7 张漂移图提交 `4fb1c66`，8 张阶段 4 新图全部齐备。
- 导出测试修正：UUID 规范化用例已通过；`dbef68a` 修正按 id 取目标错题、数值版本号 fixture、Unicode 标签顺序三条断言，不放宽业务校验。
- 全量 verify 轮 `45372436791`（record=false、scope=all、无 test_filter）失败：静态检查与编译通过，但 `BackupImportOrphanTests.testMergeResolvesOrphansAndReportsSkippedRows` 在写事务闭包内构造 fixture，fixture 又读取同一队列，触发 `Database methods are not reentrant`；进程重启后的汇总 0 failures 不构成通过证据。失败日志归档 `.workbuddy/evidence/phase4-ios-verify-45372436791-failed.log`。
- 测试修复 `76d9966` 仅将 fixture 构造移到 `queue.write` 之前，合并孤儿过滤与跳过计数断言不变；该轮 `45375717919` 已无崩溃，但暴露三条错误期望：孤儿错题跳过且共享错题保留本机时新增错题为 0；指向本机错题的新复习按合并规则跳过，新增复习为 0 且该 id 不落库。`1002bb7` 只修正这三条期望，未改实现。
- 修正后全量 verify 轮 `45378590664` 通过：head `1002bb7`，record=false、scope=all、无 test_filter；SwiftLint、编译、454 项测试全绿，0 failures / 0 unexpected，无 fatal、无进程重启、无 Record mode。绿色日志与元数据归档 `.workbuddy/evidence/phase4-ios-verify-45378590664-success.{log,json}`。
- iOS 阶段 4 PR #49 已合并：PR CI `45381108480` head `1002bb7`，454 项 0 failures / 0 unexpected，无崩溃重启、无 Record mode；日志归档 `.workbuddy/evidence/phase4-ios-pr49-45381108480-success.log`。合并提交 `4139489`，main 已更新。结果态文案与第 6 节产品冲突仍待拍板，不宣称双端阶段 4 最终收口。
- Android 静态复核证实的 `province=null` 缺陷已由 `7961517` 修复，回归测试已由 `03eb5ce` 纳入 Git；定向测试、全量门禁和快照 verify 日志均 BUILD SUCCESSFUL。结果态文案待拍板项仍阻断双端收口，详见开工清单第十节。

### 8.2 JsonNull 技术缺陷修复与回归（2026-11-24，用户定性为技术缺陷并派修）

- 定性变更：province=null 问题从业务待拍板改为技术缺陷。用户核实 kotlinx.serialization 的 JsonNull 继承 JsonPrimitive、content 为字符串 "null"，PackSchema.optionalString 对 JSON null 错误返回 "null" 而非 null，影响所有可空字段不限于 province。
- 修复提交 `7961517` fix(backup)：只改 PackSchema.kt 的 optionalString 一处，加 takeIf { it !is JsonNull } 并拆行满足 ktlint；string()/integer()/其它 schema 校验与业务口径未动。首版单行被 ktlint 拦，已 amend 修正后复验全绿。
- 回归提交 `03eb5ce` test(backup)：新增 BackupDecodeNullTest.kt（185 行、3 个 @Test）覆盖用户点名三类——nullable 字段解码（JSON null / 缺失键 / 非 null 串三态）、NULL 同键 MERGE、NULL 同键 OVERWRITE 复用 id 且 settings 不写 null。专项单测 3 项全绿，无 @Ignore / 空断言。
- 全量门禁重跑：`:app:assembleDebug test ktlintCheck detekt :app:lintDebug` BUILD SUCCESSFUL、exit 0，日志 .workbuddy/evidence/phase4-jsonnull-full-gate-2026-11-24.log。单测 debug 528 + release 528 = 1056 项，failed 0、skipped 0（较 8.1 的 525×2 增 3×2）。
- 快照 verify：`:feature-me:verifyRoborazziDebug` BUILD SUCCESSFUL、exit 0，unchanged 10、added/changed 0、无残留 PNG；日志 .workbuddy/evidence/phase4-jsonnull-snapshot-verify-2026-11-24.log。缺陷修复未引起快照变化，未 record 覆盖基线。
- 证据保留：8.1 全绿证据（989a118 时期的门禁 / verify / 1050 项单测日志）与 8.2 新证据并存，均归档 .workbuddy/evidence/，未删改。
- 未覆盖声明：占位串替换与第 6 节待拍板态本轮明确不在 JsonNull 修复范围，仍未替换 / 未拍板，不宣称相关态完成；快照只取主态，未覆盖结果态。
- mock 文案状态修正（2026-11-24 用户澄清）：「测试数据（TEST）」/ mock 实际条数 /「题库未获取成功」三串已定稿，非缺稿占位；第 5 节已移除编号 6 的占位条目，第 6 节仅保留「是否补独立高保真屏」待拍板。
- 结论：技术缺陷已修并有三类回归覆盖，门禁 / 1056 项单测 / 10 张快照 verify 全绿；占位串与待拍板态仍未覆盖。

### 8.3 拍板差集专项快照与全量验证（2026-11-24，qa-engineer，用户要求真实执行 Gradle、不做真机）
- 覆盖 head：差集实现 `97d2ed5`；静态修复 `e177cc5`（ktlint 表达式体换行）、`320ab56`（detekt MagicNumber，提取 `PROGRESS_MAX=100`），两笔均 backend 实现文件、仅格式/常量、零行为变更；本轮 QA 提交号见文件回填后的最终 head。
- 提交内容：新增/修改的专项测试、快照基线、两份证据日志与本计划 8.3，未碰产品实现、未碰 `.codebuddy/*` / `AGENTS.md` / `shared/schema/*` / `docs/*` / `.qoder-credits/*` 及其它 `.workbuddy/*` 脏文件。
- 全量门禁 `cd app-android && ./gradlew :app:assembleDebug test ktlintCheck detekt :app:lintDebug --max-workers=1`：最终 BUILD SUCCESSFUL、exit 0，日志 `.workbuddy/evidence/phase4-diff-full-gate-2026-11-24.log`。中途两次红灯均非测试资产：先 `:core-data:ktlintMainSourceSetCheck` PackVersion.kt:87 表达式体换行，后 `:core-data:detekt` 同文件 coerceIn 字面量 100 MagicNumber，均由 backend 提交修复后重跑转绿。
- 单测：debug 561 + release 561 = 1122 项，failures 0、errors 0、skipped 0（来源 `build/test-results/test{Debug,Release}UnitTest/*.xml`，80 文件/变体）。
- 专项快照：新增 30 张（P15 10 / P16 10 / P22 2 / P23 6 / P08 2），更新 6 张既有基线（P15 浅深主态、P22 浅深 sheet、P23 浅深主态，因 97d2ed5 文案变更），差集合计 36 张；P14 两张基线 sha256 前后一致（d27b7951… / 03400601…）未覆盖，P08/P09/P10 既有基线未动。
- 覆盖矩阵：P15 主态/导出成功/导出失败/导入中/导入成功/备份无法读取 × 深浅；P16 BUILTIN(59/11)/CHECKING/DOWNLOADING 42%/UPDATE_OK/RECOVERABLE_FAIL/TEST_MOCK(3/2) × 深浅；P22 合并/覆盖 + 导入失败 × 深浅；P23 主态(含真实录入题 userExamples=7)/清空中/失败/成功 × 深浅；P08 空关联导入样本 × 深浅。TEST mock 3 成语/2 真题（fixture manifest.correct.json）与 BUILTIN 59/11 状态字段分离，逐图确认不混用。
- 逐图审阅：36 张变化图 + P16 BUILTIN 两张复核，文案逐字、深浅令牌、无空白错位、无 emoji 全达标；P16 非 TEST 与 TEST 态区分达标；P22 一处红、P23 主态/清空中/成功一处红达标；P08 显示「暂未关联成语」达标。逐图结论 `.workbuddy/evidence/phase4-diff-snapshot-review-2026-11-24.md`。
- 两项拍板（2026-11-24 用户结论，均保留现状、不改基线/令牌/产品口径）：P23 失败态两处红（行内红字 CInlineError + 红填充确认按钮）保留，作为偏离视觉契约第 0/4 节「一处红 / 确认清空为唯一红」的既定事实记录，不补快照、不改实现；P08 深色页头浅绿字配浅灰底对比偏弱保持现状，视为既有基线同款、非本轮差集，仅记录观察，不改令牌、不补快照。
- 快照 verify：`./gradlew :feature-me:verifyRoborazziDebug :feature-mistake:verifyRoborazziDebug --max-workers=1` BUILD SUCCESSFUL、exit 0，无 Record mode；结果 JSON feature-me unchanged 38、feature-mistake unchanged 48，added/changed 0；`*_actual.png` / `*_compare.png` 等 record 残留 34 张已清理，仅留新增基线，verify 无残留新 PNG。日志 `.workbuddy/evidence/phase4-diff-snapshot-verify-2026-11-24.log`。
- 同名多帧：本轮均单帧 capture，无 .1 后缀产生；保留 .1 口径不改名丢帧不变。
- 未覆盖 / 未变：真机实测（按用户要求不做）；第 5 节占位串已被 97d2ed5 结果态文案收口，非本轮动作；第 6 节待拍板三项（P16 独立 mock 高保真屏、P16 退路条数 2–3 条、P15 是否另加导出按钮）仍未拍板，本轮不宣称这些态完成。
- 用户拍板落定：P23 失败态一处红冲突保留现状，仅在证据与本计划注明偏离一处红契约；P08 深色页头对比保持现状，视为既有基线同款、非本轮差集，仅记录观察，均不改令牌、不补快照、不调口径。
- 结论：全量门禁、1122 项单测、38 张差集快照 verify 全绿；专项覆盖已补齐；P23/P08 两项观察已按用户拍板保留现状并记录，第 6 节三项待拍板项仍挂起。

### 8.4 P16 两句说明快照重录与复核（2026-11-24，qa-engineer，用户要求不真机）
- 覆盖提交：`bd7d33a` feat(me) P16 补齐两句全状态说明（`P16PackScreen.kt` 6 行，`UpdateArea` 公共末尾新增两句，text3/n115/lineHeight 1.65）。本轮 QA 提交为含本节证据的最终提交，最终 head 以交付时 git log 为准（见回传）。
- 重录范围：仅 P16 六态 × 深浅 = 12 张（BUILTIN / CHECKING / DOWNLOADING 42% / UPDATE_OK / RECOVERABLE_FAIL / TEST_MOCK；测试类无 UP_TO_DATE 独立用例，其分支与 BUILTIN 同构且未设基线，不在重录范围）。用现有 `P16PackContent` internal 重载注入稳定 state，未改产品文案/视觉/业务逻辑；非 P16 既有基线与 P14 两张一律不动。
- 重录张数：12 张。重录前后 38 张基线哈希比对，仅 P16 12 张变化，P14 两张哈希不变、其余 26 张不变；无 `.1` 后缀、无 actual/compare 残留 PNG；同名多帧 `.1` 口径不变。
- 逐图结论：12 张全部达标——两句逐字含标点与 `\n` 换行、深浅 text3/n115 令牌一致、无错位截断、无 emoji、按钮与退路未遮挡；TEST mock 3 成语 / 2 真题（fixture manifest.correct.json）与 BUILTIN 59 / 11 分离；详见 `.workbuddy/evidence/phase4-diff-snapshot-review-2026-11-24.md` 追加的「P16 两句说明重录审阅」小节。
- 快照 verify：`cd app-android && ./gradlew :feature-me:verifyRoborazziDebug --max-workers=1` BUILD SUCCESSFUL、exit 0、无 Record mode；结果 JSON 38 张全部 `unchanged`（其中 P16 12 张），added / changed 0；src 下无残留 PNG。日志 `.workbuddy/evidence/p16-verify-2026-11-24.log`。
- 完整门禁：`cd app-android && ./gradlew :app:assembleDebug test ktlintCheck detekt :app:lintDebug --max-workers=1` BUILD SUCCESSFUL、exit 0（2m25s）；日志 `.workbuddy/evidence/p16-full-gate-2026-11-24.log`。
- 单测：debug 561 + release 561 = 1122 项，failures 0、errors 0、skipped 0（来源各模块 `build/test-results/test{Debug,Release}UnitTest/*.xml`，80 文件 / 变体）。
- 未覆盖 / 未变：真机实测（按用户要求不做）；第 6 节待拍板三项（P16 独立 mock 高保真屏、P16 退路条数 2–3 条、P15 是否另加导出按钮）仍未拍板，本轮不宣称这些态完成；P23 失败态两处红、P08 页头对比按 8.3 用户拍板保留现状。
- 结论：P16 12 张重录图逐图审阅全达标；feature-me verify、完整门禁、1122 项单测全绿；无实现缺陷上报，未自行改实现。
