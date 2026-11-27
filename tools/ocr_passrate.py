#!/usr/bin/env python3
"""OCR 样张通过率计算（口径见 docs/OCR样张实测方案.md）。

输入：results.json，形如
  {"provenance":{"engine":"…","device":"…","date":"…","source":"…"},
   "results":[{"id":"P01","stem":"…","A":"…","B":"…","C":"…","D":"…","durationMs":1830}, …]}
  缺项写 null，不要写空串；也兼容旧的纯数组输入（视为无 provenance）。
  无 provenance 时打印「中间产物，非验收数」，不写达标。

输出：逐张是否「题干 + 四选项」齐全、完整率、耗时 P50/P90 及参与统计的耗时条数；
     传了 --target 才给 PASS/FAIL，不传就只报数（阈值由产品定，脚本不替产品拍板）。

用法：
  python tools/ocr_passrate.py shared/fixtures/ocr/results.json
  python tools/ocr_passrate.py shared/fixtures/ocr/results.json --target 0.9
"""
import json
import sys
from pathlib import Path

FIELDS = ["stem", "A", "B", "C", "D"]
PROVENANCE_FIELDS = ["engine", "device", "date", "source"]


def pct(values, p):
    if not values:
        return 0
    values = sorted(values)
    idx = min(len(values) - 1, int(round((len(values) - 1) * p)))
    return values[idx]


def unpack(data):
    if isinstance(data, dict):
        return data.get("results"), data.get("provenance")
    if isinstance(data, list):
        return data, None
    return None, None


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    target = None
    if "--target" in argv:
        i = argv.index("--target")
        if i + 1 >= len(argv):
            print("FAIL: --target 缺数值")
            return 2
        target = float(argv[i + 1])
        argv = argv[:i] + argv[i + 2:]

    path = Path(argv[0])
    rows, provenance = unpack(json.loads(path.read_text(encoding="utf-8")))
    if not isinstance(rows, list) or not rows:
        print("FAIL: 结果为空")
        return 1

    if not isinstance(provenance, dict) or any(
        not isinstance(provenance.get(key), str) or not provenance[key].strip()
        for key in PROVENANCE_FIELDS
    ):
        print("中间产物，非验收数")
    else:
        print("来源 %s / %s / %s" % (provenance["engine"], provenance["device"], provenance["date"]))
        print("出处 %s" % provenance["source"])

    complete, details, durations = 0, [], []
    for index, row in enumerate(rows):
        if not isinstance(row, dict):
            print("FAIL: 第 %d 条不是对象" % (index + 1))
            return 1
        for field in ["id"] + FIELDS:
            value = row.get(field)
            if value is not None and not isinstance(value, str):
                print("FAIL: 第 %d 条 %s 类型错误: %s" % (index + 1, field, type(value).__name__))
                return 1
        missing = [field for field in FIELDS if not (row.get(field) or "").strip()]
        ok = not missing
        complete += 1 if ok else 0
        details.append((row.get("id") or "?", ok, missing))

        duration = row.get("durationMs")
        if isinstance(duration, bool):
            print("FAIL: 第 %d 条 durationMs 类型错误: bool" % (index + 1))
            return 1
        if isinstance(duration, (int, float)):
            durations.append(duration)
        elif duration is not None:
            print("FAIL: 第 %d 条 durationMs 类型错误: %s" % (index + 1, type(duration).__name__))
            return 1

    rate = complete / len(rows)
    for (rid, ok, missing) in details:
        print("%-4s %s%s" % (rid, "OK" if ok else "MISS", "" if ok else "  缺: " + "/".join(missing)))
    print("")
    print("样张 %d 张，完整 %d 张，通过率 %.1f%%" % (len(rows), complete, rate * 100))
    print("参与统计耗时 %d 条" % len(durations))
    if durations:
        print("耗时 P50 %dms / P90 %dms" % (pct(durations, 0.5), pct(durations, 0.9)))
    if target is not None:
        print("判据 target=%.0f%% → %s" % (target * 100, "PASS" if rate >= target else "FAIL"))
        return 0 if rate >= target else 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
