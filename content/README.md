# content · 题库源数据与素材入库

> 本目录只存放**由内容侧提供的真实素材**与入库脚本的产出。脚本本身**不生成任何真实成语、真题或卷面照片，也不伪造 OCR 识别结果**——它只负责扫描、编号登记、搬运真实素材、校验与打包。
> 出题与供图由内容侧完成；OCR 识别由人工或端侧引擎填入。

## 1. 素材放哪

统一投放到 `shared/fixtures/ocr/incoming/`（可用 `ingest_content.py --incoming` 改）：

| 素材 | 文件名 | 格式 |
| --- | --- | --- |
| 真实卷面照片 | 任意名，扩展名 `.jpg` / `.jpeg` / `.png` / `.heic`（大小写不敏感） | 真实照片，短边建议 ≥1080 |
| 成语源数据 | 固定 `idioms.source.json` | JSON 数组，字段见 §3 |
| 真题源数据 | 固定 `examples.source.json` | JSON 数组，字段见 §3 |

照片只在本机，`results.json` / 登记表进 `shared/fixtures/ocr/`，均不进题库包。

## 2. 照片如何编号

- `ingest_content.py` 扫描 `incoming/` 下所有照片，按**文件名排序**依次占用 `P01…P20`（沿用 `样张登记表.csv` 现有编号，不重排已有编号）。
- 把每张照片的相对路径写入登记表「来源」列；其余维度（光线/角度/版式/干扰等）**留给人工填写**。
- 生成 `results.json` 模板：每条 `{id,stem,A,B,C,D,durationMs}`，识别字段全为 `null`，**不含虚构识别结果**，待人手工填或端侧 OCR 引擎填写。
- 超过 20 张、无照片、扩展名不认识 → 中文提示 + 非零退出码，不静默跳过。

## 3. 真题源数据格式

两个 JSON 数组，字段**严格对齐** `shared/schema/idioms.schema.json` 与 `examples.schema.json`（`additionalProperties:false`，多一个字段都会校验失败）。

`idioms.source.json`（数组，每条）：

```json
{
  "id": "<uuid v4>", "word": "<2-8个汉字>", "pinyin": "<全拼 空格分隔>", "initials": "<拼音首字母>",
  "sense": "<释义，≥4字>", "origin": "<出处，≥4字，首发内容必填>",
  "color": "褒义|贬义|中性", "frequency": 1,
  "scopes": ["国考|省考|事业编|教师编"],
  "pitfalls": [{"kind": "望文生义|对象误用|褒贬误用|语义重复|搭配不当", "note": "<说明>"}],
  "synonyms": ["<近义成语>"], "antonyms": ["<反义成语>"]
}
```

`examples.source.json`（数组，每条）：

```json
{
  "id": "<uuid v4>", "stem": "<题干，≥6字>",
  "options": {"A": "<选项>", "B": "<选项>", "C": "<选项>", "D": "<选项>"},
  "answer": "A|B|C|D", "explain": "<解析，≥4字>",
  "examType": "国考|省考|事业编|教师编", "year": 2023, "region": "<地区，≥2字>",
  "questionType": "言语理解|逻辑填空|语句表达|常识判断",
  "idiomIds": ["<引用的成语 id，需存在于 idioms 源数据>"]
}
```

上面仅为**字段模板**，尖括号内容是格式占位、不是真实内容。`id` 必须符合 UUID v4 且全局唯一；`idiomIds` 引用不存在的成语会被校验拦下。

## 4. 运行命令（在项目根目录）

```bash
# 1) 入库：扫描真实素材 → 编号登记 → 生成 results.json 模板 → 搬运题库源数据 → 校验 → 打包
python tools/ingest_content.py

# 2) 人工或端侧 OCR 引擎填写 shared/fixtures/ocr/results.json 的识别字段（脚本绝不代填）

# 3) 算通过率（阈值由产品定，不传 --target 则只报数）
python tools/ocr_passrate.py shared/fixtures/ocr/results.json --target 0.9

# 4) 把通过率数字回填到产品验收第 9 条与 docs/开发规范 §11.3 第 9 行（人工）

# 单独重跑校验 / 打包：
python tools/validate_schema.py --data content/idioms/idioms.json content/examples/examples.json
python tools/build_pack.py v1.0.0
```

`ingest_content.py` 可选参数：`--incoming`（素材目录）、`--ocr-dir`（登记表/results 输出目录）、`--content-dir`（题库源数据输出目录）、`--pack-version`（包版本）、`--skip-pack`（只入库不打包）。

## 5. 诚实口径

- 真实卷面照片、真实成语与真题**必须由内容侧提供**；脚本不编数字、不造题、不伪造识别结果。
- 验收第 9 条目前是「待回填」口径，通过率数字来自真机/代理实测的 `results.json`，由人回填，脚本只负责算数。
