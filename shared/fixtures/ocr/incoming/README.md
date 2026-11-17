# incoming · 真实素材投放口

把内容侧提供的**真实**素材放本目录，然后回项目根目录运行 `python tools/ingest_content.py`。

- 卷面照片：`*.jpg / *.jpeg / *.png / *.heic`（真实照片，不要放示意图/占位图）。
- 成语源数据：固定名 `idioms.source.json`（JSON 数组，字段见 `content/README.md` §3）。
- 真题源数据：固定名 `examples.source.json`（同上）。

本目录默认只含 `.gitkeep`；真实素材不入库（入库的成品是 `shared/fixtures/ocr/样张登记表.csv`、`results.json` 与 `content/` 下的题库源数据）。脚本对空目录会给出「没有素材」提示并以非零码退出，不会生成假数据。
