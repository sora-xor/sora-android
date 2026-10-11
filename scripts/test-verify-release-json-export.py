#!/usr/bin/env python3
"""Exercise the batch JSON export gate with small hand-built DEX files."""

import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


CHECKER = Path(__file__).with_name("verify-release-json-export.py")
KEYS = [
    "accounts",
    "address",
    "meta",
    "name",
    "genesisHash",
    "whenCreated",
    "encoded",
    "encoding",
    "content",
    "type",
    "version",
]


def uleb128(value: int) -> bytes:
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def const_string(index: int, jumbo: bool = False) -> bytes:
    if jumbo:
        return struct.pack("<HI", 0x001B, index)
    return struct.pack("<HH", 0x001A, index)


def code_item(instructions: bytes, guarded: bool = False) -> bytes:
    """One method body. With [guarded] it also has a try block and a catch-all handler."""
    units = len(instructions) // 2
    item = struct.pack("<HHHHII", 1, 0, 0, 1 if guarded else 0, 0, units) + instructions
    if guarded:
        if units % 2:
            item += bytes(2)
        item += struct.pack("<IHH", 0, units, 1)
        # One handler list entry: no typed handler (size 0), then the catch-all address.
        item += uleb128(1) + bytes([0x00]) + uleb128(0)
    return item


def dex(strings: list[str], methods: list[bytes]) -> bytes:
    """The parts of a DEX file the checker reads: header, string table, code items, map."""
    header_size = 0x70
    table_offset = header_size
    data = bytearray(bytes(header_size) + bytes(4 * len(strings)))
    offsets = []
    for text in strings:
        offsets.append(len(data))
        data += uleb128(len(text)) + text.encode("utf-8") + b"\x00"
    for index, offset in enumerate(offsets):
        struct.pack_into("<I", data, table_offset + 4 * index, offset)
    data += bytes(-len(data) % 4)
    code_offset = len(data)
    for method in methods:
        data += bytes(-len(data) % 4)
        data += method
    data += bytes(-len(data) % 4)
    map_offset = len(data)
    data += struct.pack("<I", 1) + struct.pack("<HHII", 0x2001, 0, len(methods), code_offset)
    data[0:8] = b"dex\n039\x00"
    struct.pack_into("<I", data, 0x34, map_offset)
    struct.pack_into("<II", data, 0x38, len(strings), table_offset)
    return bytes(data)


def load(indexes, jumbo: bool = False) -> bytes:
    # return-void closes the method, as in real code.
    return b"".join(const_string(index, jumbo) for index in indexes) + struct.pack("<H", 0x000E)


class BatchJsonExportGateTest(unittest.TestCase):
    def check(self, files: dict[str, bytes]):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "candidate.apk"
            with zipfile.ZipFile(artifact, "w") as archive:
                for name, content in files.items():
                    archive.writestr(name, content)
            return subprocess.run(
                [sys.executable, str(CHECKER), str(artifact)],
                capture_output=True,
                text=True,
                check=False,
            )

    def test_one_method_that_writes_every_key_passes(self):
        strings = ["other"] + KEYS
        writer = code_item(load(range(1, len(strings))))
        result = self.check({"classes.dex": dex(strings, [code_item(load([0])), writer])})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("written key by key (classes.dex)", result.stdout)

    def test_the_writer_is_found_in_a_later_dex_file_and_after_a_try_block(self):
        strings = KEYS + ["other"]
        guarded = code_item(load([len(KEYS)]), guarded=True)
        odd = code_item(load([len(KEYS)]) + struct.pack("<H", 0x000E), guarded=True)
        writer = code_item(load(range(len(KEYS))))
        files = {
            "classes.dex": dex(["other"], [code_item(load([0]))]),
            "classes2.dex": dex(strings, [guarded, odd, writer]),
        }
        result = self.check(files)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("classes2.dex", result.stdout)

    def test_jumbo_string_constants_count(self):
        writer = code_item(load(range(len(KEYS)), jumbo=True))
        result = self.check({"classes.dex": dex(KEYS, [writer])})
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_a_bundle_is_read_from_its_module_dex_folder(self):
        writer = code_item(load(range(len(KEYS))))
        result = self.check({"base/dex/classes.dex": dex(KEYS, [writer])})
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_keys_spread_over_two_methods_fail(self):
        first = code_item(load(range(0, 5)))
        second = code_item(load(range(5, len(KEYS))))
        result = self.check({"classes.dex": dex(KEYS, [first, second])})
        self.assertEqual(result.returncode, 1)
        self.assertIn("no method writes every key", result.stderr)

    def test_a_build_without_one_of_the_key_names_fails(self):
        # What a minified build that serialises a model class looks like: the field names are
        # gone from the string table.
        strings = [key for key in KEYS if key != "whenCreated"]
        writer = code_item(load(range(len(strings))))
        result = self.check({"classes.dex": dex(strings, [writer])})
        self.assertEqual(result.returncode, 1)
        self.assertIn("{}", result.stderr)

    def test_an_artifact_without_dex_files_fails(self):
        result = self.check({"resources.arsc": b"none"})
        self.assertEqual(result.returncode, 1)
        self.assertIn("no classes*.dex", result.stderr)

    def test_a_damaged_dex_file_fails_closed(self):
        result = self.check({"classes.dex": b"dex\n039\x00" + bytes(16)})
        self.assertEqual(result.returncode, 1)
        self.assertIn("cannot read the artifact", result.stderr)

    def test_no_argument_is_a_usage_error(self):
        result = subprocess.run(
            [sys.executable, str(CHECKER)], capture_output=True, text=True, check=False
        )
        self.assertEqual(result.returncode, 2)


if __name__ == "__main__":
    unittest.main()
