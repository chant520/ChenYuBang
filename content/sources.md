# v1.0.0 题库来源台账

- 定稿日期：2026-11-17。
- 实际入包：59 条成语、11 道真题；国考 5 道、省考 5 道（河南 4、贵州 1）、事业编 1 道；年份 2024–2025，教师编 0 道。
- 包：[dist/pack-v1.0.0.zip](dist/pack-v1.0.0.zip)。合并摘要：`f0e98e97a47ea92945f042fa5f0d8439ab17a4fddca10941b14089f9203c7fc0`。

## 逐条可追溯入口

| 内容 | 来源记录 | 与包内记录的对应方式 |
| --- | --- | --- |
| 59 条成语 | [staging/idioms.raw.json](staging/idioms.raw.json) 每条的 sources 数组（实际抓取 URL、标题、依据与冲突 note） | 按 word 对应 [idioms/idioms.json](idioms/idioms.json) 的稳定 UUID v4 |
| 11 道真题 | [staging/examples.sources.md](staging/examples.sources.md) 的逐题来源及答案佐证；[staging/examples.raw.json](staging/examples.raw.json) 的 source | 按 stem 对应 [examples/examples.json](examples/examples.json) 的稳定 UUID v4；原始台账序号不随 UUID 排序变化 |
| 1 条剔除与字段冲突 | [staging/idioms.gaps.md](staging/idioms.gaps.md) | 原始候选 60 条，首发定稿 [staging/idioms.final-list.json](staging/idioms.final-list.json) 59 条 |

来源元数据保存在上述 staging 文件，不混入交换 schema；包内只含 idioms.json、examples.json、manifest.json。不复制原始台账，以免来源修订产生多个事实源。

## 收录下限与局限

- 释义和出处均必填；59 条出处全部非空。辨析、近义、反义无据可空，不生成虚构字段。
- 原题题干、选项、答案与解析不因词条缺口改写。Q10 答案 D「盲从盲信」缺可溯源词典字段，不建成语详情；该题关联偏听偏信、亦步亦趋、人云亦云三条。本包不承诺覆盖全部正确答案词条。
- 5 条未获直接褒贬标签的词条，其色彩判定依据及冲突在 sources.note 与缺口报告中列明，不冒充词典原始标注。
- frequency 是本包真题引用题次截断为 1–5 的派生值，不代表全国考试频率或词典提供的分级。
- 仅解析出现的「墨守成规」不收；题干出现的「畅所欲言」收录。
- 首发量级、年份及省份偏离最初采集目标的部分均已按实际值定稿，未用模拟题或每日一练凑数。

## 重建与校验

```bash
python tools/transform_content.py
python tools/ingest_content.py --incoming content/source --pack-version v1.0.0
python tools/build_pack.py v1.0.0
```

转换从 content/source 已有文件按 word / stem 复用 UUID；增量构建不得丢弃这些 UUID 映射。已验证二次转换字节不变、UUID v4 全局唯一、交叉引用、ZIP CRC 和包内合并摘要一致。
