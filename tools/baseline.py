#!/usr/bin/env python3
"""从 v2 高保真稿生成单屏截图基线（视觉回归的「标准答案」）。

做法：
  1. 复制稿子并在 </head> 前注入 html/body 背景 = 品红，手机框自带底色，于是画面里
     「品红以外」的连续矩形就是手机屏；
  2. Edge headless 整页截图；
  3. 按行带 + 列带切出每块 375×812（长页保留实际高度）；
  4. 页码与模式取自稿子里 <p class="vname"> 的文案，与屏的顺序一一对应；
  5. 落盘 docs/design/baseline/<组>/<页码>_<模式>_<状态>.png，并写 manifest.json + index.md。

用法：
  python tools/baseline.py            # 重新生成
  python tools/baseline.py --check    # 只校验现有基线是否完整、是否与 manifest 一致
"""
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DESIGN = ROOT / "docs" / "design"
OUT = DESIGN / "baseline"
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

GROUPS = [
    ("A", "高保真全页-emerald-A-P01-P05-v2.html", "P01–P05 主态"),
    ("B", "高保真全页-emerald-B-P06-P13-v2.html", "P06–P13 主态"),
    ("C", "高保真全页-emerald-C-P14-P23-v2.html", "P14–P23 主态"),
    ("D", "高保真空态与异常态-emerald-D-v2.html", "空态与异常态"),
]

MAGENTA = (255, 0, 255)
MIN_BAND = 700  # 手机屏至少 812 高（长页更高），低于这个的带是文字行


def vnames(html):
    """按出现顺序返回 (code, mode, label)。"""
    out = []
    for m in re.finditer(r'<p class="vname">(.+?)</p>', html, flags=re.S):
        raw = re.sub(r"<em>(.*?)</em>", r"|\1", m.group(1))
        raw = re.sub(r"<[^>]+>", "", raw).replace("&nbsp;", " ").strip()
        parts = raw.split("|")
        label = parts[0].strip()
        code = parts[1].strip() if len(parts) > 1 else ""
        mode = "dark" if "深色" in label else "light"
        out.append((code, mode, label))
    return out


def slug(text):
    s = text.replace("深色模式", "").replace("浅色模式", "")
    s = re.sub(r"[·、，,。:：/\\]+", "_", s)
    s = re.sub(r"\s+", "_", s)
    s = re.sub(r"_+", "_", s).strip("_")
    return s or "screen"


