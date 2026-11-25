#!/usr/bin/env python3
"""校验 shared/schema 下交换 schema 与 shared/fixtures 的样例数据。

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
import zipfile
import zlib
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
        pattern = schema["pattern"]
        matched = re.search(pattern, value, flags=re.S)
        if matched is None or (pattern.startswith("^") and pattern.endswith("$") and matched.end() != len(value)):
            err("%s: %r 不匹配 %s" % (path, value, pattern))
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


PACK_MEMBERS = ("idioms.json", "examples.json", "manifest.json")
# 取双端导入限制的交集，确保本地产物不会被任一客户端拒绝。
MAX_PACK_INPUT_BYTES = 4 * 1024 * 1024
MAX_PACK_ENTRY_BYTES = 4 * 1024 * 1024
MAX_PACK_EXPANDED_BYTES = 8 * 1024 * 1024


def validate_pack_zip(path):
    errors.clear()
    pack_path = Path(path)
    if not pack_path.is_file():
        err("题库包不存在：%s" % pack_path)
        return None, None, None, list(errors)
    if pack_path.stat().st_size > MAX_PACK_INPUT_BYTES:
        err("题库包超过 %d 字节输入上限" % MAX_PACK_INPUT_BYTES)
        return None, None, None, list(errors)

    try:
        with zipfile.ZipFile(pack_path) as archive:
            infos = archive.infolist()
            names = [info.filename for info in infos]
            if len(names) != len(set(names)):
                err("题库包含重复路径")
            if set(names) != set(PACK_MEMBERS) or any(info.is_dir() for info in infos):
                err("题库包根目录必须且只能包含 %s" % ", ".join(PACK_MEMBERS))
            if any(info.file_size > MAX_PACK_ENTRY_BYTES for info in infos):
                err("题库包单文件超过 %d 字节展开上限" % MAX_PACK_ENTRY_BYTES)
            if sum(info.file_size for info in infos) > MAX_PACK_EXPANDED_BYTES:
                err("题库包超过 %d 字节总展开上限" % MAX_PACK_EXPANDED_BYTES)
            if errors:
                return None, None, None, list(errors)
            payload = {name: archive.read(name) for name in PACK_MEMBERS}
    except (OSError, RuntimeError, NotImplementedError, zipfile.BadZipFile, zlib.error) as error:
        err("题库包无法读取（截断、CRC 或压缩格式异常）：%s" % error)
        return None, None, None, list(errors)

    try:
        idioms = json.loads(payload["idioms.json"].decode("utf-8"))
        examples = json.loads(payload["examples.json"].decode("utf-8"))
        manifest = json.loads(payload["manifest.json"].decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        err("题库包 JSON 解析失败：%s" % error)
        return None, None, None, list(errors)

    schema_path = SCHEMA / "pack-manifest.schema.json"
    if not schema_path.exists():
        err("缺 schema 文件 %s" % schema_path.name)
    else:
        schema = load(schema_path)
        check_schema_shape(schema, "pack-manifest")
        validate(manifest, schema, "pack.manifest")

    if isinstance(manifest, dict) and manifest.get("schemaVersion") != 1:
        err("pack.manifest.schemaVersion 仅支持 1")

    if not isinstance(idioms, list) or not isinstance(examples, list):
        err("idioms.json / examples.json 顶层必须是数组")
    elif not idioms or not examples:
        err("题库包不得为空")
    else:
        for name, data in (("idioms", idioms), ("examples", examples)):
            data_schema_path = SCHEMA / (name + ".schema.json")
            if not data_schema_path.exists():
                err("缺 schema 文件 %s" % data_schema_path.name)
                continue
            data_schema = load(data_schema_path)
            check_schema_shape(data_schema, name)
            validate(data, data_schema, "pack/" + name)

        idiom_ids = {
            item["id"] for item in idioms if isinstance(item, dict) and isinstance(item.get("id"), str)
        }
        example_ids = {
            item["id"] for item in examples if isinstance(item, dict) and isinstance(item.get("id"), str)
        }
        if len(idiom_ids) != len(idioms):
            err("pack/idioms: id 缺失、类型错误或重复")
        if len(example_ids) != len(examples):
            err("pack/examples: id 缺失、类型错误或重复")
        for index, example in enumerate(examples):
            if not isinstance(example, dict):
                continue
            idiom_refs = example.get("idiomIds")
            if not isinstance(idiom_refs, list):
                continue
            missing = [
                idiom_id
                for idiom_id in idiom_refs
                if not isinstance(idiom_id, str) or idiom_id not in idiom_ids
            ]
            if missing:
                err("pack/examples[%d]: 引用了不存在的成语 id %s" % (index, missing))

    if isinstance(manifest, dict) and isinstance(idioms, list) and isinstance(examples, list):
        expected_counts = {"idioms": len(idioms), "examples": len(examples)}
        if manifest.get("counts") != expected_counts:
            err("pack.manifest.counts 与实际条数不符（期望 %s）" % expected_counts)
        try:
            digest = combined_digest(idioms, examples)
        except (TypeError, ValueError) as error:
            err("pack.manifest.sha256 无法重算：%s" % error)
        else:
            if manifest.get("sha256") != digest:
                err("pack.manifest.sha256 与重算合并摘要不符")
    return manifest, idioms, examples, list(errors)


def validate_channel_manifest(value):
    errors.clear()
    schema_path = SCHEMA / "channel-manifest.schema.json"
    if not schema_path.exists():
        err("缺 schema 文件 %s" % schema_path.name)
    else:
        schema = load(schema_path)
        check_schema_shape(schema, "channel-manifest")
        validate(value, schema, "channel.manifest")
    if isinstance(value, dict):
        if value.get("schemaVersion") != 1:
            err("channel.manifest.schemaVersion 仅支持 1")
        version = value.get("packVersion")
        pack_file = value.get("packFile")
        if isinstance(version, str) and isinstance(pack_file, str):
            expected = "pack-%s.zip" % version
            if pack_file != expected:
                err("channel.manifest.packFile 应为 %s" % expected)
    return list(errors)


def validate_content_data(idioms_path, examples_path):
    """校验内容侧源数据（ingest 产出）：只查 idioms / examples schema 与交叉引用，
    不碰 fixtures 样例的 sha256（那套口径只针对 shared/fixtures 样例）。"""
    errors.clear()
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

    if not isinstance(idioms, list) or not isinstance(examples, list):
        return _report_content()
    idiom_ids = {i["id"] for i in idioms if isinstance(i, dict) and isinstance(i.get("id"), str)}
    if len(idiom_ids) != len(idioms):
        err("content/idioms: id 缺失、类型错误或重复")
    for i, ex in enumerate(examples):
        if not isinstance(ex, dict):
            continue
        idiom_refs = ex.get("idiomIds")
        if not isinstance(idiom_refs, list):
            continue
        missing = [x for x in idiom_refs if not isinstance(x, str) or x not in idiom_ids]
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

    schema_files = ["pack-manifest", "channel-manifest", "idioms", "examples", "user-examples", "backup-manifest", "backup-data"]
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
    versions = [m.get("version") for m in migrations.get("migrations", [])]
    if versions != list(range(1, len(versions) + 1)):
        err("migrations.json: 版本号必须从 1 起连续递增，实际 %s" % versions)
    elif versions and migrations.get("currentVersion") != versions[-1]:
        err("migrations.json: currentVersion 应等于最新迁移版本 %s" % versions[-1])

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

    if "channel-manifest" in schemas:
        channel_correct_path = FIX / "channel" / "latest.correct.json"
        channel_invalid_path = FIX / "channel" / "latest.invalid-pack-file.json"
        for p in (channel_correct_path, channel_invalid_path):
            if not p.exists():
                err("缺 channel fixture：%s（v1.9 要求合法/非法 packFile 两份）" % p)
        if channel_correct_path.exists() and channel_invalid_path.exists():
            channel_correct = load(channel_correct_path)
            channel_invalid = load(channel_invalid_path)
            validate(channel_correct, schemas["channel-manifest"], "channel.manifest.correct")
            before_invalid = len(errors)
            validate(channel_invalid, schemas["channel-manifest"], "channel.manifest.invalid-pack-file")
            invalid_errors = errors[before_invalid:]
            del errors[before_invalid:]
            if not any("packFile" in message for message in invalid_errors):
                err("channel.manifest.invalid-pack-file: 绝对 URL 必须因 packFile 规则被拒绝")

            expected_pack_file = "pack-%s.zip" % channel_correct.get("packVersion")
            if channel_correct.get("packFile") != expected_pack_file:
                err("channel.manifest.correct: packFile 应为 %s" % expected_pack_file)
            pack_reference_path = FIX / "pack" / "manifest.correct.json"
            if pack_reference_path.exists():
                pack_reference = load(pack_reference_path)
                for key in ("schemaVersion", "packVersion", "builtAt", "counts", "sha256"):
                    if channel_correct.get(key) != pack_reference.get(key):
                        err("channel.manifest.correct: %s 应与包内 manifest 一致" % key)

    hashes = {
        "idioms.sample.json": sha256_of(idioms),
        "examples.sample.json": sha256_of(examples),
        "backup/manifest.json": sha256_of(man),
        "backup/data.json": sha256_of(data),
        "channel/latest.correct.json": sha256_of(load(FIX / "channel" / "latest.correct.json")),
        "channel/latest.invalid-pack-file.json": sha256_of(load(FIX / "channel" / "latest.invalid-pack-file.json")),
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
    print("schema ok: %d 个 schema、%d 条成语、%d 条例题、备份包与通道清单 counts/sha256 全部通过"
          % (len(schemas), len(idioms), len(examples)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
