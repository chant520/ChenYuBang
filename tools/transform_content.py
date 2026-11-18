#!/usr/bin/env python3
"""raw→schema 转换：把内容侧 staging 的真实素材转成题库包源数据（绝不生成虚构内容）。

本脚本只做「结构转换 + 确定性派生」，所有实质内容（释义/出处/题干/选项/解析）都来自
内容侧与采集代理提供的真实素材；脚本不编数字、不造题、不伪造任何字段。

输入（真实素材，staging）：
  content/staging/idioms.raw.json        采集的成语词典字段（word/pinyin/sense/origin/color/…，无 id/initials/scopes）
  content/staging/examples.raw.json      真题 raw（options 数组、words、source，无 id/idiomIds）
  content/staging/idioms.final-list.json 定稿成语清单（覆盖校验基准）

输出（schema 对齐，供 ingest_content.py / build_pack.py 消费）：
  <out-dir>/idioms.source.json
  <out-dir>/examples.source.json

派生规则（全部确定性、可复现）：
  id        uuid4；幂等：out-dir 已有 source 文件时按 word/stem 复用旧 id
            （增量换版时成语 id 稳定，用户错题/收藏外键不断；符合 content/README.md 的 uuid v4 约定）
  initials  由规范化 pinyin 各音节首字母拼接
  pinyin    去声调、小写、单空格分隔（schema: ^[a-z]+( [a-z]+){1,7}$）
  scopes    由引用该成语的真题 examType 去重派生（保证 ≥1；教师编恒不出现）
  frequency 采集给出则校验 1–5；缺则按「本题库引用题次」截断 1–5（真实计数，非虚构）
  idiomIds  真题 words ∩ 成语映射，按首现序去重（解析-only 词若未采集则自然排除）
  options   数组 [a,b,c,d] → {A,B,C,D}；双空选项「X Y」整体作为一个选项字符串保留
  丢弃      words / source / sources（schema additionalProperties:false）

用法：
  python tools/transform_content.py                 # 读默认 staging，写 content/source/，并接 schema 校验
  python tools/transform_content.py --selftest      # 内存自测：全路径 + 失败分支
  python tools/transform_content.py --out-dir content/source --no-validate
"""
import argparse
import json
import re
import subprocess
import sys
import unicodedata
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
STAGING = ROOT / "content" / "staging"

UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
WORD_RE = re.compile(r"^[一-龥]{2,8}$")
PINYIN_RE = re.compile(r"^[a-z]+( [a-z]+){1,7}$")
INITIALS_RE = re.compile(r"^[a-z]{2,8}$")
COLORS = {"褒义", "贬义", "中性"}
EXAM_TYPES = {"国考", "省考", "事业编", "教师编"}
QUESTION_TYPES = {"言语理解", "逻辑填空", "语句表达", "常识判断"}
PITFALL_KINDS = {"望文生义", "对象误用", "褒贬误用", "语义重复", "搭配不当"}
OPTION_KEYS = ["A", "B", "C", "D"]

IDIOM_KEYS = ["id", "word", "pinyin", "initials", "sense", "origin", "color",
              "frequency", "scopes", "pitfalls", "synonyms", "antonyms"]
EXAMPLE_KEYS = ["id", "stem", "options", "answer", "explain", "examType",
                "year", "region", "questionType", "idiomIds"]


def normalize_pinyin(raw):
    """去声调、小写、单空格分隔。ü 经 NFD 分解后去掉组合符变 u。"""
    decomposed = unicodedata.normalize("NFD", str(raw).lower())
    stripped = "".join(c for c in decomposed if unicodedata.combining(c) == 0)
    return " ".join(stripped.split())


def derive_initials(pinyin):
    return "".join(syll[0] for syll in pinyin.split() if syll)


def new_id():
    return str(uuid.uuid4())


def is_uuid(value):
    return isinstance(value, str) and UUID_RE.match(value) is not None


def load_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def dump_json(obj):
    return json.dumps(obj, ensure_ascii=False, indent=2) + "\n"


def options_to_dict(raw_options, errors, where):
    """数组 [a,b,c,d] 或已是 {A,B,C,D} → 统一成对象；双空选项整体保留。"""
    if isinstance(raw_options, dict):
        opts = raw_options
    elif isinstance(raw_options, list):
        if len(raw_options) != 4:
            errors.append("%s: options 数组须恰为 4 项，实际 %d" % (where, len(raw_options)))
            return None
        opts = dict(zip(OPTION_KEYS, raw_options))
    else:
        errors.append("%s: options 须为数组或对象" % where)
        return None
    out = {}
    for k in OPTION_KEYS:
        v = opts.get(k)
        if not isinstance(v, str) or len(v) < 2:
            errors.append("%s: 选项 %s 须为 ≥2 字字符串" % (where, k))
            return None
        out[k] = v
    return out


