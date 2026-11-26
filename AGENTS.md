# ChenYuBang 项目约定（成语记忆 App · 考公考编）

> 会话工作目录即项目根。本文只写本项目的东西；跨项目通用约定见全局 AGENTS.md（DSH 用户全局链自动注入）。角色定义与完整规范以 `.codebuddy/AGENT-SPEC.md` 与 `.codebuddy/agents/` 为准。

## 事实源与裁决

- 文档四件套是唯一事实源，按裁决顺序引用：产品功能规划 v1.7 > 信息架构 v1.5 > 设计规范 v1.5 > 高保真稿（docs/design）；执行细节以开发规范 v1.9 为准，排期以开发排期 v1.2 为准。
- 升版必须新建版本号文件，不覆盖旧版；旧版文件经用户拍板定期从工作区清理（最近一轮 2026-11-20，删除 11 份），历史内容靠 git 追溯，正文中的历史沿革描述保留。
- 发现任何两份文档互相矛盾：停下上报用户，不自行选一个执行。
- 先改文档再改代码；偏离规范的实现必须先升规范版本。

## 关键硬口径（改动前必读，均已拍板）

- 省份口径（推翻决策 I）：省考勾选时省份默认全选、省考内容正常显示；省份仅为可增减的筛选维度，无「未选省份」异常态与橙警示（E04 已消解）。
- 拍照链路：拍照是「录入用户自己的题库」，录入的题不必然是错题；修正页可选标「是否做错」，标错才生成错题记录；修正页可勾选一条或多条归属（至少一条），保存后仍可在录入题详情增删；P08 只显示标错题，P09 按 `user_example_idiom` 展示该成语下全部录入题。
- 联网仅两处（题库获取、备份另存网盘）；拍照 OCR 全链路零联网；不建账号、学习数据不上传。
- 数据模型 12 张表（v3 新增 `user_example_idiom` 关联表，一道录入题可归属多个成语）；导入只替换内容表、绝不触碰用户表（含 `user_example` / `user_example_idiom`）；迁移方案以开发规范 v1.9 §4.5 为准，`migrations.json` 已登 v1/v2，v3 随 2.5 登记。
- 拍照入口只在四个主标签页（决策 D）；P10＝录入题详情（标错的含重做态），C7＝录入题详情与重做。
- 二期（进阶）：B6 判分、B7 标记、B4/B5 筛选搜索、独立「我的题库」页，本版不做。
- 诚实口径：OCR 验收第 9 条保留「代理实测 18/20＝90%」注记，不写达标、不编数字。

## 验证门禁（Android 与共享校验在本机跑，iOS 走 macOS CI）

- Android：`cd app-android && ./gradlew :app:assembleDebug test ktlintCheck detekt :app:lintDebug`（JDK17/Gradle 8.9 wrapper）。
- 共享：项目根 `python tools/gen_tokens.py --check`、`python tools/validate_schema.py`、`.venv/Scripts/python tools/baseline.py --check`。
- 快照：改动后先审阅再 record/verify，不覆盖基线图；verify 残留 PNG 要清理。
- 生成文件（Tokens.kt / Tokens.swift）勿手改，改 tokens.json 后重新生成。

## 记录

- 工作日志 append-only 追加到 `.workbuddy/memory/YYYY-MM-DD.md`（本项目已有 2026-11-19 卷）。
- 角色派发、拍板留痕、并行规则遵循 `.codebuddy/AGENT-SPEC.md`。

## Git 产出标识（向前看 · 自 2026-11-20 起）

- 适用范围：以提交 `67eba25`（2026-11-20）为界，仅对其后的提交生效。`67eba25` 及此前所有 2.4 提交一律不追溯、不补标、不 amend。
- 加什么：每条提交在 message 末尾追加一行 trailer，按实际产出者取值——
  - **对方侧（非 qoder）产出：取值＝用户当轮告知的同事名**。开工时指定、切换同事时用户会重新告知；不得沿用上一轮的同事名（2026-11-21 修订：本包 Android 侧同事＝deepseek → `Generated-by: deepseek`）。
  - WorkBuddy 主控或其派发角色（dev-super / designer-ui / product-dog 等）产出，且用户当轮如此指定 → `Generated-by: WorkBuddy`
  - qoder 产出 → `Generated-by: Qoder`
  - 每个 commit 只加一行，取实际执行者；双方协作的提交按主执行方归属。
- 由谁加：执行该次编辑并提交的角色在 commit message 中追加；主控在审查产出环节核验 trailer 存在且取值正确（对方侧取值按当轮告知的同事名判，不写死某个 harness），缺失或错误则要求补齐后再上报用户。
- 与现有风格共存：沿用 Conventional Commits 标题 + 空行 + `- ` bullet 详述；**标题必须带 scope**（如 `fix(P20):`、`docs(agents):`），scope 取改动所属模块/对象，不写裸 `docs:`、裸 `fix:`；trailer 位于全文最末，与最后一条 bullet 空一行，不改标题与正文体例。
- 身份口径：不得通过改 git author / committer（全局或 local）或 .mailmap 来标识——全局身份由双方共用，改 local 会误标对方提交；身份保持现状，产出者一律以 trailer 为准。
- 检索：`git log --format='%(trailers:key=Generated-by)'` 可筛出全部标识提交。
