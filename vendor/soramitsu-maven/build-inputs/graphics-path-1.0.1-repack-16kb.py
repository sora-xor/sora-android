#!/usr/bin/env python3
"""Replace only 64-bit graphics-path 1.0.1 JNI entries and update Gradle module file hashes."""

import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile


ORIGINAL_AAR_SHA256 = "8ca4032b6d79b351f0b59ad4b580eddbb9423e1652f7c958830687f1eee2ec03"
ORIGINAL_MODULE_SHA256 = "3f6fc7e96f8a1fd21045da7f2e332aef528aa1f56b6455fb8f25043aafa0e1b8"
OUTPUT_AAR_SHA256 = "eb4759c27ae0625d9e81fe95d8ce63d0a3846764a3465363ab195109d6785a71"
OUTPUT_MODULE_SHA256 = "478695824bf314c9d0603ea249e25431b172c485aab20a61856a2cdea6819441"
REPLACEMENTS = {
    "jni/arm64-v8a/libandroidx.graphics.path.so": "d7ec2a1d0e1c1c652dd7bc0f70825835b1c9a6d26f72caab33b88f4f425d5e9e",
    "jni/x86_64/libandroidx.graphics.path.so": "c286266ff104672626bce3b87b3b3367e017db972cff12dd53290c967de42e17",
}


def digest(data: bytes, algorithm: str = "sha256") -> str:
    return hashlib.new(algorithm, data).hexdigest()


def main(original_aar: Path, original_module: Path, arm64: Path, x86_64: Path,
         output_aar: Path, output_module: Path) -> None:
    paths = [original_aar, original_module, arm64, x86_64, output_aar, output_module]
    if len({path.resolve() for path in paths}) != len(paths):
        raise ValueError("all input and output paths must be distinct")
    if digest(original_aar.read_bytes()) != ORIGINAL_AAR_SHA256:
        raise ValueError("original AAR digest mismatch")
    if digest(original_module.read_bytes()) != ORIGINAL_MODULE_SHA256:
        raise ValueError("original Gradle module digest mismatch")
    replacements = dict(zip(REPLACEMENTS, (arm64.read_bytes(), x86_64.read_bytes())))
    for name, data in replacements.items():
        if digest(data) != REPLACEMENTS[name]:
            raise ValueError(f"rebuilt JNI digest mismatch: {name}")
    with ZipFile(original_aar) as source, ZipFile(output_aar, "w") as candidate:
        entries = source.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)) or not set(replacements).issubset(names):
            raise ValueError("original AAR has duplicate or missing entries")
        for entry in entries:
            candidate.writestr(entry, replacements.get(entry.filename, source.read(entry)))
    with ZipFile(original_aar) as source, ZipFile(output_aar) as candidate:
        if source.namelist() != candidate.namelist():
            raise ValueError("AAR entry names or order changed")
        changed = {name for name in source.namelist() if source.read(name) != candidate.read(name)}
        if changed != set(replacements):
            raise ValueError(f"unexpected changed AAR members: {sorted(changed)}")
    new_aar = output_aar.read_bytes()
    if digest(new_aar) != OUTPUT_AAR_SHA256:
        raise ValueError("repacked AAR digest mismatch")

    original_bytes = original_module.read_bytes()
    original_record = json.loads(original_bytes)
    if original_record["component"] != {
        "group": "androidx.graphics", "module": "graphics-path", "version": "1.0.1",
        "attributes": {"org.gradle.status": "release"},
    }:
        raise ValueError("unexpected Gradle module identity")
    replacement_values = {
        "size": len(new_aar),
        **{name: digest(new_aar, name) for name in ("sha512", "sha256", "sha1", "md5")},
    }
    original_values = {
        "size": original_aar.stat().st_size,
        **{name: digest(original_aar.read_bytes(), name) for name in ("sha512", "sha256", "sha1", "md5")},
    }
    output_bytes = original_bytes
    for key, old in original_values.items():
        old_value = str(old).encode()
        new_value = str(replacement_values[key]).encode()
        if output_bytes.count(old_value) != 2:
            raise ValueError(f"Gradle module expected two AAR descriptors for {key}")
        output_bytes = output_bytes.replace(old_value, new_value)
    output_module.write_bytes(output_bytes)
    updated = json.loads(output_bytes)
    original_record["variants"][0]["files"][0].update(replacement_values)
    original_record["variants"][1]["files"][0].update(replacement_values)
    if updated != original_record or digest(output_bytes) != OUTPUT_MODULE_SHA256:
        raise ValueError("Gradle module changed beyond the two AAR file descriptors")
    print(f"graphics-path 1.0.1 16 KB AAR: {OUTPUT_AAR_SHA256}")
    print(f"graphics-path 1.0.1 Gradle module: {OUTPUT_MODULE_SHA256}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("original_aar", "original_module", "arm64", "x86_64", "output_aar", "output_module"):
        parser.add_argument(name, type=Path)
    args = parser.parse_args()
    main(args.original_aar, args.original_module, args.arm64, args.x86_64,
         args.output_aar, args.output_module)