def transform(idioms_raw, examples_raw, final_words, reuse_idioms=None, reuse_examples=None):
    """核心纯函数：返回 (idioms_out, examples_out, errors)。errors 非空时产物不可用。

    reuse_idioms / reuse_examples：word→id / stem→id 映射，用于幂等复用旧 uuid。
    final_words：定稿清单 word 集合，覆盖校验基准（None 表示跳过覆盖校验）。
    """
    errors = []
    reuse_idioms = reuse_idioms or {}
    reuse_examples = reuse_examples or {}

    # --- 成语：结构转换 + 派生（scopes/frequency 待真题链接后回填）---
    idioms = []
    word_to_id = {}
    raw_by_word = {}
    for i, entry in enumerate(idioms_raw):
        where = "idioms[%d]" % i
        if not isinstance(entry, dict):
            errors.append("%s: 须为对象" % where)
            continue
        word = entry.get("word")
        if not isinstance(word, str) or not WORD_RE.match(word):
            errors.append("%s: word 须为 2–8 汉字，实际 %r" % (where, word))
            continue
        if word in word_to_id:
            errors.append("%s: word 重复 %s" % (where, word))
            continue
        pinyin = normalize_pinyin(entry.get("pinyin", ""))
        if not PINYIN_RE.match(pinyin):
            errors.append("%s: pinyin 规范化后不合法 %r" % (where, pinyin))
            continue
        initials = derive_initials(pinyin)
        if not INITIALS_RE.match(initials):
            errors.append("%s: initials 派生不合法 %r" % (where, initials))
            continue
        sense = entry.get("sense")
        if not isinstance(sense, str) or len(sense) < 4:
            errors.append("%s: sense 须 ≥4 字（内容侧必填下限）" % where)
            continue
        origin = entry.get("origin")
        if not isinstance(origin, str) or len(origin) < 4:
            errors.append("%s: origin 须 ≥4 字（内容侧拍板「释义+出处必填」，缺出处不予入库）" % where)
            continue
        color = entry.get("color")
        if color not in COLORS:
            errors.append("%s: color 须为 褒义/贬义/中性，实际 %r" % (where, color))
            continue
        freq = entry.get("frequency")
        if freq is not None and (not isinstance(freq, int) or isinstance(freq, bool) or not 1 <= freq <= 5):
            errors.append("%s: frequency 须为 1–5 整数或省略，实际 %r" % (where, freq))
            continue
        pitfalls = entry.get("pitfalls", [])
        if not isinstance(pitfalls, list):
            errors.append("%s: pitfalls 须为数组" % where)
            continue
        for j, p in enumerate(pitfalls):
            if not isinstance(p, dict) or p.get("kind") not in PITFALL_KINDS \
                    or not isinstance(p.get("note"), str) or len(p["note"]) < 4:
                errors.append("%s.pitfalls[%d]: kind 须属枚举且 note ≥4 字" % (where, j))
        for key in ("synonyms", "antonyms"):
            arr = entry.get(key, [])
            if not isinstance(arr, list) or any(not isinstance(w, str) or not WORD_RE.match(w) for w in arr):
                errors.append("%s.%s: 须为 2–8 汉字成语数组" % (where, key))

        iid = reuse_idioms.get(word) or new_id()
        word_to_id[word] = iid
        raw_by_word[word] = entry
        obj = {"id": iid, "word": word, "pinyin": pinyin, "initials": initials,
               "sense": sense, "origin": origin, "color": color}
        if freq is not None:
            obj["frequency"] = freq
        if pitfalls:
            obj["pitfalls"] = pitfalls
        for key in ("synonyms", "antonyms"):
            if entry.get(key):
                obj[key] = entry[key]
        idioms.append(obj)

    # --- 真题：options 数组→对象、idiomIds 链接、丢弃 words/source ---
    examples = []
    referenced = {}  # idiomId -> [examType,…]
    for i, ex in enumerate(examples_raw):
        where = "examples[%d]" % i
        if not isinstance(ex, dict):
            errors.append("%s: 须为对象" % where)
            continue
        stem = ex.get("stem")
        if not isinstance(stem, str) or len(stem) < 6:
            errors.append("%s: stem 须 ≥6 字" % where)
            continue
        opts = options_to_dict(ex.get("options"), errors, where)
        if opts is None:
            continue
        answer = ex.get("answer")
        if answer not in OPTION_KEYS:
            errors.append("%s: answer 须为 A/B/C/D" % where)
            continue
        explain = ex.get("explain")
        if not isinstance(explain, str) or len(explain) < 4:
            errors.append("%s: explain 须 ≥4 字" % where)
            continue
        exam_type = ex.get("examType")
        if exam_type not in EXAM_TYPES:
            errors.append("%s: examType 非法 %r" % (where, exam_type))
            continue
        year = ex.get("year")
        if not isinstance(year, int) or isinstance(year, bool) or not 2000 <= year <= 2100:
            errors.append("%s: year 须为 2000–2100 整数" % where)
            continue
        qtype = ex.get("questionType")
        if qtype not in QUESTION_TYPES:
            errors.append("%s: questionType 非法 %r" % (where, qtype))
            continue
        words = ex.get("words", [])
        if not isinstance(words, list):
            errors.append("%s: words 须为数组" % where)
            continue
        idiom_ids = []
        for w in words:
            iid = word_to_id.get(w)
            if iid and iid not in idiom_ids:
                idiom_ids.append(iid)
        if not idiom_ids:
            errors.append("%s: idiomIds 为空（words 与已采集成语无交集，缺录）" % where)
            continue
        eid = reuse_examples.get(stem) or new_id()
        obj = {"id": eid, "stem": stem, "options": opts, "answer": answer,
               "explain": explain, "examType": exam_type, "year": year, "questionType": qtype}
        region = ex.get("region")
        if isinstance(region, str) and len(region) >= 2:
            obj["region"] = region
        obj["idiomIds"] = idiom_ids
        examples.append(obj)
        for iid in idiom_ids:
            referenced.setdefault(iid, []).append(exam_type)

    # --- 回填 scopes / frequency（确定性派生）---
    for obj in idioms:
        exam_types = referenced.get(obj["id"], [])
        if not exam_types:
            errors.append("成语「%s」未被任何真题引用，scopes 无法派生（须 ≥1）" % obj["word"])
            continue
        obj["scopes"] = sorted(set(exam_types))
        if "frequency" not in obj:
            obj["frequency"] = max(1, min(5, len(exam_types)))

    # --- 覆盖校验：定稿清单 ⊆ 已采集 ---
    if final_words is not None:
        collected = set(word_to_id)
        missing = sorted(set(final_words) - collected)
        if missing:
            errors.append("定稿清单缺采集 %d 条：%s" % (len(missing), "、".join(missing)))

    if errors:
        return None, None, errors
    idioms.sort(key=lambda x: x["id"])
    examples.sort(key=lambda x: x["id"])
    return idioms, examples, []


