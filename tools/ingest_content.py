#!/usr/bin/env python3
"""素材入库脚本：把「获取真实素材」的全流程脚本化，但绝不生成虚构内容。

职责（口径见 docs/开发规范-v1.6.md §2.3 / §4.4 / §4.5 / §11.3、content/README.md）：
  1. 扫描 incoming 目录里的真实卷面照片（jpg/jpeg/png/heic），按文件名排序占用 P01…P20，
     把相对路径写入 样张登记表.csv 的「来源」列（其余维度留给人工）；
     并生成 results.json 模板（id + stem/A/B/C/D/durationMs 全 null），绝不代填识别结果。
  2. 读取内容侧提供的 idioms.source.json / examples.source.json，按 id 升序搬运到
     content/idioms/idioms.json 与 content/examples/examples.json。
  3. 调用 tools/validate_schema.py --data 校验；调用 tools/build_pack.py 打包（§4.5 sha256 口径）。
  4. 素材缺失 / 格式不对 → 清晰中文提示 + 非零退出码；不空跑、不静默跳过、不造假数据。

幂等：重跑时按「来源」列复用已分配编号；results.json 已有 id 的填写值不会被覆盖。

用法：
  python tools/ingest_content.py
  python tools/ingest_content.py --incoming shared/fixtures/ocr/incoming --pack-version v1.0.0
"""
import argparse
import csv
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent

PHOTO_EXT = {".jpg", ".jpeg", ".png", ".heic"}
SOURCE_NAMES = {"idioms.source.json", "examples.source.json"}
IGNORE_NAMES = {".gitkeep", "README.md", "README.txt"}
RESULT_FIELDS = ["stem", "A", "B", "C", "D", "durationMs"]
SLOT_PREFIX = "样张编号"
SOURCE_COL = "来源"
MAX_SLOTS = 20


def fail(msg, code=2):
    print("FAIL: " + msg)
    return code


def parse_args(argv):
    ap = argparse.ArgumentParser(description="真实素材入库（不生成虚构内容）")
    ap.add_argument("--incoming", default=str(ROOT / "shared" / "fixtures" / "ocr" / "incoming"),
                    help="真实素材目录")
    ap.add_argument("--ocr-dir", default=str(ROOT / "shared" / "fixtures" / "ocr"),
                    help="登记表与 results.json 输出目录")
    ap.add_argument("--content-dir", default=str(ROOT / "content"), help="题库源数据输出目录")
    ap.add_argument("--pack-version", default="v1.0.0", help="题库包版本，形如 v1.0.0")
    ap.add_argument("--skip-pack", action="store_true", help="只入库与校验，不打包")
    return ap.parse_args(argv)


def scan(incoming):
    """返回 (照片列表, 源数据字典, 未识别文件列表)。"""
    photos, sources, unknown = [], {}, []
    for p in sorted(incoming.iterdir(), key=lambda x: x.name.lower()):
        if p.name in IGNORE_NAMES or p.name.startswith("."):
            continue
        if p.is_dir():
            unknown.append(p.name + "/（子目录）")
            continue
        if p.suffix.lower() in PHOTO_EXT:
            photos.append(p)
        elif p.name in SOURCE_NAMES:
            sources[p.name] = p
        else:
            unknown.append(p.name)
    return photos, sources, unknown


def read_registry(path):
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        fields = list(reader.fieldnames or [])
        rows = list(reader)
    return fields, rows


def slot_of(row, index):
    return (row.get(SLOT_PREFIX) or "").strip() or ("P%02d" % (index + 1))


def assign_slots(photos, incoming, rows):
    """按「来源」列复用已分配编号；否则占用第一个空位。返回 ([(行号, 照片, 相对路径)], 错误)。"""
    source_to_row = {}
    for i, row in enumerate(rows):
        src = (row.get(SOURCE_COL) or "").strip()
        if src:
            source_to_row[src] = i
    assignments, used = [], set()
    for photo in photos:
        rel = photo.relative_to(incoming).as_posix()
        if rel in source_to_row:  # 幂等：同一素材重跑，复用原编号
            idx = source_to_row[rel]
            assignments.append((idx, photo, rel))
            used.add(idx)
            continue
        idx = None
        for i, row in enumerate(rows):
            if i in used:
                continue
            if not (row.get(SOURCE_COL) or "").strip():
                idx = i
                break
        if idx is None:
            return None, "照片数量超过登记表 %d 个编号位（%s）；请清理 incoming 或扩充登记表" % (
                MAX_SLOTS, "P01…P%02d" % MAX_SLOTS)
        assignments.append((idx, photo, rel))
        used.add(idx)
    return assignments, None


