#!/usr/bin/env python3
"""Replace only 64-bit CameraX 1.3.1 JNI and update its Gradle module hashes."""

import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile


ORIGINAL_AAR_SHA256 = "6b7ea2da7cc504d6624c3c12a0c2d488dd6635563421dacba0790399507443e8"
ORIGINAL_MODULE_SHA256 = "fe175138941912c5c1ad8ce070a72c56650beceef7bbdfbde49d179ee3dec894"
OUTPUT_AAR_SHA256 = "e42b866b808d12cf69f9477aa902f8ba0934449ca738e97d4ffd341ad4b3ca37"
OUTPUT_MODULE_SHA256 = "58b26548d8ddd483f14e1740eae38655333852d5985c7dde84597807af1f1b21"
REPLACEMENTS = {
    "jni/arm64-v8a/libimage_processing_util_jni.so": "2e7b45ad96e790e1b11095f22021232d29d9b128d776eb5a2a7485d22da19e4e",
    "jni/x86_64/libimage_processing_util_jni.so": "c8ac7fe97507a374f789b42f8ca41dff671c4723eb8ec78ec76872746143a4d0",
}


def digest(data: bytes, algorithm: str = "sha256") -> str:
    return hashlib.new(algorithm, data).hexdigest()


def main(original_aar: Path, original_module: Path, arm64: Path, x86_64: Path,
         output_aar: Path, output_module: Path) -> None:
    paths = [original_aar, original_module, arm64, x86_64, output_aar, output_module]
    if len({path.resolve() for path in paths}) != len(paths):
        raise ValueError("all input and output paths must be distinct")
    original_bytes = original_aar.read_bytes()
    module_bytes = original_module.read_bytes()
    if digest(original_bytes) != ORIGINAL_AAR_SHA256:
        raise ValueError("original AAR digest mismatch")
    if digest(module_bytes) != ORIGINAL_MODULE_SHA256:
        raise ValueError("original Gradle module digest mismatch")
    replacements = dict(zip(REPLACEMENTS, (arm64.read_bytes(), x86_64.read_bytes())))
    for name, data in replacements.items():
        if digest(data) != REPLACEMENTS[name]:
            raise ValueError(f"rebuilt JNI digest mismatch: {name}")
    with ZipFile(original_aar) as source, ZipFile(output_aar, "w") as candidate:
        entries = source.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != 24 or len(names) != len(set(names)) or not set(replacements).issubset(names):
            raise ValueError("original AAR layout changed")
        for entry in entries:
            candidate.writestr(entry, replacements.get(entry.filename, source.read(entry)))
    with ZipFile(original_aar) as source, ZipFile(output_aar) as candidate:
        if source.namelist() != candidate.namelist():
            raise ValueError("AAR entry names or order changed")
        changed = {name for name in source.namelist() if source.read(name) != candidate.read(name)}
        if changed != set(replacements):
            raise ValueError(f"unexpected changed AAR entries: {sorted(changed)}")
    new_aar = output_aar.read_bytes()
    if digest(new_aar) != OUTPUT_AAR_SHA256:
        raise ValueError("repacked AAR digest mismatch")

    original_record = json.loads(module_bytes)
    if original_record["component"] != {
        "group": "androidx.camera", "module": "camera-core", "version": "1.3.1",
        "attributes": {"org.gradle.status": "release"},
    }:
        raise ValueError("unexpected Gradle module identity")
    original_values = {
        "size": len(original_bytes),
        **{name: digest(original_bytes, name) for name in ("sha512", "sha256", "sha1", "md5")},
    }
    replacement_values = {
        "size": len(new_aar),
        **{name: digest(new_aar, name) for name in ("sha512", "sha256", "sha1", "md5")},
    }
    updated_bytes = module_bytes
    for key, old in original_values.items():
        old_value = str(old).encode()
        if updated_bytes.count(old_value) != 2:
            raise ValueError(f"expected two Gradle AAR descriptors for {key}")
        updated_bytes = updated_bytes.replace(old_value, str(replacement_values[key]).encode())
    output_module.write_bytes(updated_bytes)
    for variant in original_record["variants"][:2]:
        if variant["files"][0]["name"] != "camera-core-1.3.1.aar":
            raise ValueError("unexpected Gradle AAR variant layout")
        variant["files"][0].update(replacement_values)
    if json.loads(updated_bytes) != original_record or digest(updated_bytes) != OUTPUT_MODULE_SHA256:
        raise ValueError("Gradle module changed beyond its two AAR file descriptors")
    print(f"CameraX 1.3.1 16 KB AAR: {OUTPUT_AAR_SHA256}")
    print(f"CameraX 1.3.1 Gradle module: {OUTPUT_MODULE_SHA256}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("original_aar", "original_module", "arm64", "x86_64", "output_aar", "output_module"):
        parser.add_argument(name, type=Path)
    args = parser.parse_args()
    main(args.original_aar, args.original_module, args.arm64, args.x86_64,
         args.output_aar, args.output_module)
