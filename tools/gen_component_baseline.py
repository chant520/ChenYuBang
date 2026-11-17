#!/usr/bin/env python3
"""生成构件状态基线板（14 构件 × 4 状态 × 2 模式 = 112 格），并切成 14 张单构件图。

样式全部由 shared/tokens/tokens.json 驱动，不另写色值。
产物：
  docs/design/构件状态基线-emerald-v2.html   （可人工看的总板）
  docs/design/baseline/components/<构件>.png （每构件 830×384，含浅/深两模式各 4 态）
  docs/design/baseline/components/manifest.json

用法：python tools/gen_component_baseline.py
"""
import json
import os
import subprocess
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TOKENS = json.loads((ROOT / "shared" / "tokens" / "tokens.json").read_text(encoding="utf-8"))
DESIGN = ROOT / "docs" / "design"
OUT = DESIGN / "baseline" / "components"
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

ROW_H = 96
TOP = 320
CELL_W = 330


def hex_vars(table):
    return "\n".join("    --%s: %s;" % (k, v) for k, v in table.items())


def stylesheet():
    light = hex_vars(TOKENS["color"]["light"])
    dark = hex_vars(TOKENS["color"]["dark"])
    return """
  * { box-sizing: border-box; }
  body { margin:0; background:#e8eaec; color:#1a1a1a;
         font-family:"PingFang SC","Microsoft YaHei",system-ui,sans-serif;
         -webkit-font-smoothing:antialiased; }
  .lt { %s }
  .dk { %s }
  .cell { position:absolute; width:%dpx; height:88px; padding:10px 12px; border-radius:2px;
          background:var(--page); color:var(--text); overflow:hidden; }
  .celltitle { position:absolute; width:112px; font-size:11px; line-height:1.5; color:#3c4147; }
  .celltitle b { display:block; font-size:11.5px; color:#22262a; }
  .btn { display:block; width:100%%; padding:13px 0; text-align:center; border-radius:6px;
         font-size:14px; font-weight:700; background:var(--btnbg); color:var(--btnfg);
         border:1px solid var(--btnline); }
  .btn.pressed { filter:brightness(.9); }
  .btn.loading { display:flex; align-items:center; justify-content:center; gap:9px; }
  .spinner { width:14px; height:14px; border:2px solid currentColor; border-right-color:transparent;
             border-radius:50%%; }
  .disbtn { display:block; width:100%%; padding:12px 0; text-align:center; border-radius:6px;
            font-size:13.5px; font-weight:700; background:var(--disbg); color:var(--disfg);
            border:1px solid var(--disline); }
  .dangerbtn { display:block; width:100%%; padding:12px 0; text-align:center; border-radius:6px;
               font-size:13.5px; font-weight:700; background:var(--errfill); color:var(--errfillfg);
               border:1px solid var(--errfill); }
  .dangerout { display:block; width:100%%; padding:12px 0; text-align:center; border-radius:6px;
               font-size:13.5px; font-weight:700; color:var(--errfg); background:transparent;
               border:1px solid var(--errline); }
  .ghostbtn { display:block; width:100%%; padding:10px 0; text-align:center; font-size:11.5px;
              border-radius:5px; border:1px solid var(--line); color:var(--text2); }
  .topbtn { display:inline-flex; align-items:center; gap:6px; padding:7px 11px; border-radius:5px;
            background:var(--btnbg); color:var(--btnfg); font-size:11.5px; font-weight:700; }
  .tag { display:inline-block; padding:4px 9px; border-radius:4px; font-size:11px;
         background:var(--tintbg); color:var(--tintfg); border:1px solid var(--tintline); }
  .tag.ghost { background:transparent; color:var(--text3); border:1px dashed var(--line); }
  .tag.on { background:var(--accent); color:var(--segonfg); border-color:var(--accent); }
  .distag { display:inline-block; padding:4px 9px; border-radius:4px; font-size:11px;
            background:var(--disbg); color:var(--disfg); border:1px solid var(--disline); }
  .badgewarn { display:inline-block; padding:2px 6px; border-radius:3px; font-size:10px;
               background:var(--warnbg); color:var(--warnfg); border:1px solid var(--warnline); }
  .badgerr { display:inline-block; padding:2px 6px; border-radius:3px; font-size:10px;
             background:var(--errbg); color:var(--errfg); border:1px solid var(--errline); }
  .badgeinfo { display:inline-block; padding:2px 6px; border-radius:3px; font-size:10px;
               background:var(--tintbg); color:var(--tintfg); border:1px solid var(--tintline); }
  .bar { display:flex; align-items:center; gap:8px; padding:9px 11px; border-radius:5px;
         font-size:11.5px; line-height:1.5; }
  .bar.info { background:var(--tintbg); color:var(--tintfg); border:1px solid var(--tintline); }
  .bar.warn { background:var(--warnbg); color:var(--warnfg); border:1px solid var(--warnline); }
  .bar.err { background:var(--errbg); color:var(--errfg); border:1px solid var(--errline); }
  .bar .act { margin-left:auto; font-weight:700; text-decoration:underline; }
  .editinput { width:100%%; padding:10px 12px; border-radius:5px; font-size:13px;
               background:var(--inputbg); color:var(--text); border:1px solid var(--line); }
  .editinput.focus { border-color:var(--accent); }
  .editinput.err { border-color:var(--errline); color:var(--text); }
  .disinput { width:100%%; padding:10px 12px; border-radius:5px; font-size:13px;
              background:var(--disbg); color:var(--disfg); border:1px solid var(--disline); }
  .imgph { display:flex; align-items:center; justify-content:center; height:64px;
           border-radius:4px; font-size:11.5px; background:var(--phbg); color:var(--text3);
           border:1px dashed var(--phline); }
  .imgph.warn { background:var(--warnbg); color:var(--warnfg); border-color:var(--warnline); }
  .skel { height:64px; border-radius:4px; background:var(--skel); border:1px solid var(--line2); }
  .cut { margin-top:6px; font-size:10px; letter-spacing:.06em; color:var(--text3);
         border-top:1px dashed var(--cut); padding-top:5px; }
  .lrow { display:flex; align-items:center; justify-content:space-between; padding:9px 11px;
          border-radius:5px; background:var(--surface); border:1px solid var(--line); }
  .lrow.on { border-color:var(--accent); background:var(--tintbg); }
  .lrow.dis { background:var(--disbg); color:var(--disfg); border-color:var(--disline); }
  .lmain { font-size:13px; font-weight:700; color:inherit; }
  .lmeta { font-size:10.5px; color:var(--text3); margin-left:6px; font-weight:400; }
  .seg { display:flex; border-radius:5px; overflow:hidden; border:1px solid var(--line); }
  .seg span { flex:1; padding:8px 0; text-align:center; font-size:11.5px;
              background:var(--seg); color:var(--text2); }
  .seg .on { background:var(--segon); color:var(--segonfg); font-weight:700; }
  .seg span.dis { color:var(--disfg); }
  .chkrow { display:flex; align-items:center; gap:8px; font-size:12px; color:var(--text); }
  .chk { width:16px; height:16px; border-radius:3px; border:1px solid var(--line);
         background:var(--surface); display:inline-flex; align-items:center; justify-content:center; }
  .chk.on { background:var(--accent); border-color:var(--accent); }
  .chk.on svg { width:11px; height:11px; stroke:var(--chkfg); stroke-width:2.2; fill:none; }
  .chk.dis { background:var(--disbg); border-color:var(--disline); }
  .fabcell { display:flex; align-items:center; gap:10px; }
  .fab { width:52px; height:52px; border-radius:50%%; background:var(--fab); color:var(--fabicon);
         display:flex; align-items:center; justify-content:center; font-size:24px; font-weight:700;
         box-shadow:0 4px 10px rgba(0,0,0,.18); }
  .fab.dis { background:var(--disbg); color:var(--disfg); box-shadow:none; }
  """ % (light, dark, CELL_W)


