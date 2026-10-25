#!/usr/bin/env python3
"""Repack the pinned xcrypto AAR with only the two rebuilt 64-bit JNI libraries."""

import argparse
import hashlib
from pathlib import Path
from zipfile import ZipFile


ORIGINAL_SHA256 = "dee65fa2e0aef012a9ac591638da02bf89efc7f77a5969e2e0d2bbb7fdeebdf1"
OUTPUT_SHA256 = "021e37c8b6f1b9c49a80a2b81af3094de4519b06f75a2fc82f57161630550f7f"
REPLACEMENT_SHA256 = {
    "jni/arm64-v8a/libsr25519java_1.so": "ef74ddc0640a676ad3b9b2b96d066b9001f72281a471cbdefd412f79020e9a45",
    "jni/x86_64/libsr25519java_1.so": "bcc7d06342cd10496492197daa4a47752c161ad92f4cf0b1ffc56e13e2205649",
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
