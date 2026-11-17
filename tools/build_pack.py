#!/usr/bin/env python3
"""题库包打包（口径见 docs/开发规范-v1.4.md §4.4 / §4.5）。

输入：content/idioms/idioms.json + content/examples/examples.json（由 ingest_content.py 生成）
输出：content/dist/pack-vX.Y.Z.zip
      ├── idioms.json
      ├── examples.json
      └── manifest.json        # 符合 shared/schema/pack-manifest.schema.json

sha256 口径（单一事实源，v1.2 决策）：
  - manifest.sha256 直接承载题库包「合并摘要」——按文件名排序
    （examples.json < idioms.json）后各自规范化拼接，再取 SHA-256（§4.5）。
  - 算法唯一实现在 canonical.py:combined_digest，validate_schema.py 复用同一函数；
    无旁车 .zip.sha256——合并摘要随 manifest.json 进包，P16 增量下载才能端到端校验。
  数组排序由 ingest_content.py 写入时保证按 id 升序，故 canonical.py「保持原序」与 §4.5「按 id 升序」一致。

用法：
  python tools/build_pack.py                          # 默认 v1.0.0，读 content/
  python tools/build_pack.py v1.2.0                   # 指定版本
  python tools/build_pack.py v1.2.0 --content-dir content --out-dir content/dist
"""
import argparse
import json
import re
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE))
from canonical import combined_digest  # noqa: E402

VERSION_RE = re.compile(r"^v\d+\.\d+\.\d+$")


def fail(msg, code=1):
    print("FAIL: " + msg)
    return code


def load_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def dump_json(obj):
    # 与 shared/fixtures 写法一致：UTF-8、无 BOM、LF、末尾换行
    return (json.dumps(obj, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def main(argv):
    ap = argparse.ArgumentParser(description="打成题库包 pack-vX.Y.Z.zip")
    ap.add_argument("version", nargs="?", default="v1.0.0", help="包版本，形如 v1.0.0")
    ap.add_argument("--content-dir", default=str(ROOT / "content"), help="题库源数据目录")
    ap.add_argument("--out-dir", default=None, help="输出目录，默认 <content-dir>/dist")
    args = ap.parse_args(argv)

    if not VERSION_RE.match(args.version):
        return fail("版本号格式不对：%s（应为 v数字.数字.数字，如 v1.0.0）" % args.version, 2)

    content_dir = Path(args.content_dir)
    out_dir = Path(args.out_dir) if args.out_dir else content_dir / "dist"

    idioms_path = content_dir / "idioms" / "idioms.json"
    examples_path = content_dir / "examples" / "examples.json"
    for p in (idioms_path, examples_path):
        if not p.exists():
            return fail("缺题库源数据 %s —— 请先运行 tools/ingest_content.py 放入真实素材" % p)

    try:
        idioms = load_json(idioms_path)
        examples = load_json(examples_path)
    except json.JSONDecodeError as e:
        return fail("题库源数据不是合法 JSON：%s" % e)
    if not isinstance(idioms, list) or not isinstance(examples, list):
        return fail("idioms.json / examples.json 顶层必须是数组")
    if not idioms or not examples:
        return fail("题库源数据为空（idioms=%d, examples=%d），拒绝打空包" % (len(idioms), len(examples)))

    built_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    digest = combined_digest(idioms, examples)
    manifest = {
        "schemaVersion": 1,
        "packVersion": args.version,
        "builtAt": built_at,
        "counts": {"idioms": len(idioms), "examples": len(examples)},
        "sha256": digest,
    }

    out_dir.mkdir(parents=True, exist_ok=True)
    zip_path = out_dir / ("pack-%s.zip" % args.version)
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("idioms.json", dump_json(idioms))
        z.writestr("examples.json", dump_json(examples))
        z.writestr("manifest.json", dump_json(manifest))

    print("打包完成：%s" % zip_path)
    print("  manifest.sha256（合并摘要）= %s" % digest)
    print("  条数：成语 %d / 真题 %d；构建时间 %s" % (len(idioms), len(examples), built_at))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