def cell(mode, body):
    return '<div class="cell %s">%s</div>' % (mode, body)


CHECK = '<svg viewBox="0 0 24 24"><path d="M5 12l4.5 4.5L19 7"/></svg>'

COMPONENTS = [
    ("主按钮", [
        ('默认', '<div class="btn">保存到错题</div>'),
        ('按下', '<div class="btn pressed">保存到错题</div>'),
        ('禁用', '<div class="disbtn">保存到错题</div>'),
        ('加载', '<div class="btn loading"><span class="spinner"></span>正在识别…</div>'),
    ]),
    ("危险按钮（填充）", [
        ('默认', '<div class="dangerbtn">清空全部数据</div>'),
        ('按下', '<div class="dangerbtn pressed">清空全部数据</div>'),
        ('禁用', '<div class="disbtn">清空全部数据</div>'),
        ('加载', '<div class="dangerbtn loading"><span class="spinner"></span>正在清空…</div>'),
    ]),
    ("危险按钮（描边）", [
        ('默认', '<div class="dangerout">删除这道错题</div>'),
        ('按下', '<div class="dangerout pressed">删除这道错题</div>'),
        ('禁用', '<div class="disbtn">删除这道错题</div>'),
        ('加载', '<div class="dangerout loading"><span class="spinner"></span>正在删除…</div>'),
    ]),
    ("幽灵按钮", [
        ('默认', '<div class="ghostbtn">收藏（预留）</div>'),
        ('按下', '<div class="ghostbtn pressed">收藏（预留）</div>'),
        ('禁用', '<div class="disbtn">收藏（预留）</div>'),
        ('加载', '<div class="ghostbtn"><span class="spinner"></span> 收藏中…</div>'),
    ]),
    ("顶部按钮", [
        ('默认', '<div class="topbtn">+ 拍照录题</div>'),
        ('按下', '<div class="topbtn pressed">+ 拍照录题</div>'),
        ('禁用', '<div class="topbtn" style="background:var(--disbg);color:var(--disfg)">+ 拍照录题</div>'),
        ('加载', '<div class="topbtn"><span class="spinner"></span> 打开相机</div>'),
    ]),
    ("标签", [
        ('默认', '<span class="tag">搭配不当</span>'),
        ('幽灵', '<span class="tag ghost">收藏（预留）</span>'),
        ('选中', '<span class="tag on">褒义</span>'),
        ('禁用', '<span class="distag">教师编</span>'),
    ]),
    ("徽标", [
        ('信息', '<span class="badgeinfo">合并</span>'),
        ('警示橙', '<span class="badgewarn">覆盖</span>'),
        ('错误红', '<span class="badgerr">损坏</span>'),
        ('中性', '<span class="distag">已停用</span>'),
    ]),
    ("提示条", [
        ('信息（中性）', '<div class="bar info">识别到 5 行，请核对</div>'),
        ('可恢复（橙）', '<div class="bar warn">省考还没选省份，先选一个再筛题</div>'),
        ('硬错误（红）', '<div class="bar err">备份包损坏，无法导入</div>'),
        ('橙 + 操作', '<div class="bar warn">识别失败，可重试<span class="act">重试</span></div>'),
    ]),
    ("输入框", [
        ('默认', '<input class="editinput" value="按部就班">'),
        ('聚焦', '<input class="editinput focus" value="按部就班">'),
        ('错误', '<input class="editinput err" value="">'),
        ('禁用', '<input class="disinput" value="不可编辑">'),
    ]),
    ("图片占位", [
        ('空态', '<div class="imgph">还没有照片</div>'),
        ('原图丢失（橙）', '<div class="imgph warn">原图已不在</div>'),
        ('骨架屏', '<div class="skel"></div>'),
        ('长页截断', '<div class="imgph">继续下滑</div><div class="cut">长页 · 已截断</div>'),
    ]),
    ("列表行", [
        ('默认', '<div class="lrow"><span class="lmain">按部就班</span><span class="lmeta">贬义 · 高频</span></div>'),
        ('选中', '<div class="lrow on"><span class="lmain">别出心裁</span><span class="lmeta">褒义 · 高频</span></div>'),
        ('禁用', '<div class="lrow dis"><span class="lmain">墨守成规</span><span class="lmeta">已停用</span></div>'),
        ('带徽标', '<div class="lrow"><span class="lmain">墨守成规 <span class="badgewarn">待复习</span></span></div>'),
    ]),
    ("分段控件", [
        ('默认', '<div class="seg"><span class="on">国考</span><span>省考</span></div>'),
        ('第二段选中', '<div class="seg"><span>国考</span><span class="on">省考</span></div>'),
        ('禁用', '<div class="seg"><span class="on">国考</span><span class="dis">事业编</span></div>'),
        ('三段', '<div class="seg"><span class="on">全部</span><span>待复习</span><span>已掌握</span></div>'),
    ]),
    ("勾选行", [
        ('勾选', '<div class="chkrow"><span class="chk on">%s</span><span>国考</span></div>' % CHECK),
        ('未勾选', '<div class="chkrow"><span class="chk"></span><span>省考</span></div>'),
        ('禁用', '<div class="chkrow" style="color:var(--disfg)"><span class="chk dis"></span><span>教师编</span></div>'),
        ('带警示', '<div class="chkrow"><span class="chk on">%s</span><span>省考</span><span class="badgewarn">未选省份</span></div>' % CHECK),
    ]),
    ("悬浮按钮", [
        ('默认', '<div class="fabcell"><div class="fab">+</div></div>'),
        ('按下', '<div class="fabcell"><div class="fab pressed" style="filter:brightness(.9)">+</div></div>'),
        ('禁用', '<div class="fabcell"><div class="fab dis">+</div></div>'),
        ('带徽标', '<div class="fabcell"><div class="fab">+</div><span class="badgewarn">待复习 3</span></div>'),
    ]),
]


