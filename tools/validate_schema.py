#!/usr/bin/env python3
"""校验 shared/schema 下 6 个交换 schema 与 shared/fixtures 的样例数据。

本机没有 jsonschema 库，这里自带一个够用的 draft-07 子集校验器：
  支持 type（含类型数组）、required、properties、additionalProperties:false、
       items、minItems、enum、pattern、minimum、maximum。
  不支持 $ref / oneOf / allOf / if-then。schema 里也不要用，用了会直接报 unsupported。

用法：
  python tools/validate_schema.py             # 校验 fixtures 样例
  python tools/validate_schema.py --update    # 重算 expected.json 的 sha256（改样例后跑一次）
  python tools/validate_schema.py --data <idioms.json> <examples.json>
                                              # 校验 content/ 源数据（ingest 产出，供脚本流水线调用）
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from canonical import combined_digest, sha256_of  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SCHEMA = ROOT / "shared" / "schema"
FIX = ROOT / "shared" / "fixtures"
EXPECTED = FIX / "expected.json"

errors = []


def err(msg):
    errors.append(msg)


SUPPORTED_KEYS = {
    "$schema", "$id", "title", "$comment", "type", "required", "properties",
    "additionalProperties", "items", "minItems", "enum", "pattern",
    "minimum", "maximum",
}


def check_schema_shape(node, path):
    if isinstance(node, dict):
        for k in node.keys():
            if k not in SUPPORTED_KEYS and not k.startswith("$"):
                err("schema %s: 不支持的关键字 %s" % (path, k))
        for k, v in node.items():
            if k in ("properties",):
                for pk, pv in v.items():
                    check_schema_shape(pv, path + ".properties." + pk)
            elif k == "items":
                check_schema_shape(v, path + ".items")


def type_ok(value, t):
    if t == "object":
        return isinstance(value, dict)
    if t == "array":
        return isinstance(value, list)
    if t == "string":
        return isinstance(value, str)
    if t == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if t == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if t == "boolean":
        return isinstance(value, bool)
    if t == "null":
        return value is None
    raise ValueError("unsupported type %s" % t)


def validate(value, schema, path):
    if "type" in schema:
        types = schema["type"] if isinstance(schema["type"], list) else [schema["type"]]
        if not any(type_ok(value, t) for t in types):
            err("%s: 类型应为 %s，实际 %r" % (path, types, type(value).__name__))
            return
    if "enum" in schema and value not in schema["enum"]:
        err("%s: 值 %r 不在枚举 %s 内" % (path, value, schema["enum"]))
    if isinstance(value, str) and "pattern" in schema:
        if re.search(schema["pattern"], value, flags=re.S) is None:
            err("%s: %r 不匹配 %s" % (path, value, schema["pattern"]))
    if isinstance(value, int) and not isinstance(value, bool):
        if "minimum" in schema and value < schema["minimum"]:
            err("%s: %r < minimum %s" % (path, value, schema["minimum"]))
        if "maximum" in schema and value > schema["maximum"]:
            err("%s: %r > maximum %s" % (path, value, schema["maximum"]))

    if isinstance(value, dict):
        for k in schema.get("required", []):
            if k not in value:
                err("%s: 缺必填字段 %s" % (path, k))
        props = schema.get("properties", {})
        if schema.get("additionalProperties") is False:
            for k in value.keys():
                if k not in props:
                    err("%s: 多出未声明字段 %s" % (path, k))
        for k, sub in props.items():
            if k in value:
                validate(value[k], sub, path + "." + k)

    if isinstance(value, list):
        if "minItems" in schema and len(value) < schema["minItems"]:
            err("%s: 元素数 %d < minItems %s" % (path, len(value), schema["minItems"]))
        if "items" in schema:
            for i, item in enumerate(value):
                validate(item, schema["items"], "%s[%d]" % (path, i))


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def validate_content_data(idioms_path, examples_path):
    """校验内容侧源数据（ingest 产出）：只查 idioms / examples schema 与交叉引用，
    不碰 fixtures 样例的 sha256（那套口径只针对 shared/fixtures 样例）。"""
    paths = [Path(idioms_path), Path(examples_path)]
    for p in paths:
        if not p.exists():
            err("content 数据缺文件：%s" % p)
    if errors:
        return _report_content()
    try:
        idioms = load(paths[0])
        examples = load(paths[1])
    except json.JSONDecodeError as e:
        err("content JSON 解析失败：%s" % e)
        return _report_content()

    for name, data in (("idioms", idioms), ("examples", examples)):
        sp = SCHEMA / (name + ".schema.json")
        if not sp.exists():
            err("缺 schema 文件 %s" % sp.name)
            continue
        schema = load(sp)
        check_schema_shape(schema, name)
        validate(data, schema, "content/" + name)

    idiom_ids = {i["id"] for i in idioms if isinstance(i, dict) and "id" in i}
    if len(idiom_ids) != len(idioms):
        err("content/idioms: id 有重复")
    for i, ex in enumerate(examples):
        missing = [x for x in ex.get("idiomIds", []) if x not in idiom_ids]
        if missing:
            err("content/examples[%d]: 引用了不存在的成语 id %s" % (i, missing))
    return _report_content()


def _report_content():
    if errors:
        print("content FAIL (%d)" % len(errors))
        for e in errors:
            print("  - " + e)
        return 1
    print("content ok: idioms/examples 通过 schema 与交叉引用校验")
    return 0


def main(argv):
    if "--data" in argv:
        i = argv.index("--data")
        paths = argv[i + 1:i + 3]
        if len(paths) != 2:
            print("FAIL: --data 后需跟 <idioms.json> <examples.json> 两个路径")
            return 2
        return validate_content_data(*paths)

    update = "--update" in argv

    schema_files = ["pack-manifest", "idioms", "examples", "user-examples", "backup-manifest", "backup-data"]
    schemas = {}
    for name in schema_files:
        p = SCHEMA / (name + ".schema.json")
        if not p.exists():
            err("缺 schema 文件 %s" % p.name)
            continue
        try:
            schemas[name] = load(p)
            check_schema_shape(schemas[name], name)
        except json.JSONDecodeError as e:
            err("%s 不是合法 JSON：%s" % (p.name, e))

    migrations = load(SCHEMA / "migrations.json")
    if migrations.get("currentVersion") != migrations.get("schemaVersion"):
        err("migrations.json: currentVersion 与 schemaVersion 不一致")

    idioms = load(FIX / "idioms.sample.json")
    examples = load(FIX / "examples.sample.json")
    man = load(FIX / "backup" / "manifest.json")
    data = load(FIX / "backup" / "data.json")

    if "idioms" in schemas:
        validate(idioms, schemas["idioms"], "idioms.sample")
    if "examples" in schemas:
        validate(examples, schemas["examples"], "examples.sample")
    if "backup-manifest" in schemas:
        validate(man, schemas["backup-manifest"], "backup.manifest")
    if "backup-data" in schemas:
        validate(data, schemas["backup-data"], "backup.data")

    idiom_ids = {i["id"] for i in idioms}
    if len(idiom_ids) != len(idioms):
        err("idioms.sample: id 有重复")
    for i, ex in enumerate(examples):
        missing = [x for x in ex["idiomIds"] if x not in idiom_ids]
        if missing:
            err("examples.sample[%d]: 引用了成语样例里不存在的 id %s" % (i, missing))

    mistake_ids = {m["id"] for m in data["mistakes"]}
    if len(mistake_ids) != len(data["mistakes"]):
        err("backup.data: mistake id 有重复")
    for i, m in enumerate(data["mistakes"]):
        missing = [x for x in m["idiomIds"] if x not in idiom_ids]
        if missing:
            err("backup.data.mistakes[%d]: 引用了不存在的成语 id %s" % (i, missing))
        related = [r for r in data["reviews"] if r["mistakeId"] == m["id"]]
        if len(related) != m["reviewCount"]:
            err("backup.data.mistakes[%d]: reviewCount=%d 但 reviews 里有 %d 条"
                % (i, m["reviewCount"], len(related)))
        if m["imagePath"] is not None and not m["imagePath"].startswith("images/"):
            err("backup.data.mistakes[%d]: imagePath 必须在 images/ 下" % i)

    for r in data["reviews"]:
        if r["mistakeId"] not in mistake_ids:
            err("backup.data.reviews: mistakeId %s 不存在" % r["mistakeId"])

    tags = {t["name"] for t in data["tags"]}
    for i, m in enumerate(data["mistakes"]):
        missing = [t for t in m["tagNames"] if t not in tags]
        if missing:
            err("backup.data.mistakes[%d]: tagNames %s 不在 tags 里" % (i, missing))

    counts = man["counts"]
    if counts["mistakes"] != len(data["mistakes"]):
        err("backup.manifest.counts.mistakes=%d 与 data 的 %d 不符"
            % (counts["mistakes"], len(data["mistakes"])))
    if counts["reviews"] != len(data["reviews"]):
        err("backup.manifest.counts.reviews 与实际不符")
    if counts["idiomStates"] != len(data["idiomStates"]):
        err("backup.manifest.counts.idiomStates 与实际不符")
    if counts["scopes"] != len(data["scopes"]):
        err("backup.manifest.counts.scopes 与实际不符")
    if man.get("sha256") != sha256_of(data):
        err("backup.manifest: sha256 与 data.json 规范化重算值不符")

    # --- 题库包 manifest：合并摘要成功 / 失败两条分支（规范 v1.4 §4.5）---
    # 两份样例共用同一组合法 idioms/examples 样例；correct.sha256 = 合并摘要，应通过；
    # corrupt.sha256 被篡改，必须被识别为「不匹配」。两者结构上都合法（过 pack-manifest schema）。
    if "pack-manifest" in schemas:
        pack_correct_path = FIX / "pack" / "manifest.correct.json"
        pack_corrupt_path = FIX / "pack" / "manifest.corrupt.json"
        for p in (pack_correct_path, pack_corrupt_path):
            if not p.exists():
                err("缺 pack fixture：%s（v1.4 要求正确/损坏摘要两份）" % p)
        if pack_correct_path.exists() and pack_corrupt_path.exists():
            pack_correct = load(pack_correct_path)
            pack_corrupt = load(pack_corrupt_path)
            validate(pack_correct, schemas["pack-manifest"], "pack.manifest.correct")
            validate(pack_corrupt, schemas["pack-manifest"], "pack.manifest.corrupt")
            recomputed = combined_digest(idioms, examples)
            if pack_correct.get("sha256") != recomputed:
                err("pack.manifest.correct: sha256 与重算合并摘要不符（成功分支应通过）")
            if pack_corrupt.get("sha256") == recomputed:
                err("pack.manifest.corrupt: 损坏样例 sha256 不应等于重算合并摘要（失败分支应被拒）")
            expected_counts = {"idioms": len(idioms), "examples": len(examples)}
            for label, pm in (("correct", pack_correct), ("corrupt", pack_corrupt)):
                if pm.get("counts") != expected_counts:
                    err("pack.manifest.%s: counts 与样例条数不符（期望 %s）"
                        % (label, expected_counts))

    hashes = {
        "idioms.sample.json": sha256_of(idioms),
        "examples.sample.json": sha256_of(examples),
        "backup/manifest.json": sha256_of(man),
        "backup/data.json": sha256_of(data),
    }
    if update:
        with EXPECTED.open("w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps({"algorithm": "tools/canonical.py", "sha256": hashes},
                               ensure_ascii=False, indent=2, sort_keys=True) + "\n")
        print("wrote expected.json")
    else:
        if not EXPECTED.exists():
            err("缺 shared/fixtures/expected.json（先跑 --update 生成）")
        else:
            exp = load(EXPECTED)["sha256"]
            for k, v in hashes.items():
                if exp.get(k) != v:
                    err("sha256 漂移：%s（期望 %s，实际 %s）" % (k, exp.get(k), v))

    if errors:
        print("FAIL (%d)" % len(errors))
        for e in errors:
            print("  - " + e)
        return 1
    print("schema ok: 6 个 schema、%d 条成语、%d 条例题、备份包 counts 与 sha256 全部通过"
          % (len(idioms), len(examples)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
