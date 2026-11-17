# OCR-DEMO 素材与实测中间产物

用户提供的 20 张真实卷面照片（`1.PNG … 20.PNG`，原始文件，未裁剪、未改动）。

| 路径 | 内容 | 说明 |
| --- | --- | --- |
| `1.PNG … 20.PNG` | 原始样张 | 只读，不要覆盖 |
| `ocr-up/` | 2× 放大 + 灰度 + 对比度 + 锐化后的图 | 模拟「P18 裁切后上采样」，可重生成 |
| `ocr-out/` | 原尺寸直跑的 OCR 原始文本（逐行） | 仅作对照，**原尺寸直跑会丢行** |
| `ocr-boxes/` | 2× 图 OCR 的词 + 坐标 | 版面重建的输入 |
| `results-boxes.json` | 版面重建后的结构化结果（题干 + A/B/C/D） | `tools/ocr_passrate.py` 的输入 |
| `report-boxes.md` | 逐张对照表 | 人工核对用 |
| `样张登记表.csv` | 20 行登记 | 版式/分辨率/是否完整/缺项/耗时已填；来源、光线、角度、干扰四列**未记录**，不许臆测 |

## 引擎说明

本轮只用本机 Windows 自带的 `Windows.Media.Ocr`（`zh-Hans-CN`，离线），**不是** App 的 PaddleOCR / Vision。
数字性质是「代理实测」，验收第 9 条仍标待真机复测。完整方法与限制见 `docs/OCR实测记录.md`。

## 复算

```
python tools/ocr_passrate.py docs/OCR-DEMO/results-boxes.json --target 0.9
```
