#!/usr/bin/env python3
"""OCR 样张通过率计算（口径见 docs/OCR样张实测方案.md）。

输入：results.json，形如
  [{"id":"P01","stem":"…","A":"…","B":"…","C":"…","D":"…","durationMs":1830}, …]
  （缺项写 null，不要写空串）

输出：逐张是否「题干 + 四选项」齐全、完整率、耗时 P50/P90；
     传了 --target 才给 PASS/FAIL，不传就只报数（阈值由产品定，脚本不替产品拍板）。

用法：
  python tools/ocr_passrate.py shared/fixtures/ocr/results.json
  python tools/ocr_passrate.py shared/fixtures/ocr/results.json --target 0.9
"""
import json
import statistics
import sys
from pathlib import Path

FIELDS = ["stem", "A", "B", "C", "D"]


def pct(values, p):
    if not values:
        return 0
    values = sorted(values)
    idx = min(len(values) - 1, int(round((len(values) - 1) * p)))
    return values[idx]


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    target = None
    if "--target" in argv:
        i = argv.index("--target")
        target = float(argv[i + 1])
        argv = argv[:i] + argv[i + 2:]

    path = Path(argv[0])
    rows = json.loads(path.read_text(encoding="utf-8"))
    if not rows:
        print("FAIL: 结果为空")
        return 1

    complete, details = 0, []
    for r in rows:
        missing = [f for f in FIELDS if not (r.get(f) or "").strip()]
        ok = not missing
        complete += 1 if ok else 0
        details.append((r.get("id", "?"), ok, missing))

    rate = complete / len(rows)
    durations = [r["durationMs"] for r in rows if isinstance(r.get("durationMs"), int)]

    for (rid, ok, missing) in details:
        print("%-4s %s%s" % (rid, "OK" if ok else "MISS", "" if ok else "  缺: " + "/".join(missing)))
    print("")
    print("样张 %d 张，完整 %d 张，通过率 %.1f%%" % (len(rows), complete, rate * 100))
    if durations:
        print("耗时 P50 %dms / P90 %dms" % (pct(durations, 0.5), pct(durations, 0.9)))
    if target is not None:
        print("判据 target=%.0f%% → %s" % (target * 100, "PASS" if rate >= target else "FAIL"))
        return 0 if rate >= target else 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
