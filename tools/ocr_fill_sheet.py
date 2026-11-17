import csv, json, os
from pathlib import Path
from PIL import Image

ROOT = Path(r"D:\ProgramData\WorkBuddy Workspace\ChenYuBang")
DEMO = ROOT / "docs" / "OCR-DEMO"
BOX = DEMO / "ocr-boxes"
RES = json.loads((DEMO / "results-boxes.json").read_text(encoding="utf-8"))

MARK = "ABCD巳匚⊂&8"
layout = {}
for p in sorted(BOX.glob("*.json"), key=lambda x: int(x.stem)):
    data = json.loads(p.read_text(encoding="utf-8-sig"))
    ys = []
    for w in data["words"]:
        t = w["t"].strip()
        if len(t) <= 2 and t[:1] in MARK:
            ys.append(w["y"])
    two_col = any(abs(a - b) < 22 for i, a in enumerate(ys) for b in ys[i + 1:])
    layout[p.stem] = "选项双列" if two_col else "选项单列"

rows = []
for r in RES:
    rid = r["id"]
    img = Image.open(DEMO / ("%s.PNG" % rid))
    rows.append({
        "样张编号": "P%02d" % int(rid),
        "来源": "",
        "光线": "",
        "角度": "",
        "版式": layout.get(rid, ""),
        "干扰": "",
        "分辨率": "%dx%d" % img.size,
        "识别完整": "否" if r["_missing"] else "是",
        "缺了哪项": "/".join(r["_missing"]),
        "耗时ms": r["durationMs"],
        "备注": "文件 %s.PNG" % rid,
    })

with (DEMO / "样张登记表.csv").open("w", encoding="utf-8-sig", newline="") as f:
    w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
    w.writeheader()
    w.writerows(rows)
print("csv rows", len(rows))
for k, v in layout.items():
    print(k, v)