def build_html():
    rows = []
    y = TOP
    positions = []
    for name, states in COMPONENTS:
        start = y
        for label, body in states:
            rows.append('<div class="celltitle" style="left:20px;top:%dpx"><b>%s</b>%s</div>' % (y + 12, name, label))
            rows.append('<div style="position:absolute;left:150px;top:%dpx">%s</div>' % (y + 4, cell("lt", body)))
            rows.append('<div style="position:absolute;left:490px;top:%dpx">%s</div>' % (y + 4, cell("dk", body)))
            y += ROW_H
        positions.append({"name": name, "y0": start, "y1": y})

    html = """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>构件状态基线 · emerald v2</title>
<style>%s</style></head>
<body>
<div style="position:absolute;left:20px;top:24px;width:790px">
  <div style="border-bottom:3px solid #22262a;padding-bottom:14px">
    <h1 style="margin:0 0 10px;font-size:23px">构件状态基线 · emerald v2</h1>
    <p style="margin:0;font-size:12.5px;line-height:1.75;color:#3c4147">
      14 构件 × 4 状态 × 2 模式 = 112 格。左列浅色、右列深色，色值全部取自
      shared/tokens/tokens.json。本页是视觉回归基线，不是新设计稿；改设计必须先改 tokens.json 再重生成。
    </p>
  </div>
</div>
%s
</body></html>""" % (stylesheet(), "\n".join(rows))
    return html, positions