def write_registry(path, fields, rows, assignments):
    for idx, _photo, rel in assignments:
        if not (rows[idx].get(SLOT_PREFIX) or "").strip():
            rows[idx][SLOT_PREFIX] = "P%02d" % (idx + 1)
        rows[idx][SOURCE_COL] = rel
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def manage_results(ocr_dir, assignments, rows):
    """生成/更新 results.json 模板：新增 id 补 null 模板，已有 id 的填写值保留。"""
    path = ocr_dir / "results.json"
    existing = {}
    if path.exists():
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            if not isinstance(data, list):
                return fail("results.json 顶层必须是数组，拒绝覆盖", 2)
            for item in data:
                if isinstance(item, dict) and item.get("id"):
                    existing[item["id"]] = item
        except json.JSONDecodeError as e:
            return fail("results.json 不是合法 JSON（%s），拒绝覆盖" % e, 2)

    out = []
    for idx, _photo, _rel in assignments:
        rid = slot_of(rows[idx], idx)
        prev = existing.get(rid, {})
        row = {"id": rid}
        for field in RESULT_FIELDS:
            row[field] = prev.get(field)  # null = 等待人工/OCR 填写
        out.append(row)
    covered = {r["id"] for r in out}
    orphan = [rid for rid in existing if rid not in covered]
    out.extend(existing[rid] for rid in orphan)
    out.sort(key=lambda r: str(r.get("id", "")))

    with path.open("w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
    return (len(covered), len(orphan))


def ingest_sources(sources, content_dir, skip_pack, pack_version):
    """搬运题库源数据 → 校验 → 打包。返回退出码；sources 为空表示本轮无题库素材。"""
    if not sources:
        return 0, False
    missing = [n for n in SOURCE_NAMES if n not in sources]
    if missing:
        return fail("真题源数据需同时提供 idioms.source.json 与 examples.source.json；缺：%s"
                    % "、".join(missing), 2), True

    loaded = {}
    for name, path in sources.items():
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            return fail("%s 不是合法 JSON：%s" % (name, e), 2), True
        if not isinstance(data, list) or not data:
            return fail("%s 必须是非空 JSON 数组" % name, 2), True
        data.sort(key=lambda x: str(x.get("id", "")))  # §4.5 数组按 id 升序
        loaded[name] = data

    targets = {
        "idioms.source.json": content_dir / "idioms" / "idioms.json",
        "examples.source.json": content_dir / "examples" / "examples.json",
    }
    for name, target in targets.items():
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(loaded[name], ensure_ascii=False, indent=2) + "\n")
        print("  题库源数据：%s → %s（%d 条，按 id 升序）" % (name, target, len(loaded[name])))

    validate = [sys.executable, str(HERE / "validate_schema.py"), "--data",
                str(targets["idioms.source.json"]), str(targets["examples.source.json"])]
    print("\n[校验] " + " ".join(validate))
    rc = subprocess.run(validate, cwd=str(ROOT)).returncode
    if rc != 0:
        return fail("题库源数据 schema 校验未通过，已生成的文件不予打包", 2), True

    if skip_pack:
        print("  已跳过打包（--skip-pack）")
        return 0, True

    pack = [sys.executable, str(HERE / "build_pack.py"), pack_version,
            "--content-dir", str(content_dir)]
    print("\n[打包] " + " ".join(pack))
    rc = subprocess.run(pack, cwd=str(ROOT)).returncode
    if rc != 0:
        return fail("题库打包失败", 2), True
    return 0, True


def main(argv):
    args = parse_args(argv)
    incoming = Path(args.incoming).resolve()
    ocr_dir = Path(args.ocr_dir).resolve()
    content_dir = Path(args.content_dir).resolve()

    if not incoming.exists() or not incoming.is_dir():
        return fail("素材目录不存在：%s（请创建后放入真实素材）" % incoming, 3)

    photos, sources, unknown = scan(incoming)
    if not photos and not sources:
        print("没有素材：%s 下未发现真实卷面照片，也未发现 idioms.source.json / examples.source.json。" % incoming)
        print("脚本不会生成虚构内容；请把内容侧提供的真实素材放入后再运行。（详见 content/README.md）")
        return 3
    if unknown:
        return fail("incoming 中有不认识的素材（%s）；照片仅支持 %s，源数据仅支持 %s"
                    % ("、".join(unknown), "/".join(sorted(PHOTO_EXT)),
                       "、".join(sorted(SOURCE_NAMES))), 2)

    print("素材扫描：照片 %d 张，题库源数据 %d 份" % (len(photos), len(sources)))

    # --- 照片：编号登记 + results.json 模板 ---
    if photos:
        registry = ocr_dir / "样张登记表.csv"
        if not registry.exists():
            return fail("缺样张登记表：%s（不要新建假表，应从项目自带表继续沿用编号）" % registry, 2)
        fields, rows = read_registry(registry)
        if SLOT_PREFIX not in fields or SOURCE_COL not in fields:
            return fail("样张登记表缺少「%s」或「%s」列" % (SLOT_PREFIX, SOURCE_COL), 2)
        if len(rows) < len(photos):
            return fail("登记表仅 %d 行，不足 %d 张照片" % (len(rows), len(photos)), 2)

        assignments, err = assign_slots(photos, incoming, rows)
        if err:
            return fail(err, 2)

        write_registry(registry, fields, rows, assignments)
        made, orphan = manage_results(ocr_dir, assignments, rows)
        print("\n[照片编号登记] → %s" % registry)
        for idx, photo, rel in assignments:
            print("  %s ← %s（仅登记来源，识别结果留空待人填）" % (slot_of(rows[idx], idx), photo.name))
        results_path = ocr_dir / "results.json"
        print("[识别模板] %s：新建/更新 %d 条（全 null），保留历史 %d 条"
              % (results_path, made, orphan))
    else:
        print("\n[照片] incoming 未发现卷面照片，跳过样张登记与 results.json 生成。")

    # --- 题库源数据：搬运 → 校验 → 打包 ---
    print()
    rc, did_sources = ingest_sources(sources, content_dir, args.skip_pack, args.pack_version)
    if rc != 0:
        return rc
    if not did_sources:
        print("[题库] incoming 未发现题库源数据，跳过入库/校验/打包。")

    print("\n完成。真实识别结果仍由人工或端侧 OCR 引擎填写 results.json；"
          "填好后运行 tools/ocr_passrate.py 算通过率，再回填验收第 9 条。")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
