#!/usr/bin/env python3
"""Fail closed when a minified APK or AAB cannot write the several-account JSON backup file.

The batch export ("Export JSON" for several accounts) is the backup of accounts that have no
matching recovery phrase. It must be written key by key (JsonAccountsEncoder.generate). When it
was written by handing a model class to Gson, R8 removed the fields that only reflection read
and the release build wrote the file as "{}".

Class and field names do not survive minification, string constants do. So this check reads the
DEX code of the artifact and requires one method that loads every key of the batch file as a
string constant. A build that goes back to a reflective model has no such method and fails.

Usage: verify-release-json-export.py APK_OR_AAB...
"""

import re
import struct
import sys
import zipfile
from pathlib import Path


# Every key JsonAccountsEncoder.generate(List) writes, as polkadot.js and the import read them.
BATCH_EXPORT_KEYS = (
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
)
DEX_PATH = re.compile(r"(?:[^/]+/dex/)?classes\d*[.]dex\Z")
TYPE_CODE_ITEM = 0x2001
CONST_STRING = 0x1A
CONST_STRING_JUMBO = 0x1B


def uleb128(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, offset
        shift += 7
        if shift > 35:
            raise ValueError("malformed LEB128 value")


def sleb128(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        shift += 7
        if byte < 0x80:
            if byte & 0x40:
                value -= 1 << shift
            return value, offset
        if shift > 35:
            raise ValueError("malformed LEB128 value")


def string_indexes(data: bytes, wanted: tuple[str, ...]) -> dict[str, int]:
    """The string-table index of each wanted string that this DEX file holds."""
    count, table = struct.unpack_from("<II", data, 0x38)
    encoded = {text.encode("utf-8"): text for text in wanted}
    found = {}
    for index in range(count):
        (offset,) = struct.unpack_from("<I", data, table + 4 * index)
        _, start = uleb128(data, offset)
        end = data.index(b"\x00", start)
        text = encoded.get(data[start:end])
        if text is not None:
            found[text] = index
    return found


def code_items(data: bytes):
    """The instruction arrays (16-bit code units) of every method body in this DEX file."""
    (map_offset,) = struct.unpack_from("<I", data, 0x34)
    (entries,) = struct.unpack_from("<I", data, map_offset)
    offset = None
    count = 0
    for entry in range(entries):
        kind, _, size, start = struct.unpack_from("<HHII", data, map_offset + 4 + 12 * entry)
        if kind == TYPE_CODE_ITEM:
            offset, count = start, size
    if offset is None:
        return
    for _ in range(count):
        offset = (offset + 3) & ~3
        _, _, _, tries, _, units = struct.unpack_from("<HHHHII", data, offset)
        start = offset + 16
        end = start + 2 * units
        yield data[start:end]
        offset = end
        if tries:
            if units % 2:
                offset += 2
            offset += 8 * tries
            handlers, offset = uleb128(data, offset)
            for _ in range(handlers):
                # Zero or less: the typed handlers are followed by a catch-all address.
                size, offset = sleb128(data, offset)
                for _ in range(abs(size)):
                    _, offset = uleb128(data, offset)
                    _, offset = uleb128(data, offset)
                if size <= 0:
                    _, offset = uleb128(data, offset)


def loaded_strings(code: bytes) -> set[int]:
    """Every string index a const-string instruction could load at any position of [code].

    Positions are not decoded as an instruction stream, so this is a superset. Requiring eleven
    particular indexes in one method leaves no room for an accidental match.
    """
    found = set()
    units = len(code) // 2
    for position in range(units - 1):
        opcode = code[2 * position]
        if opcode == CONST_STRING:
            found.add(struct.unpack_from("<H", code, 2 * position + 2)[0])
        elif opcode == CONST_STRING_JUMBO and position + 2 < units:
            found.add(struct.unpack_from("<I", code, 2 * position + 2)[0])
    return found


def writes_batch_export(data: bytes) -> bool:
    if data[:4] != b"dex\n":
        raise ValueError("not a DEX file")
    indexes = string_indexes(data, BATCH_EXPORT_KEYS)
    if len(indexes) != len(BATCH_EXPORT_KEYS):
        return False
    wanted = set(indexes.values())
    return any(wanted <= loaded_strings(code) for code in code_items(data))


def inspect(path: Path) -> list[str]:
    try:
        with zipfile.ZipFile(path) as archive:
            names = sorted(name for name in archive.namelist() if DEX_PATH.fullmatch(name))
            if not names:
                return ["no classes*.dex in the artifact"]
            for name in names:
                if writes_batch_export(archive.read(name)):
                    print(f"{path}: batch JSON export is written key by key ({name})")
                    return []
    except (OSError, ValueError, IndexError, struct.error, zipfile.BadZipFile) as error:
        return [f"cannot read the artifact: {error}"]
    return [
        "no method writes every key of the several-account JSON backup file "
        "(" + ", ".join(BATCH_EXPORT_KEYS) + "). A minified build that serialises a model "
        "class instead writes the file as {}. See JsonAccountsEncoder.generate."
    ]


def main(arguments: list[str]) -> int:
    if not arguments:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    failed = False
    for argument in arguments:
        for issue in inspect(Path(argument)):
            failed = True
            print(f"{argument}: {issue}", file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
