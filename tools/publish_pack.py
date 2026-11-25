#!/usr/bin/env python3
"""将已校验的题库包发布到本地通道目录；云端上传按 2026-11-24 拍板后置。"""
import argparse
import hashlib
import json
import os
import shutil
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE))
from validate_schema import validate_channel_manifest, validate_pack_zip  # noqa: E402


def fail(message, code=1):
    print("FAIL: " + message)
    return code


def dump_json(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def file_sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_write(path, data):
    target = Path(path)
    descriptor, temporary_name = tempfile.mkstemp(prefix=target.name + ".", suffix=".tmp", dir=target.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def atomic_copy(source, target):
    destination = Path(target)
    descriptor, temporary_name = tempfile.mkstemp(prefix=destination.name + ".", suffix=".tmp", dir=destination.parent)
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        shutil.copyfile(source, temporary)
        try:
            os.link(temporary, destination)
        except FileExistsError:
            if file_sha256(destination) != file_sha256(source):
                raise
    finally:
        temporary.unlink(missing_ok=True)


def publish_pack(pack_path, dest_dir, dry_run=False):
    pack_path = Path(pack_path)
    dest_dir = Path(dest_dir)
    manifest, _, _, validation_errors = validate_pack_zip(pack_path)
    if validation_errors:
        return fail("题库包校验失败：\n  - " + "\n  - ".join(validation_errors))
    if not isinstance(manifest, dict):
        return fail("题库包缺少 manifest.json")

    pack_version = manifest.get("packVersion")
    pack_file = "pack-%s.zip" % pack_version
    if pack_path.name != pack_file:
        return fail("文件名 %s 与包内版本 %s 不一致，应为 %s" % (pack_path.name, pack_version, pack_file))

    latest = {
        "schemaVersion": manifest["schemaVersion"],
        "packVersion": pack_version,
        "builtAt": manifest["builtAt"],
        "counts": manifest["counts"],
        "sha256": manifest["sha256"],
        "sizeBytes": pack_path.stat().st_size,
        "packFile": pack_file,
    }
    channel_errors = validate_channel_manifest(latest)
    if channel_errors:
        return fail("通道清单校验失败：\n  - " + "\n  - ".join(channel_errors))

    target_pack = dest_dir / pack_file
    target_latest = dest_dir / "latest.json"
    if target_pack.exists() and file_sha256(target_pack) != file_sha256(pack_path):
        return fail("目标目录已有同版本但内容不同的 %s，拒绝覆盖" % pack_file)
    if dry_run:
        print("dry-run：将先写 %s，再原子更新 %s" % (target_pack, target_latest))
        print(dump_json(latest).decode("utf-8"), end="")
        return 0

    dest_dir.mkdir(parents=True, exist_ok=True)
    if not target_pack.exists():
        try:
            atomic_copy(pack_path, target_pack)
        except FileExistsError:
            return fail("目标目录已有同版本但内容不同的 %s，拒绝覆盖" % pack_file)

    atomic_write(target_latest, dump_json(latest))
    print("本地发布完成：%s" % target_pack)
    print("通道清单已更新：%s" % target_latest)
    return 0


def main(argv):
    parser = argparse.ArgumentParser(description="发布题库包到本地通道目录（先 zip 后 latest.json）")
    parser.add_argument("pack", help="build_pack.py 生成的 pack-vX.Y.Z.zip")
    parser.add_argument("--dest", default=str(ROOT / "content" / "dist" / "channel"), help="本地通道目录")
    parser.add_argument("--dry-run", action="store_true", help="只校验并打印将写入的 latest.json")
    args = parser.parse_args(argv)
    try:
        return publish_pack(args.pack, args.dest, args.dry_run)
    except OSError as error:
        return fail("本地发布失败：%s" % error)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
