#!/usr/bin/env python3
"""Replace only the 64-bit JNI entries in the pinned RootBeer 0.1.0 AAR."""
import argparse
import hashlib
from pathlib import Path
from zipfile import ZipFile

ORIGINAL_SHA256 = "6c4d2e20148111a550aa3923c24e9b1360f300f1454117235a4d435e45928ee7"
OUTPUT_SHA256 = "d071cebd41c71d920ebfc445adf237ad3c560aaa3a074534af0393560ad85953"
REPLACEMENT_SHA256 = {
    "jni/arm64-v8a/libtoolChecker.so": "656be4fef61da1f1fe9740beac8c11297b3337f9028dceb87bdbbf7a65414cfa",
    "jni/x86_64/libtoolChecker.so": "15b820b18ac414d90dff74e225743f05b3ba793bd6ad9084b7c1a7bd5e52eee5",
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def repack(original: Path, arm64: Path, x86_64: Path, output: Path) -> None:
    if output.resolve() in {original.resolve(), arm64.resolve(), x86_64.resolve()}:
        raise ValueError("output must be separate from every input")
    if sha256(original.read_bytes()) != ORIGINAL_SHA256:
        raise ValueError("original AAR digest mismatch")
    replacements = {
        "jni/arm64-v8a/libtoolChecker.so": arm64.read_bytes(),
        "jni/x86_64/libtoolChecker.so": x86_64.read_bytes(),
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
    print(f"RootBeer 0.1.0 16 KB AAR: {OUTPUT_SHA256}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("arm64", type=Path)
    parser.add_argument("x86_64", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    repack(args.original, args.arm64, args.x86_64, args.output)
