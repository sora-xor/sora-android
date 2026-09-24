#!/usr/bin/env python3
"""Fail closed when a packaged 64-bit Android native library is not 16 KB ready."""

import hashlib
import re
import struct
import sys
import zipfile
from pathlib import Path


PAGE_SIZE = 16_384
ABI_MACHINE = {"arm64-v8a": 183, "x86_64": 62}
LIBRARY_PATH = re.compile(r"lib/(arm64-v8a|x86_64)/[^/]+[.]so\Z")
BUNDLE_LIBRARY_PATH = re.compile(r"[^/]+/lib/(arm64-v8a|x86_64)/[^/]+[.]so\Z")
LOCAL_FILE_HEADER = struct.Struct("<IHHHHHIIIHH")
ELF_HEADER = struct.Struct("<HHIQQQIHHHHHH")
PROGRAM_HEADER = struct.Struct("<IIQQQQQQ")
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def data_offset(source, info: zipfile.ZipInfo) -> int:
    source.seek(info.header_offset)
    header = source.read(LOCAL_FILE_HEADER.size)
    if len(header) != LOCAL_FILE_HEADER.size:
        raise ValueError("truncated ZIP local file header")
    fields = LOCAL_FILE_HEADER.unpack(header)
    if fields[0] != 0x04034B50 or fields[3] != info.compress_type:
        raise ValueError("ZIP local file header disagrees with central directory")
    return info.header_offset + LOCAL_FILE_HEADER.size + fields[-2] + fields[-1]


def inspect_elf(data: bytes, abi: str) -> list[str]:
    issues = []
    if len(data) < 64 or data[:4] != b"\x7fELF" or data[4:6] != b"\x02\x01":
        return ["not a little-endian ELF64 shared library"]
    fields = ELF_HEADER.unpack_from(data, 16)
    elf_type, machine, version = fields[:3]
    ph_offset, header_size, ph_size, ph_count = fields[4], fields[7], fields[8], fields[9]
    if elf_type != 3 or machine != ABI_MACHINE[abi] or version != 1:
        issues.append("unexpected ELF type, machine, or version")
    if header_size != 64 or ph_size != PROGRAM_HEADER.size or ph_count == 0:
        return issues + ["invalid ELF program-header layout"]
    if ph_offset > len(data) or ph_count > (len(data) - ph_offset) // ph_size:
        return issues + ["ELF program headers extend beyond the library"]

    load_count = 0
    for index in range(ph_count):
        fields = PROGRAM_HEADER.unpack_from(data, ph_offset + index * ph_size)
        segment_type, _, offset, virtual_address, _, file_size, memory_size, alignment = fields
        if segment_type == PT_LOAD:
            load_count += 1
            if alignment < PAGE_SIZE:
                issues.append(f"PT_LOAD[{index}] alignment {alignment:#x} < {PAGE_SIZE:#x}")
            if alignment == 0 or alignment & (alignment - 1):
                issues.append(f"PT_LOAD[{index}] alignment {alignment:#x} is not a power of two")
            if offset % PAGE_SIZE != virtual_address % PAGE_SIZE:
                issues.append(f"PT_LOAD[{index}] offset and virtual address are not 16 KB congruent")
            if alignment >= PAGE_SIZE and offset % alignment != virtual_address % alignment:
                issues.append(f"PT_LOAD[{index}] offset and virtual address disagree modulo p_align")
            if file_size > memory_size or offset > len(data) or file_size > len(data) - offset:
                issues.append(f"PT_LOAD[{index}] has invalid file or memory bounds")
        elif segment_type == PT_GNU_RELRO:
            if (virtual_address + memory_size) % PAGE_SIZE:
                issues.append(
                    f"GNU_RELRO[{index}] end {(virtual_address + memory_size):#x} is not 16 KB aligned"
                )
    if load_count == 0:
        issues.append("ELF has no PT_LOAD segments")
    return issues


def inspect_apk(path: Path) -> tuple[int, list[str]]:
    issues = []
    library_count = 0
    with path.open("rb") as source, zipfile.ZipFile(source) as archive:
        seen = set()
        for info in sorted(archive.infolist(), key=lambda entry: entry.filename):
            match = LIBRARY_PATH.fullmatch(info.filename)
            if not match:
                continue
            library_count += 1
            name = info.filename
            if name in seen:
                issues.append(f"{name}: duplicate APK entry")
                continue
            seen.add(name)
            if info.compress_type != zipfile.ZIP_STORED:
                issues.append(f"{name}: native library is compressed")
            try:
                offset = data_offset(source, info)
            except ValueError as error:
                issues.append(f"{name}: {error}")
                continue
            if offset % PAGE_SIZE:
                issues.append(f"{name}: ZIP data offset {offset:#x} is not 16 KB aligned")
            if info.file_size > 128 * 1024 * 1024:
                issues.append(f"{name}: native library exceeds the 128 MiB inspection limit")
                continue
            try:
                data = archive.read(info)
            except (OSError, RuntimeError, zipfile.BadZipFile) as error:
                issues.append(f"{name}: cannot read library: {error}")
                continue
            issues.extend(f"{name}: {issue}" for issue in inspect_elf(data, match.group(1)))
    if library_count == 0:
        issues.append("APK contains no arm64-v8a or x86_64 native libraries")
    return library_count, issues