def render(html_path, tag, height=32000):
    src = html_path.read_text(encoding="utf-8")
    src = src.replace("</head>", "<style>html,body{background:#FF00FF !important;}</style></head>")
    tmp = Path(os.environ["TEMP"]) / ("baseline_%s.html" % tag)
    png = Path(os.environ["TEMP"]) / ("baseline_%s.png" % tag)
    if png.exists():
        png.unlink()
    tmp.write_text(src, encoding="utf-8")
    subprocess.run(
        [EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars",
         "--force-device-scale-factor=1", "--screenshot=" + str(png),
         "--window-size=900,%d" % height, "file:///" + str(tmp).replace("\\", "/")],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    for _ in range(120):
        if png.exists() and png.stat().st_size > 0:
            break
        time.sleep(0.5)
    return Image.open(png).convert("RGB")


def detect(im):
    """返回 [(x0, y0, x1, y1)]，按阅读顺序。"""
    px = im.load()
    w, h = im.size
    samples = [40, 250, 400, 460, 700, 805]
    bands, inband, start = [], False, 0
    for y in range(h):
        is_phone = all(px[x, y] != MAGENTA for x in samples)
        if is_phone and not inband:
            inband, start = True, y
        elif not is_phone and inband:
            inband = False
            if y - start >= MIN_BAND:
                bands.append((start, y))
    if inband and h - start >= MIN_BAND:
        bands.append((start, h))

    rects = []
    for (y0, y1) in bands:
        ymid = (y0 + y1) // 2
        runs, x = [], 0
        while x < w:
            if px[x, ymid] != MAGENTA:
                x0 = x
                while x < w and px[x, ymid] != MAGENTA:
                    x += 1
                runs.append((x0, x))
            else:
                x += 1
        for (x0, x1) in runs:
            if x1 - x0 < 300:
                continue
            rects.append((x0, y0, x1, y1))
    return rects


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build():
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = {"source": "docs/design/*-v2.html", "groups": [], "screens": []}
    total = 0
    for letter, fname, desc in GROUPS:
        html_path = DESIGN / fname
        if not html_path.exists():
            print("MISSING %s" % fname)
            return 1
        html = html_path.read_text(encoding="utf-8")
        labels = vnames(html)
        im = render(html_path, letter)
        rects = detect(im)
        print("%s: 稿内 %d 个 vname / 检出 %d 块屏" % (letter, len(labels), len(rects)))
        if len(rects) != len(labels):
            print("  !! 数量不一致，先人工核对（不猜）")
            return 1
        group_dir = OUT / letter
        group_dir.mkdir(exist_ok=True)
        for (x0, y0, x1, y1), (code, mode, label) in zip(rects, labels):
            crop = im.crop((x0, y0, x1, y1))
            name = "%s_%s_%s.png" % (code or "X", mode, slug(label))
            path = group_dir / name
            crop.save(path)
            manifest["screens"].append({
                "group": letter,
                "file": str(path.relative_to(OUT)).replace("\\", "/"),
                "page": code,
                "mode": mode,
                "label": label,
                "size": [crop.size[0], crop.size[1]],
                "sha256": sha256(path),
            })
            total += 1
        manifest["groups"].append({"group": letter, "source": fname, "desc": desc,
                                   "screens": len(rects)})

    (OUT / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    write_index(manifest)
    print("baseline: %d 屏写入 %s" % (total, OUT))
    return 0


def write_index(manifest):
    lines = [
        "# 截图基线",
        "",
        "来源：`docs/design/` 下四个 `*-v2.html`（当前视觉事实源）。",
        "生成：`python tools/baseline.py`；校验：`python tools/baseline.py --check`。",
        "",
        "| 组 | 源文件 | 说明 | 屏数 |",
        "| --- | --- | --- | --- |",
    ]
    for g in manifest["groups"]:
        lines.append("| %s | `%s` | %s | %d |" % (g["group"], g["source"], g["desc"], g["screens"]))
    lines += ["", "| 组 | 页码 | 模式 | 状态 | 尺寸 | 文件 |", "| --- | --- | --- | --- | --- | --- |"]
    for s in manifest["screens"]:
        lines.append("| %s | %s | %s | %s | %d×%d | `%s` |"
                     % (s["group"], s["page"], s["mode"],
                        s["label"].replace("深色模式 · ", "").replace("浅色模式 · ", ""),
                        s["size"][0], s["size"][1], s["file"]))

    comp_dir = OUT / "components"
    comp_manifest = comp_dir / "manifest.json"
    if comp_manifest.exists():
        comps = json.loads(comp_manifest.read_text(encoding="utf-8"))
        lines += [
            "",
            "## 构件基线",
            "",
            "来源：`docs/design/构件状态基线-emerald-v2.html`（14 构件 × 4 状态 × 2 模式）。",
            "生成：`python tools/gen_component_baseline.py`。每张图左侧浅色、右侧深色，四行对应四个状态。",
            "",
            "| 构件 | 尺寸 | 文件 |",
            "| --- | --- | --- |",
        ]
        for c in comps["components"]:
            lines.append("| %s | %d×%d | `components/%s` |"
                         % (c["name"], c["size"][0], c["size"][1], c["file"]))
    (OUT / "index.md").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def check():
    mf = OUT / "manifest.json"
    if not mf.exists():
        print("baseline missing: 先跑 python tools/baseline.py")
        return 1
    manifest = json.loads(mf.read_text(encoding="utf-8"))
    bad = []
    for s in manifest["screens"]:
        p = OUT / s["file"]
        if not p.exists():
            bad.append("缺文件 %s" % s["file"])
        elif sha256(p) != s["sha256"]:
            bad.append("内容变了 %s" % s["file"])
    if bad:
        print("BASELINE DRIFT (%d)" % len(bad))
        for b in bad:
            print("  - " + b)
        return 1
    print("baseline ok: %d 屏与 manifest 一致（%s）"
          % (len(manifest["screens"]),
             "、".join("%s %d" % (g["group"], g["screens"]) for g in manifest["groups"])))
    return 0


if __name__ == "__main__":
    sys.exit(check() if "--check" in sys.argv[1:] else build())
