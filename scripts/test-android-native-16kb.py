#!/usr/bin/env python3
"""Exercise the APK gate with aligned and independently broken ELF/ZIP fixtures."""

import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


CHECKER = Path(__file__).with_name("verify-android-native-16kb.py")


def native_library(machine: int, load_alignment: int, relro_end: int, load_offset=0) -> bytes:
    ident = b"\x7fELF\x02\x01\x01" + bytes(9)
    header = struct.pack("<HHIQQQIHHHHHH", 3, machine, 1, 0, 64, 0, 0, 64, 56, 2, 0, 0, 0)
    load = struct.pack("<IIQQQQQQ", 1, 5, load_offset, 0, 0, 176, 176, load_alignment)
    relro = struct.pack("<IIQQQQQQ", 0x6474E552, 4, 0, 0x3000, 0, 0, relro_end - 0x3000, 1)
    image = ident + header + load + relro
    return image + bytes(max(0, load_offset + 176 - len(image)))


def make_apk(path: Path, *, load_alignment=0x4000, load_offset=0, relro_end=0x4000, align_zip=True) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        for abi, machine in (("arm64-v8a", 183), ("x86_64", 62)):
            name = f"lib/{abi}/libfixture.so"
            info = zipfile.ZipInfo(name)
            info.compress_type = zipfile.ZIP_STORED
            if align_zip:
                padding = (-archive.fp.tell() - 30 - len(name)) % 0x4000
                assert padding >= 4
                info.extra = struct.pack("<HH", 0xCAFE, padding - 4) + bytes(padding - 4)
            archive.writestr(info, native_library(machine, load_alignment, relro_end, load_offset))


def make_aab(path: Path, *, alignment=2, load_alignment=0x4000, include_config=True) -> None:
    native_config = bytes((0x08, 0x01, 0x10, alignment))
    optimizations = bytes((0x12, len(native_config))) + native_config
    config = bytes((0x12, len(optimizations))) + optimizations
    with zipfile.ZipFile(path, "w") as archive:
        if include_config:
            archive.writestr("BundleConfig.pb", config)
        for abi, machine in (("arm64-v8a", 183), ("x86_64", 62)):
            archive.writestr(
                f"base/lib/{abi}/libfixture.so",
                native_library(machine, load_alignment, 0x4000),
            )


class NativePageGateTest(unittest.TestCase):
    def check(self, **fixture_options):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "candidate.apk"
            make_apk(apk, **fixture_options)
            return subprocess.run(
                [sys.executable, str(CHECKER), str(apk)],
                capture_output=True,
                text=True,
                check=False,
            )

    def check_bundle(self, **fixture_options):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory) / "candidate.aab"
            make_aab(bundle, **fixture_options)
            return subprocess.run(
                [sys.executable, str(CHECKER), str(bundle)],
                capture_output=True,
                text=True,
                check=False,
            )

    def test_aligned_apk_passes(self):
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("64-bit native libraries: 2", result.stdout)

    def test_four_kilobyte_load_alignment_fails(self):
        result = self.check(load_alignment=0x1000)
        self.assertEqual(result.returncode, 1)
        self.assertIn("PT_LOAD", result.stderr)

    def test_non_power_of_two_load_alignment_fails(self):
        result = self.check(load_alignment=0x4001)
        self.assertEqual(result.returncode, 1)
        self.assertIn("not a power of two", result.stderr)

    def test_load_offset_must_match_full_alignment(self):
        result = self.check(load_alignment=0x8000, load_offset=0x4000)
        self.assertEqual(result.returncode, 1)
        self.assertIn("modulo p_align", result.stderr)

    def test_zip_data_alignment_fails(self):
        result = self.check(align_zip=False)
        self.assertEqual(result.returncode, 1)
        self.assertIn("ZIP data offset", result.stderr)

    def test_relro_end_alignment_fails(self):
        result = self.check(relro_end=0x5000)
        self.assertEqual(result.returncode, 1)
        self.assertIn("GNU_RELRO", result.stderr)

    def test_aligned_bundle_passes(self):
        result = self.check_bundle()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("64-bit native libraries: 2", result.stdout)

    def test_sixty_four_kilobyte_bundle_alignment_passes(self):
        result = self.check_bundle(alignment=3)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_four_kilobyte_bundle_config_fails(self):
        result = self.check_bundle(alignment=1)
        self.assertEqual(result.returncode, 1)
        self.assertIn("PAGE_ALIGNMENT_16K", result.stderr)

    def test_missing_bundle_config_fails(self):
        result = self.check_bundle(include_config=False)
        self.assertEqual(result.returncode, 1)
        self.assertIn("BundleConfig.pb", result.stderr)

    def test_bundle_elf_failure_is_detected(self):
        result = self.check_bundle(load_alignment=0x1000)
        self.assertEqual(result.returncode, 1)
        self.assertIn("PT_LOAD", result.stderr)


if __name__ == "__main__":
    unittest.main()
