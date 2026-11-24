# 阶段 4 · Android 拍板差集清单（2026-11-24）

事实源：产品功能规划 v1.7 > 信息架构 v1.5 > 设计规范 v1.4 > 高保真稿；执行细节以开发规范 v1.9 为准；排期以开发排期 v1.2 为准。

本清单基于共享工作区 Android 当前 head `03eb5ce` 静态复核，只列未完成项。Qoder 不运行 Gradle、不碰共享工作区 index、不做真机实测。执行方为 WorkBuddy，完成后把提交、门禁、单测和快照证据回填本文件。

## 一、统一结果态（双端逐字一致）

替换或删除下列占位串及对应 `_PLACEHOLDER` 常量名，不再出现“占位·待拍板”。

- 导出成功标题：`导出完成`
- 导出失败：`导出失败，请重试`
- 备份无法读取：`备份无法读取，请更换文件后重试`
- 导入完成标题：`导入完成`
- 导入失败：`导入失败，请检查备份文件后重试`
- 清空失败：`清空失败，本机数据未变更`
- 清空成功：`学习数据已清空`
- 最近备份空值：`暂无备份记录`
- P16 检查中：`正在检查题库更新`
- P16 下载中：`正在下载题库更新（进度）`，按整数百分比显示
- P16 更新完成：`题库已更新`

P22 超版、sha256 不符、字段异常属于校验硬拒绝，不弹模式选择；文件读取或校验失败统一显示 `备份无法读取，请更换文件后重试`，导入执行失败统一显示 `导入失败，请检查备份文件后重试`。底层诊断可保留在测试日志，不作为用户主文案。

## 二、P15 / P22

文件：`app-android/feature-me/src/main/kotlin/com/chenyubang/feature/me/P15BackupViewModel.kt`、`P15BackupScreen.kt`。

1. 导出说明改为 `导出的内容：错题、录入题、收藏、学习状态。不含题库本身。`
2. 保留现有两条导出入口，不新增第三入口；另存网盘仍走系统分享面板，不上传学习数据。
3. 导出成功条数逐字使用：
   `错题 X · 录入题 X · 复习 X · 学习状态 X · 考试范围 X`
4. 导入结果条保留真实写入量、四类孤儿计数和安全副本说明，删除尾部占位标记；这些明细不另起未拍板模板。
5. P22 覆盖说明改为：
   `用备份文件替换本机现有记录。本机现在的错题、录入题、收藏、学习状态都会被清掉，之后无法找回。`
6. 导入进行态显示 `正在导入`，清空进行态显示 `正在清空`。

## 三、P16 离线题库版本页

文件：`app-android/feature-me/src/main/kotlin/com/chenyubang/feature/me/P16PackScreen.kt`、`P16PackViewModel.kt`；状态模型位于 `app-android/core-data/src/main/kotlin/com/chenyubang/core/data/PackVersion.kt`。

1. 检查中改为 `正在检查题库更新`，更新完成改为 `题库已更新`，删除占位注释和常量。
2. 增加下载进度展示状态与可注入快照入口，显示 `正在下载题库更新（42%）`；不要求阶段 4 接真实联网下载。
3. 可恢复异常 E18 必须同时提供：
   - `重试`：恢复既有检查行为。
   - `继续离线使用`：回到当前内置包离线态，不发请求、不写学习记录、不改题库版本。
   异常副标题保留当前版本号与离线可用说明。
4. 进入 P16 不自动检查更新；进入 P03 / P06 才自动检查，在途检查沿用既有合并去重。不要把 P16 的 `LaunchedEffect` 改成自动检查。
5. TEST mock 与 BUILTIN 不共用状态或文案。`PackMock` 的 3 成语 / 2 真题来自 `shared/fixtures/pack/manifest.correct.json`，是测试包实际条数，不应改成真实内置包 59 / 11；补测试钉住来源一致，并保持 `TEST` 与 `题库未获取成功` 标识。真实 BUILTIN 快照继续显示内置包实际 59 / 11。
6. 不新增独立 mock 高保真屏；用 Compose 专项快照覆盖 CHECKING、DOWNLOADING 42%、UPDATE_OK、RECOVERABLE_FAIL、TEST_MOCK 与 BUILTIN。
7. 按信息架构 v1.5 的 P16 第⑥至⑧块补齐两句说明：每次进入成语库页或例题页时自动尝试拉取一次，失败后可在 P16 重试或继续离线使用；随安装包内置的题库作离线底座，内置包也没有时才用标注 TEST 的 mock 兜底。现有「检查题库更新」按钮、更新状态区与「只下载题库增量包，不改动本机学习记录」保留。

## 四、P23 清空学习数据

文件：`app-android/feature-me/src/main/kotlin/com/chenyubang/feature/me/P23ClearViewModel.kt`、`P23ClearDialog.kt`。

1. 失败文案改为 `清空失败，本机数据未变更`，成功文案改为 `学习数据已清空`。
2. `P23ClearUiState` 增加 `userExamples`，`loadCounts()` 只读查询 `SELECT COUNT(*) FROM user_example`，与错题、收藏条数一起刷新。
3. 影响卡逐字格式：
   `错题 X 道 · 录入题 X 条 · 收藏 X 个 · 学习状态`
4. 保持现有清六表、保留题库与考试范围、二次确认和安全副本行为，不扩展清空范围。

## 五、备份导入空归属例外

文件：`app-android/core-data/src/main/kotlin/com/chenyubang/core/data/BackupSchema.kt`、`BackupOrphans.kt`；P08 行为文件：`app-android/feature-mistake/src/main/kotlin/com/chenyubang/feature/mistake/P08MistakeListRows.kt`。