def main():
    html, positions = build_html()
    path = DESIGN / "构件状态基线-emerald-v2.html"
    path.write_text(html, encoding="utf-8", newline="\n")
    print("html %d bytes -> %s" % (len(html), path.name))

    png = Path(os.environ["TEMP"]) / "components_board.png"
    if png.exists():
        png.unlink()
    total_h = TOP + sum(len(s) for _, s in COMPONENTS) * ROW_H + 40
    subprocess.run(
        [EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars",
         "--force-device-scale-factor=1", "--screenshot=" + str(png),
         "--window-size=900,%d" % total_h, "file:///" + str(path).replace("\\", "/")],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    for _ in range(120):
        if png.exists() and png.stat().st_size > 0:
            break
        time.sleep(0.5)
    im = Image.open(png).convert("RGB")
    print("render", im.size)

    OUT.mkdir(parents=True, exist_ok=True)
    manifest = {"board": str(path.relative_to(ROOT)).replace("\\", "/"), "count": len(positions), "components": []}
    for p in positions:
        crop = im.crop((0, p["y0"] - 6, 830, p["y1"] - 6))
        out = OUT / ("%s.png" % p["name"])
        crop.save(out)
        manifest["components"].append({"name": p["name"], "file": out.name,
                                       "size": [crop.size[0], crop.size[1]]})
        print("  %s %dx%d" % (out.name, crop.size[0], crop.size[1]))
    (OUT / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("components: %d 张 -> %s" % (len(positions), OUT))


if __name__ == "__main__":
    main()
