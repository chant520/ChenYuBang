#!/usr/bin/env python3
"""把 shared/tokens/tokens.json 生成 Android / iOS 令牌常量。

用法：
  python tools/gen_tokens.py            # 生成（覆盖）
  python tools/gen_tokens.py --check    # 只校验生成结果是否与磁盘一致，不一致退出码 1

规则：
  - 输出确定性：颜色按名称排序，其余按 JSON 内的键序。
  - 生成文件带 GENERATED 头，禁止手改。
  - 十六进制统一转 ARGB；rgba(...) 转 alpha + RGB。
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOKENS = ROOT / "shared" / "tokens" / "tokens.json"
ANDROID_OUT = ROOT / "app-android" / "core-ui" / "src" / "main" / "kotlin" / "com" / "chenyubang" / "core" / "ui" / "tokens" / "Tokens.kt"
IOS_OUT = ROOT / "app-ios" / "Sources" / "Tokens" / "Tokens.swift"

HEADER = "// GENERATED FILE — DO NOT EDIT.\n// Source: shared/tokens/tokens.json (docs/设计规范-v1.2.md)\n// Regenerate: python tools/gen_tokens.py\n"


def parse_color(value):
    """返回 (r, g, b, a) 0-255。支持 #RRGGBB 与 rgba(r,g,b,alpha)。"""
    v = value.strip()
    if v.startswith("#"):
        h = v[1:]
        if len(h) != 6:
            raise ValueError("unsupported hex: %s" % value)
        return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255
    if v.startswith("rgba(") and v.endswith(")"):
        parts = [p.strip() for p in v[5:-1].split(",")]
        if len(parts) != 4:
            raise ValueError("unsupported rgba: %s" % value)
        r, g, b = (int(p) for p in parts[:3])
        a = int(round(float(parts[3]) * 255))
        return r, g, b, a
    raise ValueError("unsupported color: %s" % value)


def camel(name):
    return name


def kotlin():
    data = json.loads(TOKENS.read_text(encoding="utf-8"))
    light = data["color"]["light"]
    dark = data["color"]["dark"]
    out = [HEADER, "package com.chenyubang.core.ui.tokens", "",
           "import androidx.compose.ui.graphics.Color", "",
           "/** 设计令牌。数值唯一来源：docs/设计规范-v1.2.md */",
           "object Tokens {", ""]

    if sorted(light.keys()) != sorted(dark.keys()):
        raise ValueError("light/dark color keys differ")
    out.append("    /** 浅/深色组共同接口：if/else 选色组时保留类型 */")
    out.append("    interface ColorSet {")
    for key in sorted(light.keys()):
        out.append("        val %s: Color" % camel(key))
    out.append("    }")
    out.append("")

    for mode, table in (("Light", light), ("Dark", dark)):
        out.append("    /** %s 模式（规范 2.%s） */" % (mode, "2" if mode == "Light" else "3"))
        out.append("    object %s : ColorSet {" % mode)
        for key in sorted(table.keys()):
            r, g, b, a = parse_color(table[key])
            out.append("        override val %s = Color(0x%02X%02X%02X%02X)" % (camel(key), a, r, g, b))
        out.append("    }")
        out.append("")

    for group, label in (("font", "字号（规范 3.2，11 档）"), ("radius", "圆角（规范 4.1）")):
        out.append("    /** %s，单位 dp/sp */" % label)
        out.append("    object %s {" % ("Font" if group == "font" else "Radius"))
        for key, val in data[group].items():
            if isinstance(val, float) and val != int(val):
                out.append("        val %s = %s" % (key, val))
            else:
                out.append("        val %s = %d" % (key, int(val)))
        out.append("    }")
        out.append("")

    out.append("    /** 间距（规范 4.2），单位 dp */")
    out.append("    object Space {")
    for key, val in data["space"].items():
        out.append("        val %s = %d" % (key, int(val)))
    out.append("    }")
    out.append("")
    out.append("    /** 图标尺寸（规范 6），单位 dp */")
    out.append("    object Icon {")
    for key, val in data["icon"].items():
        out.append("        val %s = %d" % (key, int(val)))
    out.append("    }")
    out.append("}")
    out.append("")
    return "\n".join(out)


def swift():
    data = json.loads(TOKENS.read_text(encoding="utf-8"))
    light = data["color"]["light"]
    dark = data["color"]["dark"]
    out = [HEADER, "import SwiftUI", "",
           "/// 设计令牌。数值唯一来源：docs/设计规范-v1.2.md",
           "public struct TokenColors: Sendable {"]
    for key in sorted(light.keys()):
        out.append("    public let %s: Color" % key)
    out += ["", "    public init("]
    out.append(",\n".join("        %s: Color" % k for k in sorted(light.keys())))
    out.append("    ) {")
    for key in sorted(light.keys()):
        out.append("        self.%s = %s" % (key, key))
    out += ["    }", "}", ""]

    out.append("public enum Tokens {")
    out.append("    public static let light = TokenColors(")
    for key in sorted(light.keys()):
        r, g, b, a = parse_color(light[key])
        out.append("        %s: Color(red: %.4f, green: %.4f, blue: %.4f, opacity: %.4f),"
                   % (key, r / 255, g / 255, b / 255, a / 255))
    out.append("    )")
    out.append("    public static let dark = TokenColors(")
    for key in sorted(dark.keys()):
        r, g, b, a = parse_color(dark[key])
        out.append("        %s: Color(red: %.4f, green: %.4f, blue: %.4f, opacity: %.4f),"
                   % (key, r / 255, g / 255, b / 255, a / 255))
    out.append("    )")
    out.append("")

    out.append("    public enum Font {")
    for key, val in data["font"].items():
        out.append("        public static let %s: CGFloat = %s" % (key, fmt(val)))
    out.append("    }")
    out.append("")
    out.append("    public enum Radius {")
    for key, val in data["radius"].items():
        out.append("        public static let %s: CGFloat = %s" % (key, fmt(val)))
    out.append("    }")
    out.append("")
    out.append("    public enum Space {")
    for key, val in data["space"].items():
        out.append("        public static let %s: CGFloat = %s" % (key, fmt(val)))
    out.append("    }")
    out.append("")
    out.append("    public enum Icon {")
    for key, val in data["icon"].items():
        out.append("        public static let %s: CGFloat = %s" % (key, fmt(val)))
    out.append("    }")
    out.append("}")
    out.append("")
    return "\n".join(out)


def fmt(v):
    if isinstance(v, float) and v != int(v):
        return repr(v)
    return str(int(v))


def main(argv):
    check = "--check" in argv
    pairs = [(ANDROID_OUT, kotlin()), (IOS_OUT, swift())]
    drift = []
    for path, text in pairs:
        if check:
            on_disk = path.read_text(encoding="utf-8") if path.exists() else ""
            if on_disk != text:
                drift.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            with path.open("w", encoding="utf-8", newline="\n") as f:
                f.write(text)
            print("wrote %s (%d bytes)" % (path.relative_to(ROOT), len(text)))
    if check:
        if drift:
            print("TOKEN DRIFT: %s" % ", ".join(drift))
            return 1
        print("tokens ok: generated files match tokens.json")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