1. `BackupSchema.kt` 两处 `idiomIds` 约束由 `array(uuid, 1)` 改为 `array(uuid, 0)`，允许导入态空数组。
2. 共享 `shared/schema/backup-data.schema.json` 已由 Qoder 同步为 `minItems: 0`；不要覆盖或改回。正常录入使用的 `shared/schema/user-examples.schema.json` 保持 `minItems: 1`。
3. 继续过滤失效成语 id，但保留整条错题或录入题；全部关联失效后允许该条 `idiomIds=[]`。不要改成丢弃整条，也不要给正常录入放开空归属。
4. `BackupOrphans.kt` 类注释与行内注释删除“待拍板”，写明这是备份导入态例外，并保留现有生产过滤算法。
5. P08 平铺行的成语位置为空时显示 `暂未关联成语`，不能留空白；导入态零归属错题在时间或考试类型视图仍可点击进入 P10。P10 已有相同提示，无需重复修改。
6. 必须补回归：
   - 含空 `idiomIds` 的备份通过校验并解码为空数组。
   - 错题与录入题全部成语引用失效时，本体仍保留、关联表为零行、四类孤儿计数正确。
   - 正常录入零成语仍被拒绝，不写文件或数据库。
   - P08 空关联行显示提示且可进入 P10。

## 六、快照与门禁

1. 保留现有基线，不覆盖未审阅 PNG；record 轮只产图，不作为通过证据。
2. 逐张审阅后只回填差集，保留 Roborazzi `.1` 后缀；最终 verify 必须真实全绿并给出 added/changed 数量。
3. 至少覆盖：
   - P15：主态、导出成功、导出失败、导入成功、备份无法读取，各深浅。
   - P16：BUILTIN、CHECKING、DOWNLOADING 42%、UPDATE_OK、RECOVERABLE_FAIL、TEST_MOCK，各深浅。
   - P23：主态及真实录入题条数，深浅。
   - P22：现有合并 / 覆盖及导入失败态，保持双端语义一致。
   - P08：空关联导入样本。
4. 执行 Android 完整门禁：
   `cd app-android && ./gradlew :app:assembleDebug test ktlintCheck detekt :app:lintDebug`
5. 单独执行快照 verify，并回传实际测试数、BUILD SUCCESSFUL、无 Record mode、无残留 PNG 的日志。
6. 不做真机实测；文件面板、杀进程重启等平台行为按项目口径整体后置。

## 七、交付证据

- 提交标题带 scope，正文沿用 `- ` bullet，末尾空一行加 `Generated-by: WorkBuddy`。
- 回填最终 head、提交列表、完整门禁结果、单测数量、快照审阅结论与 verify 结果。
- 对“全部成语关联过滤后本体保留”和“正常录入仍至少一条”分别给出正反测试证据，不能用同一断言混证。
- 若实现与本清单或现行文档冲突，停止并上报，不自行改口径。

## 八、交付证据回填（2026-11-24，WorkBuddy）

- 最终 head：`cfb031e`。本轮提交链：`d3b7429` docs(phase4) → `d780d8b` fix(backup) → `97d2ed5` feat(me) → `e177cc5` style(core-data) → `320ab56` fix(core-data) → `cfb031e` test(phase4)；均带 scope 与 `Generated-by: WorkBuddy`，未暂存 Qoder 既有脏文件。
- Android 门禁：`cd app-android && ./gradlew :app:assembleDebug test ktlintCheck detekt :app:lintDebug --max-workers=1` BUILD SUCCESSFUL、exit 0（9m20s），日志 `.workbuddy/evidence/phase4-diff-full-gate-2026-11-24.log`。中途两次红灯均来自 backend `PackVersion.kt` 静态违规，已由 `e177cc5` / `320ab56` 修复后转绿。
- 单测：debug 561 + release 561 = 1122 项，failed 0、error 0、skipped 0。
- 专项正反证据（`d780d8b`）：空 `idiomIds` 备份通过校验并解码为 `[]`；错题与录入题成语引用全失效后本体保留、关联表零行、四类孤儿计数正确；正常录入零成语仍被拒绝且不写库、不写文件，未用同一断言混证。
- 快照：36 张差集（新增 30：P15 10 / P16 10 / P22 2 / P23 6 / P08 2；更新 6：P15 / P22 / P23 主态因文案变更）；P14 两张与 P08 / P09 / P10 既有基线未动。38 张逐图审阅全达标，TEST mock 3 成语 / 2 真题与 BUILTIN 真实 59 / 11 分离；结论 `.workbuddy/evidence/phase4-diff-snapshot-review-2026-11-24.md`。
- verify：双模块 BUILD SUCCESSFUL、无 Record mode；feature-me unchanged 38 / feature-mistake unchanged 48，added / changed 0；record 残留 34 张已清理、无新增残留 PNG。本轮均单帧 capture，无同名多帧、未产生 `.1` 后缀，保留 `.1` 口径不变。日志 `.workbuddy/evidence/phase4-diff-snapshot-verify-2026-11-24.log`。
- 用户拍板：P23 失败态「行内红字 + 红填充确认按钮」两处红保留现状，仅在证据注明偏离一处红契约；P08 深色页头浅绿字配浅灰底对比偏弱保持现状，视为既有基线同款，不改令牌、不补快照。
- 未覆盖：真机实测按用户要求不做；P16 独立 mock 高保真屏、P16 退路条数 2–3 条、P15 是否另加导出按钮仍待拍板，不宣称完成。
