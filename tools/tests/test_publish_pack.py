import json
import sys
import tempfile
import threading
import unittest
import zipfile
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
TOOLS = ROOT / "tools"
FIXTURES = ROOT / "shared" / "fixtures"
sys.path.insert(0, str(TOOLS))

import publish_pack  # noqa: E402
from canonical import combined_digest  # noqa: E402
from validate_schema import (  # noqa: E402
    MAX_PACK_ENTRY_BYTES,
    MAX_PACK_EXPANDED_BYTES,
    MAX_PACK_INPUT_BYTES,
    validate_channel_manifest,
    validate_content_data,
    validate_pack_zip,
)


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def make_pack(directory, mutate=None, mutate_examples=None, mutate_manifest=None, extra_file=None, digest=None):
    idioms = json.loads((FIXTURES / "idioms.sample.json").read_text(encoding="utf-8"))
    examples = json.loads((FIXTURES / "examples.sample.json").read_text(encoding="utf-8"))
    if mutate is not None:
        mutate(idioms)
    if mutate_examples is not None:
        mutate_examples(examples)
    manifest = {
        "schemaVersion": 1,
        "packVersion": "v1.0.0",
        "builtAt": "2026-11-17T00:00:00Z",
        "counts": {"idioms": len(idioms), "examples": len(examples)},
        "sha256": digest or combined_digest(idioms, examples),
    }
    if mutate_manifest is not None:
        mutate_manifest(manifest)
    path = Path(directory) / "pack-v1.0.0.zip"
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("idioms.json", json_bytes(idioms))
        archive.writestr("examples.json", json_bytes(examples))
        archive.writestr("manifest.json", json_bytes(manifest))
        if extra_file is not None:
            archive.writestr(extra_file, b"unexpected")
    return path


