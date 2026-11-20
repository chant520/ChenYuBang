# ChenYuBang 项目约定（成语记忆 App · 考公考编）

> 会话工作目录即项目根。本文只写本项目的东西；跨项目通用约定见全局 AGENTS.md（DSH 用户全局链自动注入）。角色定义与完整规范以 `.codebuddy/AGENT-SPEC.md` 与 `.codebuddy/agents/` 为准。

## 事实源与裁决

- 文档四件套是唯一事实源，按裁决顺序引用：产品功能规划 v1.6 > 信息架构 v1.4 > 设计规范 v1.3 > 高保真稿（docs/design）；执行细节以开发规范 v1.6 为准，排期以开发排期 v1.2 为准。
- 升版必须新建版本号文件，不覆盖旧版；旧版文件经用户拍板定期从工作区清理（最近一轮 2026-11-20，删除 11 份），历史内容靠 git 追溯，正文中的历史沿革描述保留。
- 发现任何两份文档互相矛盾：停下上报用户，不自行选一个执行。
- 先改文档再改代码；偏离规范的实现必须先升规范版本。

## 关键硬口径（改动前必读，均已拍板）

- 省份口径（推翻决策 I）：省考勾选时省份默认全选、省考内容正常显示；省份仅为可增减的筛选维度，无「未选省份」异常态与橙警示（E04 已消解）。
- 拍照链路：拍照是「录入用户自己的题库」，录入的题不必然是错题；修正页可选标「是否做错」，标错才生成错题记录；P08 只显示标错题，P09 展示该成语下全部录入题。
- 联网仅两处（题库获取、备份另存网盘）；拍照 OCR 全链路零联网；不建账号、学习数据不上传。
- 数据模型 11 张表；导入只替换内容表、绝不触碰用户表（含 `user_example`）；v2 迁移方案只写在开发规范 §4.5，`migrations.json` 仍为 v1/10 表口径。
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
