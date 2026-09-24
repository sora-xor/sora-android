#!/usr/bin/env python3
"""Replace only the 64-bit libsodium entries in Lazysodium 5.0.2."""

import argparse
import hashlib
from pathlib import Path
from zipfile import ZipFile


ORIGINAL = "e38503013e03a3623bd9da01a0fbbf644a87947a35dbeb4df7b35605b71534ad"
OUTPUT = "02eb05b96f8236b646f7f642c8e424cdb7f6ad6451d6b3c9eaec0e8c8138f989"
REPLACEMENTS = {
    "jni/arm64-v8a/libsodium.so": "917eb22551c9792b0b5316a8dae614d7a68c2232e28075ae73afeeac94a8e3cb",
    "jni/x86_64/libsodium.so": "8e7278efed584d83f99b1a374443fdbc2612c60b0ff17b8aa5a5152d1a909b26",
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main(original, arm64, x86_64, output):
    if len({original.resolve(), arm64.resolve(), x86_64.resolve(), output.resolve()}) != 4:
        raise ValueError("input and output paths must be distinct")
    if sha(original.read_bytes()) != ORIGINAL:
        raise ValueError("original AAR checksum mismatch")
    replacements = {
        "jni/arm64-v8a/libsodium.so": arm64.read_bytes(),
        "jni/x86_64/libsodium.so": x86_64.read_bytes(),
    }
    for name, data in replacements.items():
        if sha(data) != REPLACEMENTS[name]:
            raise ValueError(f"replacement checksum mismatch: {name}")
    with ZipFile(original) as source, ZipFile(output, "w") as candidate:
        infos = source.infolist()
        names = [info.filename for info in infos]
        if len(names) != len(set(names)) or not set(replacements).issubset(names):
            raise ValueError("missing or duplicate AAR entries")
        for info in infos:
            candidate.writestr(info, replacements.get(info.filename, source.read(info)))
    with ZipFile(original) as source, ZipFile(output) as candidate:
        if [i.filename for i in source.infolist()] != [i.filename for i in candidate.infolist()]:
            raise ValueError("AAR member order or names changed")
        changed = {name for name in source.namelist() if source.read(name) != candidate.read(name)}
        if changed != set(replacements):
            raise ValueError(f"unexpected changed entries: {changed}")
    output_sha = sha(output.read_bytes())
    if output_sha != OUTPUT:
        raise ValueError(f"repacked AAR checksum mismatch: {output_sha}")
    print(f"Lazysodium 5.0.2 16 KB AAR: {output_sha}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("arm64", type=Path)
    parser.add_argument("x86_64", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    main(args.original, args.arm64, args.x86_64, args.output)
