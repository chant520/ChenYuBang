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
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOKENS = ROOT / "shared" / "tokens" / "tokens.json"
ANDROID_OUT = ROOT / "app-android" / "core-ui" / "src" / "main" / "kotlin" / "com" / "chenyubang" / "core" / "ui" / "tokens" / "Tokens.kt"
IOS_OUT = ROOT / "app-ios" / "Sources" / "Tokens" / "Tokens.swift"

def source_ref(data):
    """规范版本引用取自 tokens.json，避免生成器硬编码版本号。"""
    source = data.get("source")
    if not source:
        raise ValueError("tokens.json 缺少 source 字段（规范版本引用）")
    return source


def header(source):
    return ("// GENERATED FILE — DO NOT EDIT.\n"
            "// Source: shared/tokens/tokens.json (%s)\n"
            "// Regenerate: python tools/gen_tokens.py\n" % source)


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


def parse_shadow(value):
    """CSS box-shadow 串 → 逐层 (ox, oy, blur, spread, r, g, b, a)，长度单位 px。"""
    layers = []
    depth = 0
    buf = ""
    for ch in value:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            layers.append(buf)
            buf = ""
        else:
            buf += ch
    if buf.strip():
        layers.append(buf)
    out = []
    for layer in layers:
        m = re.search(r"rgba\([^)]*\)", layer)
        if not m:
            raise ValueError("shadow layer without rgba: %s" % layer)
        r, g, b, a = parse_color(m.group(0))
        nums = [float(x[:-2] if x.endswith("px") else x)
                for x in re.findall(r"-?\d*\.?\d+(?:px)?", layer[:m.start()] + layer[m.end():])]
        if len(nums) not in (3, 4):
            raise ValueError("shadow layer lengths: %s" % layer)
        ox, oy, blur = nums[:3]
        spread = nums[3] if len(nums) == 4 else 0.0
        out.append((ox, oy, blur, spread, r, g, b, a))
    return out


def fnum(v):
    return ("%g" % v) + "f"


def kotlin():
    data = json.loads(TOKENS.read_text(encoding="utf-8"))
    source = source_ref(data)
    light = data["color"]["light"]
    dark = data["color"]["dark"]
    out = [header(source), "package com.chenyubang.core.ui.tokens", "",
           "import androidx.compose.ui.graphics.Color", "",
           "/** 设计令牌。数值唯一来源：%s */" % source,
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

    groups = (
        ("font", "字号（规范 3.2，11 档）", "Font"),
        ("androidFontRole", "Android 语义字号（规范 3.4）", "FontRole"),
        ("radius", "圆角（规范 4.1）", "Radius"),
    )
    for group, label, name in groups:
        out.append("    /** %s，单位 dp/sp */" % label)
        out.append("    object %s {" % name)
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
    out.append("")
    out.append("    /** 投影层参数（规范 2.2/2.3）：CSS box-shadow 解析结果，实现端按平台等效映射 */")
    out.append("    data class ShadowLayer(")
    out.append("        val offsetX: Float,")
    out.append("        val offsetY: Float,")
    out.append("        val blur: Float,")
    out.append("        val spread: Float,")
    out.append("        val color: Color,")
    out.append("    )")
    out.append("")
    out.append("    object Shadow {")
    for mode in ("light", "dark"):
        out.append("        val fab%s: List<ShadowLayer> = listOf(" % mode.capitalize())
        for ox, oy, blur, spread, r, g, b, a in parse_shadow(data["shadow"]["fab"][mode]):
            out.append("            ShadowLayer(%s, %s, %s, %s, Color(0x%02X%02X%02X%02X)),"
                       % (fnum(ox), fnum(oy), fnum(blur), fnum(spread), a, r, g, b))
        out.append("        )")
    out.append("    }")
    out.append("}")
    out.append("")
    return "\n".join(out)


def swift():
    data = json.loads(TOKENS.read_text(encoding="utf-8"))
    source = source_ref(data)
    light = data["color"]["light"]
    dark = data["color"]["dark"]
    out = [header(source), "import SwiftUI", "",
           "/// 设计令牌。数值唯一来源：%s" % source,
           "public struct TokenColors: Sendable {"]
    for key in sorted(light.keys()):
        out.append("    public let %s: Color" % key)
    out += ["", "    public init("]
    out.append(",\n".join("        %s: Color" % k for k in sorted(light.keys())))
    out.append("    ) {")
    for key in sorted(light.keys()):
        out.append("        self.%s = %s" % (key, key))
    out += ["    }", "}", ""]

    out.append("/// 投影层参数（规范 2.2/2.3）：CSS box-shadow 解析结果，实现端按平台等效映射")
    out.append("public struct ShadowLayer: Sendable {")
    out.append("    public let offsetX: CGFloat")
    out.append("    public let offsetY: CGFloat")
    out.append("    public let blur: CGFloat")
    out.append("    public let spread: CGFloat")
    out.append("    public let color: Color")
    out.append("")
    out.append("    public init(offsetX: CGFloat, offsetY: CGFloat, blur: CGFloat, spread: CGFloat, color: Color) {")
    out.append("        self.offsetX = offsetX")
    out.append("        self.offsetY = offsetY")
    out.append("        self.blur = blur")
    out.append("        self.spread = spread")
    out.append("        self.color = color")
    out.append("    }")
    out.append("}")
    out.append("")
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
    out.append("")
    out.append("    public enum Shadow {")
    for mode in ("light", "dark"):
        entries = []
        for ox, oy, blur, spread, r, g, b, a in parse_shadow(data["shadow"]["fab"][mode]):
            entries.append("            ShadowLayer(offsetX: %s, offsetY: %s, blur: %s, spread: %s, "
                           "color: Color(red: %.4f, green: %.4f, blue: %.4f, opacity: %.4f))"
                           % (fmt(ox), fmt(oy), fmt(blur), fmt(spread), r / 255, g / 255, b / 255, a / 255))
        out.append("        public static let fab%s: [ShadowLayer] = [" % mode.capitalize())
        out.append(",\n".join(entries))
        out.append("        ]")
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
