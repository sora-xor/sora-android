#!/usr/bin/env python3
"""Repack the pinned xcrypto AAR with only the two rebuilt 64-bit JNI libraries."""

import argparse
import hashlib
from pathlib import Path
from zipfile import ZipFile


ORIGINAL_SHA256 = "a701705120918cc3c66d7217590035c9d385466e1b00836191c917845e9ff56b"
OUTPUT_SHA256 = "bb2cbb157c36430a7843e8db6900fec1ce36cacd966ff8fe00c6eab524f853ef"
REPLACEMENT_SHA256 = {
    "jni/arm64-v8a/libsr25519java_1.so": "74744f6eb2d7efefaf0d985d316b7db418997cea6fb5f43dc4bba85b5fb2cde2",
    "jni/x86_64/libsr25519java_1.so": "1daa9af93389dc4c2ffb48d54eccdc71f207b415e913c8ec9d26a2ed047843b4",
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def repack(original: Path, arm64: Path, x86_64: Path, output: Path) -> None:
    if output.resolve() in {original.resolve(), arm64.resolve(), x86_64.resolve()}:
        raise ValueError("output must be separate from every input")
    if sha256(original.read_bytes()) != ORIGINAL_SHA256:
        raise ValueError("original AAR digest mismatch")
    replacements = {
        "jni/arm64-v8a/libsr25519java_1.so": arm64.read_bytes(),
        "jni/x86_64/libsr25519java_1.so": x86_64.read_bytes(),
    }
    for name, data in replacements.items():
        if sha256(data) != REPLACEMENT_SHA256[name]:
            raise ValueError(f"rebuilt library digest mismatch: {name}")
    with ZipFile(original, "r") as source, ZipFile(output, "w") as candidate:
        entries = source.infolist()
        names = [info.filename for info in entries]
        if len(names) != len(set(names)) or not set(replacements).issubset(names):
            raise ValueError("original AAR has duplicate or missing entries")
        for info in entries:
            candidate.writestr(info, replacements.get(info.filename, source.read(info)))
    with ZipFile(original, "r") as source, ZipFile(output, "r") as candidate:
        if [entry.filename for entry in source.infolist()] != [
            entry.filename for entry in candidate.infolist()
        ]:
            raise ValueError("repacked AAR changed entry order or names")
        changed = {
            name for name in source.namelist() if source.read(name) != candidate.read(name)
        }
        if changed != set(replacements):
            raise ValueError(f"unexpected changed AAR entries: {sorted(changed)}")
    if sha256(output.read_bytes()) != OUTPUT_SHA256:
        raise ValueError("repacked AAR digest mismatch")
    print(f"xcrypto 16 KB AAR: {OUTPUT_SHA256}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("arm64", type=Path)
    parser.add_argument("x86_64", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    repack(args.original, args.arm64, args.x86_64, args.output)