def read_varint(data: bytes, position: int) -> tuple[int, int]:
    value = 0
    for index in range(10):
        if position >= len(data):
            raise ValueError("truncated BundleConfig.pb varint")
        byte = data[position]
        position += 1
        value |= (byte & 0x7F) << (7 * index)
        if byte < 0x80:
            return value, position
    raise ValueError("oversized BundleConfig.pb varint")


def proto_fields(data: bytes) -> list[tuple[int, int, int | bytes]]:
    fields = []
    position = 0
    while position < len(data):
        key, position = read_varint(data, position)
        number, wire_type = key >> 3, key & 7
        if number == 0:
            raise ValueError("BundleConfig.pb has field zero")
        if wire_type == 0:
            value, position = read_varint(data, position)
        elif wire_type in (1, 2, 5):
            length = {1: 8, 5: 4}.get(wire_type)
            if wire_type == 2:
                length, position = read_varint(data, position)
            if length is None or length > len(data) - position:
                raise ValueError("BundleConfig.pb field extends past its message")
            value = data[position : position + length]
            position += length
        else:
            raise ValueError(f"BundleConfig.pb has unsupported wire type {wire_type}")
        fields.append((number, wire_type, value))
    return fields


def one_field(data: bytes, number: int, wire_type: int) -> int | bytes:
    matches = [value for field, wire, value in proto_fields(data) if field == number and wire == wire_type]
    if len(matches) != 1:
        raise ValueError(f"BundleConfig.pb requires exactly one field {number} with wire type {wire_type}")
    return matches[0]


def inspect_bundle_config(data: bytes) -> list[str]:
    try:
        optimizations = one_field(data, 2, 2)
        native_libraries = one_field(optimizations, 2, 2)
        enabled = one_field(native_libraries, 1, 0)
        alignment = one_field(native_libraries, 2, 0)
    except ValueError as error:
        return [str(error)]
    issues = []
    if enabled != 1:
        issues.append("BundleConfig.pb does not enable uncompressed native libraries")
    if alignment not in (2, 3):
        issues.append(
            f"BundleConfig.pb alignment is {alignment}, not PAGE_ALIGNMENT_16K (2) or PAGE_ALIGNMENT_64K (3)"
        )
    return issues


def inspect_aab(path: Path) -> tuple[int, list[str]]:
    issues = []
    library_count = 0
    with zipfile.ZipFile(path) as archive:
        configs = [info for info in archive.infolist() if info.filename == "BundleConfig.pb"]
        if len(configs) != 1 or configs[0].file_size > 1024 * 1024:
            issues.append("AAB requires one BundleConfig.pb of at most 1 MiB")
        else:
            issues.extend(inspect_bundle_config(archive.read(configs[0])))
        seen = set()
        for info in sorted(archive.infolist(), key=lambda entry: entry.filename):
            match = BUNDLE_LIBRARY_PATH.fullmatch(info.filename)
            if not match:
                continue
            library_count += 1
            name = info.filename
            if name in seen:
                issues.append(f"{name}: duplicate AAB entry")
                continue
            seen.add(name)
            if info.file_size > 128 * 1024 * 1024:
                issues.append(f"{name}: native library exceeds the 128 MiB inspection limit")
                continue
            try:
                data = archive.read(info)
            except (OSError, RuntimeError, zipfile.BadZipFile) as error:
                issues.append(f"{name}: cannot read library: {error}")
                continue
            issues.extend(f"{name}: {issue}" for issue in inspect_elf(data, match.group(1)))
    if library_count == 0:
        issues.append("AAB contains no arm64-v8a or x86_64 native libraries")
    return library_count, issues


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: verify-android-native-16kb.py APK_OR_AAB", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    if not path.is_file() or path.is_symlink() or path.stat().st_size == 0:
        print("artifact must be a nonempty regular file, not a symlink", file=sys.stderr)
        return 2
    try:
        if path.suffix == ".apk":
            library_count, issues = inspect_apk(path)
        elif path.suffix == ".aab":
            library_count, issues = inspect_aab(path)
        else:
            print("artifact must have .apk or .aab suffix", file=sys.stderr)
            return 2
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"cannot inspect artifact: {error}", file=sys.stderr)
        return 2
    print(f"artifact SHA-256: {sha256_file(path)}; 64-bit native libraries: {library_count}")
    if issues:
        for issue in issues:
            print(issue, file=sys.stderr)
        print(f"16 KB native artifact check failed: {len(issues)} issue(s)", file=sys.stderr)
        return 1
    print("16 KB native artifact check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
