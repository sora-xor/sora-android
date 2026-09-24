#!/usr/bin/env python3
"""Replace exactly two 64-bit JNI members of the published TensorFlow Lite 2.12.0 AAR."""

import argparse
import hashlib
from pathlib import Path
from zipfile import ZipFile


ORIGINAL_SHA256 = "002371fefe277e93f1421206062823d04daceb6c9e4e824cb543eab2d3a00c91"
OUTPUT_SHA256 = "4da11c611a69427c4b6742f66fdda7d276d5267b2f7a08528e1b268c4e65ccbd"
REPLACEMENT_SHA256 = {
    "jni/arm64-v8a/libtensorflowlite_jni.so": "c9099070c3034d21cbcda735cb8e39f83c34fa5e3ab079eb8d6d189e5c5fdae7",
    "jni/x86_64/libtensorflowlite_jni.so": "a95289cb76794d714a91ce3fa240b92f88b8a356293c7bcdc9ca503cfcc98bef",
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def repack(original: Path, arm64: Path, x86_64: Path, output: Path) -> None:
    paths = (original, arm64, x86_64, output)
    if len({path.resolve() for path in paths}) != len(paths):
        raise ValueError("input and output paths must be distinct")
    if sha256(original.read_bytes()) != ORIGINAL_SHA256:
        raise ValueError("published AAR digest mismatch")
    replacements = {
        "jni/arm64-v8a/libtensorflowlite_jni.so": arm64.read_bytes(),
        "jni/x86_64/libtensorflowlite_jni.so": x86_64.read_bytes(),
    }
    for name, contents in replacements.items():
        if sha256(contents) != REPLACEMENT_SHA256[name]:
            raise ValueError(f"rebuilt JNI digest mismatch: {name}")
    with ZipFile(original) as source, ZipFile(output, "w") as candidate:
        entries = source.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)) or not set(replacements).issubset(names):
            raise ValueError("published AAR has duplicate or missing members")
        for entry in entries:
            candidate.writestr(entry, replacements.get(entry.filename, source.read(entry)))
    with ZipFile(original) as source, ZipFile(output) as candidate:
        if source.namelist() != candidate.namelist():
            raise ValueError("repacked AAR changed member names or order")
        changed = {
            name for name in source.namelist() if source.read(name) != candidate.read(name)
        }
        if changed != set(replacements):
            raise ValueError(f"unexpected changed AAR members: {sorted(changed)}")
    if sha256(output.read_bytes()) != OUTPUT_SHA256:
        raise ValueError("repacked AAR digest mismatch")
    print(f"TensorFlow Lite 2.12.0 16 KB AAR: {OUTPUT_SHA256}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("arm64", type=Path)
    parser.add_argument("x86_64", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    repack(args.original, args.arm64, args.x86_64, args.output)