def order_keys(obj, keys):
    return {k: obj[k] for k in keys if k in obj}


def run(idioms_raw_path, examples_raw_path, final_list_path, out_dir, validate):
    for p in (idioms_raw_path, examples_raw_path):
        if not Path(p).exists():
            print("FAIL: 缺输入素材 %s（须由内容侧/采集代理提供真实素材，脚本不生成）" % p)
            return 3
    try:
        idioms_raw = load_json(idioms_raw_path)
        examples_raw = load_json(examples_raw_path)
    except json.JSONDecodeError as e:
        print("FAIL: 输入不是合法 JSON：%s" % e)
        return 2
    if not isinstance(idioms_raw, list) or not isinstance(examples_raw, list):
        print("FAIL: idioms.raw / examples.raw 顶层须为数组")
        return 2

    final_words = None
    if final_list_path and Path(final_list_path).exists():
        final_words = {e["word"] for e in load_json(final_list_path) if isinstance(e, dict) and "word" in e}

    out_dir = Path(out_dir)
    reuse_idioms, reuse_examples = {}, {}
    old_i = out_dir / "idioms.source.json"
    old_e = out_dir / "examples.source.json"
    if old_i.exists():
        reuse_idioms = {e["word"]: e["id"] for e in load_json(old_i) if "word" in e and "id" in e}
    if old_e.exists():
        reuse_examples = {e["stem"]: e["id"] for e in load_json(old_e) if "stem" in e and "id" in e}

    idioms, examples, errors = transform(idioms_raw, examples_raw, final_words, reuse_idioms, reuse_examples)
    if errors:
        print("转换 FAIL (%d)：" % len(errors))
        for e in errors:
            print("  - " + e)
        return 2

    out_dir.mkdir(parents=True, exist_ok=True)
    idioms_out = [order_keys(o, IDIOM_KEYS) for o in idioms]
    examples_out = [order_keys(o, EXAMPLE_KEYS) for o in examples]
    (out_dir / "idioms.source.json").write_text(dump_json(idioms_out), encoding="utf-8", newline="\n")
    (out_dir / "examples.source.json").write_text(dump_json(examples_out), encoding="utf-8", newline="\n")
    print("转换完成：成语 %d 条 / 真题 %d 道 → %s" % (len(idioms_out), len(examples_out), out_dir))
    print("  idioms.source.json / examples.source.json（按 id 升序，uuid 幂等复用）")

    if validate:
        cmd = [sys.executable, str(HERE / "validate_schema.py"), "--data",
               str(out_dir / "idioms.source.json"), str(out_dir / "examples.source.json")]
        print("\n[校验] " + " ".join(cmd))
        rc = subprocess.run(cmd, cwd=str(ROOT)).returncode
        if rc != 0:
            print("FAIL: schema 校验未通过，产物不予入库")
            return 2
    print("\n下一步：python tools/ingest_content.py --incoming %s --pack-version v1.0.0" % out_dir)
    return 0


