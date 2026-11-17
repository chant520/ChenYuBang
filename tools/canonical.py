#!/usr/bin/env python3
"""规范化 JSON 与 sha256（题库包 / 备份包的完整性口径）。

规范化算法（项目自定，比 RFC 8785 更窄，够用且两端都能用一种写法复现）：
  1. 只允许对象 / 数组 / 字符串 / 整数 / 布尔 / null；出现浮点直接报错（金额、比例一律用整数或字符串）。
  2. 字符串统一 NFC，UTF-8 编码，不做 \\u 转义（ensure_ascii=False）。
  3. 对象键按 Unicode 码点升序递归排序；数组保持原序。
  4. 无多余空白：分隔符固定为 "," 与 ":"。
  5. 顶层写文件时不带 BOM、不带换行。

两端实现要求：
  Android —— 用 kotlinx.serialization 的 JsonObject 递归排序后再拼串，不要用 Gson 的默认输出。
  iOS     —— 用 JSONSerialization 的 .sortedKeys 输出，再把浮点等于整数的值转成 Int（否则会带 .0）。

用法：
  python tools/canonical.py <file.json>          # 打印规范化文本
  python tools/canonical.py --sha <file.json>    # 打印 sha256
"""
import hashlib
import json
import sys
import unicodedata
from pathlib import Path


def _normalize(value, path="$"):
    if isinstance(value, dict):
        out = {}
        for k in value.keys():
            if not isinstance(k, str):
                raise ValueError("%s: 键必须是字符串" % path)
            out[unicodedata.normalize("NFC", k)] = _normalize(value[k], path + "." + k)
        return out
    if isinstance(value, list):
        return [_normalize(v, "%s[%d]" % (path, i)) for i, v in enumerate(value)]
    if isinstance(value, str):
        return unicodedata.normalize("NFC", value)
    if isinstance(value, bool) or value is None or isinstance(value, int):
        return value
    if isinstance(value, float):
        if value.is_integer():
            return int(value)
        raise ValueError("%s: 不允许浮点（先用整数或字符串表示）" % path)
    raise ValueError("%s: 不支持的类型 %r" % (path, type(value)))


def canonical_text(obj):
    return json.dumps(_normalize(obj), ensure_ascii=False, sort_keys=True,
                      separators=(",", ":"), allow_nan=False)


def canonical_bytes(obj):
    return canonical_text(obj).encode("utf-8")


def sha256_of(obj):
    return hashlib.sha256(canonical_bytes(obj)).hexdigest()


def combined_digest(idioms, examples):
    """题库包合并摘要（规范 §4.5，单一事实源）：按文件名排序
    （examples.json < idioms.json）后各自规范化拼接，再取 SHA-256。
    build_pack.py 写 manifest.sha256 与 validate_schema.py 校验均调用本函数。"""
    ordered = sorted({"examples.json": examples, "idioms.json": idioms}.items())
    merged = "".join(canonical_text(obj) for _, obj in ordered)
    return hashlib.sha256(merged.encode("utf-8")).hexdigest()


def sha256_file(path):
    obj = json.loads(Path(path).read_text(encoding="utf-8"))
    return sha256_of(obj)


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    if "--sha" in argv:
        target = [a for a in argv if a != "--sha"][0]
        print(sha256_file(target))
        return 0
    target = argv[0]
    print(canonical_text(json.loads(Path(target).read_text(encoding="utf-8"))))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