class PublishPackTests(unittest.TestCase):
    def test_writes_pack_before_latest(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            pack = make_pack(root / "source")
            dest = root / "channel"
            events = []
            original_copy = publish_pack.atomic_copy
            original_write = publish_pack.atomic_write

            def copy(source, target):
                events.append("pack")
                original_copy(source, target)

            def write(target, data):
                events.append("latest")
                original_write(target, data)

            with mock.patch.object(publish_pack, "atomic_copy", side_effect=copy), mock.patch.object(
                publish_pack, "atomic_write", side_effect=write
            ):
                result = publish_pack.publish_pack(pack, dest)

            self.assertEqual(result, 0)
            self.assertEqual(events, ["pack", "latest"])
            self.assertTrue((dest / "pack-v1.0.0.zip").is_file())
            self.assertTrue((dest / "latest.json").is_file())

    def test_latest_matches_manifest_and_actual_zip_size(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            pack = make_pack(root / "source")
            dest = root / "channel"
            self.assertEqual(publish_pack.publish_pack(pack, dest), 0)

            latest = json.loads((dest / "latest.json").read_text(encoding="utf-8"))
            with zipfile.ZipFile(pack) as archive:
                manifest = json.loads(archive.read("manifest.json"))
            for field in ("schemaVersion", "packVersion", "builtAt", "counts", "sha256"):
                self.assertEqual(latest[field], manifest[field])
            self.assertEqual(latest["packFile"], "pack-v1.0.0.zip")
            self.assertEqual(latest["sizeBytes"], pack.stat().st_size)
            self.assertEqual((dest / "pack-v1.0.0.zip").read_bytes(), pack.read_bytes())

    def test_same_version_with_different_bytes_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = make_pack(root / "first")
            dest = root / "channel"
            self.assertEqual(publish_pack.publish_pack(first, dest), 0)
            latest_before = (dest / "latest.json").read_bytes()

            second = make_pack(root / "second", mutate=lambda idioms: idioms[0].__setitem__("word", "变动成语"))
            self.assertNotEqual(publish_pack.publish_pack(second, dest), 0)
            self.assertNotEqual(publish_pack.publish_pack(second, dest, dry_run=True), 0)
            self.assertEqual((dest / "latest.json").read_bytes(), latest_before)
            self.assertEqual((dest / "pack-v1.0.0.zip").read_bytes(), first.read_bytes())

    def test_concurrent_same_version_publish_cannot_overwrite_pack(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = make_pack(root / "first")
            second = make_pack(root / "second", mutate=lambda idioms: idioms[0].__setitem__("word", "变动成语"))
            dest = root / "channel"
            barrier = threading.Barrier(2)
            original_copy = publish_pack.atomic_copy
            results = {}

            def synchronized_copy(source, target):
                barrier.wait(timeout=5)
                original_copy(source, target)

            def run(label, pack):
                results[label] = publish_pack.publish_pack(pack, dest)

            with mock.patch.object(publish_pack, "atomic_copy", side_effect=synchronized_copy):
                threads = [
                    threading.Thread(target=run, args=("first", first)),
                    threading.Thread(target=run, args=("second", second)),
                ]
                for thread in threads:
                    thread.start()
                for thread in threads:
                    thread.join(timeout=10)
                self.assertTrue(all(not thread.is_alive() for thread in threads))

            self.assertEqual(sorted(results.values()), [0, 1])
            winner = first if results["first"] == 0 else second
            self.assertEqual((dest / "pack-v1.0.0.zip").read_bytes(), winner.read_bytes())
            with zipfile.ZipFile(winner) as archive:
                manifest = json.loads(archive.read("manifest.json"))
            latest = json.loads((dest / "latest.json").read_text(encoding="utf-8"))
            self.assertEqual(latest["sha256"], manifest["sha256"])
            self.assertEqual(list(dest.glob("*.tmp")), [])

    def test_absolute_pack_url_fixture_is_rejected(self):
        invalid = json.loads((FIXTURES / "channel" / "latest.invalid-pack-file.json").read_text(encoding="utf-8"))
        errors = validate_channel_manifest(invalid)
        self.assertTrue(any("packFile" in message for message in errors))

    def test_extra_pack_member_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack = make_pack(temporary, extra_file="unexpected.txt")
            _, _, _, errors = validate_pack_zip(pack)
            self.assertTrue(any("只能包含" in message for message in errors))

    def test_digest_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack = make_pack(temporary, digest="0" * 64)
            _, _, _, errors = validate_pack_zip(pack)
            self.assertTrue(any("sha256" in message for message in errors))

    def test_broken_idiom_reference_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack = make_pack(temporary, mutate_examples=lambda examples: examples[0]["idiomIds"].append("missing-id"))
            _, _, _, errors = validate_pack_zip(pack)
            self.assertTrue(any("不存在的成语 id" in message for message in errors))

    def test_oversized_zip_input_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack = Path(temporary) / "pack-v1.0.0.zip"
            pack.write_bytes(b"0" * (MAX_PACK_INPUT_BYTES + 1))
            _, _, _, errors = validate_pack_zip(pack)
            self.assertTrue(any("输入上限" in message for message in errors))

    def test_oversized_zip_entry_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack = Path(temporary) / "pack-v1.0.0.zip"
            with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("idioms.json", b"0" * (MAX_PACK_ENTRY_BYTES + 1))
                archive.writestr("examples.json", b"[]")
                archive.writestr("manifest.json", b"{}")
            _, _, _, errors = validate_pack_zip(pack)
            self.assertTrue(any("展开上限" in message for message in errors))

    def test_excessive_total_zip_expansion_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack = Path(temporary) / "pack-v1.0.0.zip"
            entry_size = MAX_PACK_ENTRY_BYTES
            with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr("idioms.json", b"0" * entry_size)
                archive.writestr("examples.json", b"0" * entry_size)
                archive.writestr("manifest.json", b"0")
            self.assertGreater(entry_size * 2 + 1, MAX_PACK_EXPANDED_BYTES)
            _, _, _, errors = validate_pack_zip(pack)
            self.assertTrue(any("总展开上限" in message for message in errors))

    def test_manifest_schema_violation_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack = make_pack(temporary, mutate_manifest=lambda manifest: manifest.__setitem__("schemaVersion", 0))
            manifest, _, _, errors = validate_pack_zip(pack)
            self.assertIsInstance(manifest, dict)
            self.assertTrue(any("schemaVersion" in message for message in errors))

    def test_unsupported_manifest_schema_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack = make_pack(temporary, mutate_manifest=lambda manifest: manifest.__setitem__("schemaVersion", 2))
            _, _, _, errors = validate_pack_zip(pack)
            self.assertTrue(any("schemaVersion 仅支持 1" in message for message in errors))

    def test_channel_schema_and_pack_file_must_match_supported_version(self):
        channel = json.loads((FIXTURES / "channel" / "latest.correct.json").read_text(encoding="utf-8"))
        channel["schemaVersion"] = 2
        channel["packFile"] = "pack-v9.9.9.zip"
        errors = validate_channel_manifest(channel)
        self.assertTrue(any("schemaVersion 仅支持 1" in message for message in errors))
        self.assertTrue(any("packFile 应为 pack-v1.0.0.zip" in message for message in errors))

    def test_manifest_count_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack = make_pack(temporary, mutate_manifest=lambda manifest: manifest["counts"].__setitem__("idioms", 999))
            _, _, _, errors = validate_pack_zip(pack)
            self.assertTrue(any("counts 与实际条数不符" in message for message in errors))

    def test_malformed_id_is_rejected_without_exception(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack = make_pack(temporary, mutate=lambda idioms: idioms[0].__setitem__("id", ["invalid-id"]))
            _, _, _, errors = validate_pack_zip(pack)
            self.assertTrue(any("id 缺失、类型错误或重复" in message for message in errors))

    def test_non_list_idiom_ids_is_rejected_without_exception(self):
        with tempfile.TemporaryDirectory() as temporary:
            pack = make_pack(temporary, mutate_examples=lambda examples: examples[0].__setitem__("idiomIds", "invalid"))
            _, _, _, errors = validate_pack_zip(pack)
            self.assertTrue(any("idiomIds" in message and "类型应为" in message for message in errors))

    def test_pack_file_with_trailing_newline_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            invalid = json.loads((FIXTURES / "channel" / "latest.correct.json").read_text(encoding="utf-8"))
            invalid["packFile"] += "\n"
            errors = validate_channel_manifest(invalid)
            self.assertTrue(any("packFile" in message and "不匹配" in message for message in errors))

    def test_malformed_content_ids_are_rejected_without_exception(self):
        idioms = json.loads((FIXTURES / "idioms.sample.json").read_text(encoding="utf-8"))
        examples = json.loads((FIXTURES / "examples.sample.json").read_text(encoding="utf-8"))
        idioms[0]["id"] = ["invalid-id"]
        examples[0]["idiomIds"] = "invalid"
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            idioms_path = root / "idioms.json"
            examples_path = root / "examples.json"
            idioms_path.write_bytes(json_bytes(idioms))
            examples_path.write_bytes(json_bytes(examples))
            with mock.patch("builtins.print"):
                self.assertEqual(validate_content_data(idioms_path, examples_path), 1)

    def test_dry_run_does_not_create_destination(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            pack = make_pack(root / "source")
            dest = root / "channel"
            self.assertEqual(publish_pack.publish_pack(pack, dest, dry_run=True), 0)
            self.assertFalse(dest.exists())


if __name__ == "__main__":
    unittest.main()