# ----------------------------- 自测 -----------------------------
def _idiom(word, pinyin, **kw):
    base = {"word": word, "pinyin": pinyin, "sense": "测试释义内容四字",
            "origin": "测试出处内容四字", "color": "中性"}
    base.update(kw)
    return base


def _example(stem, options, words, **kw):
    base = {"stem": stem, "options": options, "answer": "A", "explain": "测试解析内容四字",
            "examType": "国考", "year": 2024, "region": "全国", "questionType": "逻辑填空",
            "words": words, "source": {"url": "https://example.test", "title": "测试来源"}}
    base.update(kw)
    return base


def selftest():
    passed, failed = 0, 0

    def check(name, cond):
        nonlocal passed, failed
        if cond:
            passed += 1
        else:
            failed += 1
            print("  FAIL: " + name)

    # 1 happy path：声调拼音规范化、initials 派生、options 数组→对象、链接、丢弃 words/source
    ir = [_idiom("甲乙丙丁", "jiǎ yǐ bǐng dīng"), _idiom("戊己庚辛", "wu ji geng xin")]
    er = [_example("这是一道用于自测的题干甲", ["甲乙丙丁", "戊己庚辛", "选项丙啊", "选项丁啊"],
                   ["甲乙丙丁", "戊己庚辛"])]
    idioms, examples, errs = transform(ir, er, {"甲乙丙丁", "戊己庚辛"})
    check("happy 无错误", not errs)
    check("pinyin 去声调", idioms[0]["pinyin"] == "jia yi bing ding" or idioms[1]["pinyin"] == "jia yi bing ding")
    by_word = {o["word"]: o for o in idioms}
    check("initials 派生", by_word["甲乙丙丁"]["initials"] == "jybd")
    check("uuid4 形", is_uuid(by_word["甲乙丙丁"]["id"]))
    check("scopes 派生国考", by_word["甲乙丙丁"]["scopes"] == ["国考"])
    check("frequency 缺省按题次=1", by_word["甲乙丙丁"]["frequency"] == 1)
    check("options 转对象", examples[0]["options"] == {"A": "甲乙丙丁", "B": "戊己庚辛", "C": "选项丙啊", "D": "选项丁啊"})
    check("idiomIds 链接两条", len(examples[0]["idiomIds"]) == 2)
    check("丢弃 words/source", "words" not in examples[0] and "source" not in examples[0])
    check("按 id 升序", idioms == sorted(idioms, key=lambda x: x["id"]))

    # 2 双空选项整体保留
    er2 = [_example("这是一道双空自测题干乙", ["甲乙丙丁 戊己庚辛", "选项乙啊", "选项丙啊", "选项丁啊"],
                    ["甲乙丙丁", "戊己庚辛"])]
    _, ex2, e2 = transform(ir, er2, None)
    check("双空选项保留", not e2 and ex2[0]["options"]["A"] == "甲乙丙丁 戊己庚辛")

    # 3 解析-only 词未采集 → 自然排除，不报错
    er3 = [_example("这是一道含解析词的题干丙", ["甲乙丙丁", "戊己庚辛", "选项丙啊", "选项丁啊"],
                    ["甲乙丙丁", "戊己庚辛", "未采集词"])]
    _, ex3, e3 = transform(ir, er3, None)
    check("解析-only 词排除", not e3 and len(ex3[0]["idiomIds"]) == 2)

    # 4 定稿清单缺采集 → FAIL
    _, _, e4 = transform(ir, er, {"甲乙丙丁", "戊己庚辛", "缺采集词"})
    check("缺采集报错", any("缺采集" in x for x in e4))

    # 5 options 长度非 4 → FAIL
    er5 = [_example("这是一道选项数错的题干丁", ["甲乙丙丁", "戊己庚辛"], ["甲乙丙丁"])]
    _, _, e5 = transform(ir, er5, None)
    check("options 非4报错", any("恰为 4 项" in x for x in e5))

    # 6 frequency 越界 → FAIL；合法则保留
    ir6 = [_idiom("甲乙丙丁", "jia yi bing ding", frequency=6)]
    _, _, e6 = transform(ir6, er, None)
    check("frequency 越界报错", any("frequency" in x for x in e6))
    ir6b = [_idiom("甲乙丙丁", "jia yi bing ding", frequency=3), _idiom("戊己庚辛", "wu ji geng xin")]
    i6b, _, e6b = transform(ir6b, er, None)
    check("frequency 合法保留", not e6b and {o["word"]: o for o in i6b}["甲乙丙丁"]["frequency"] == 3)

    # 7 幂等复用：reuse 映射 → id 不变
    reuse = {o["word"]: o["id"] for o in idioms}
    i7, x7, e7 = transform(ir, er, None, reuse_idioms=reuse,
                           reuse_examples={examples[0]["stem"]: examples[0]["id"]})
    check("幂等复用 id", not e7 and {o["word"]: o["id"] for o in i7} == reuse
          and x7[0]["id"] == examples[0]["id"])

    # 8 缺出处 → FAIL（内容侧必填下限）
    ir8 = [{"word": "甲乙丙丁", "pinyin": "jia yi bing ding", "sense": "测试释义内容四字", "color": "中性"}]
    _, _, e8 = transform(ir8, er, None)
    check("缺出处报错", any("origin" in x for x in e8))

    # 9 color 非法 → FAIL
    ir9 = [_idiom("甲乙丙丁", "jia yi bing ding", color="褒贬")]
    _, _, e9 = transform(ir9, er, None)
    check("color 非法报错", any("color" in x for x in e9))

    # 10 未被引用成语 → scopes 无法派生 FAIL
    ir10 = [_idiom("甲乙丙丁", "jia yi bing ding"), _idiom("戊己庚辛", "wu ji geng xin"),
            _idiom("壬癸子丑", "ren gui zi chou")]
    _, _, e10 = transform(ir10, er, None)
    check("未引用报错", any("未被任何真题引用" in x for x in e10))

    # 11 多真题 → scopes 去重并集、frequency 按题次
    er11 = [_example("自测题干第一道甲啊", ["甲乙丙丁", "戊己庚辛", "选项丙啊", "选项丁啊"],
                     ["甲乙丙丁", "戊己庚辛"], examType="国考"),
            _example("自测题干第二道乙啊", ["甲乙丙丁", "选项乙啊", "选项丙啊", "选项丁啊"],
                     ["甲乙丙丁"], examType="省考")]
    i11, _, e11 = transform(ir, er11, None)
    bw11 = {o["word"]: o for o in i11}
    check("scopes 并集", not e11 and bw11["甲乙丙丁"]["scopes"] == ["国考", "省考"])
    check("frequency 题次=2", bw11["甲乙丙丁"]["frequency"] == 2)

    print("selftest: %d passed, %d failed" % (passed, failed))
    return 0 if failed == 0 else 1


def main(argv):
    ap = argparse.ArgumentParser(description="raw→schema 转换（不生成虚构内容）")
    ap.add_argument("--idioms-raw", default=str(STAGING / "idioms.raw.json"))
    ap.add_argument("--examples-raw", default=str(STAGING / "examples.raw.json"))
    ap.add_argument("--final-list", default=str(STAGING / "idioms.final-list.json"))
    ap.add_argument("--out-dir", default=str(ROOT / "content" / "source"))
    ap.add_argument("--no-validate", action="store_true", help="转换后不接 schema 校验")
    ap.add_argument("--selftest", action="store_true", help="内存自测全路径与失败分支")
    args = ap.parse_args(argv)
    if args.selftest:
        return selftest()
    return run(args.idioms_raw, args.examples_raw, args.final_list, args.out_dir, not args.no_validate)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
