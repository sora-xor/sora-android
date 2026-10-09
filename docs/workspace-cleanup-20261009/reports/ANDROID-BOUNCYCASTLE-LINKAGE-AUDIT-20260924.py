#!/usr/bin/env python3
"""Read-only static xcrypto -> Bouncy Castle Release linkage audit.

Uses only the candidate's lock, verification metadata, vendored AAR and the
SHA-256-matching artifacts already present in the local Gradle cache.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from io import BytesIO
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parent
BC = "org/bouncycastle/"
NS = {"v": "https://schema.gradle.org/dependency-verification"}


class Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def number(self, size):
        value = int.from_bytes(self.data[self.pos : self.pos + size], "big")
        self.pos += size
        return value

    def skip(self, size):
        self.pos += size


def class_info(data):
    reader = Reader(data)
    if reader.number(4) != 0xCAFEBABE:
        raise ValueError("invalid classfile magic")
    reader.skip(4)
    pool = [None] * reader.number(2)
    index = 1
    while index < len(pool):
        tag = reader.number(1)
        if tag == 1:
            size = reader.number(2)
            pool[index] = (tag, reader.data[reader.pos : reader.pos + size].decode("utf8", "replace"))
            reader.skip(size)
        elif tag in (3, 4):
            pool[index] = (tag, reader.number(4))
        elif tag in (5, 6):
            pool[index] = (tag, reader.number(4), reader.number(4))
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, reader.number(2))
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, reader.number(2), reader.number(2))
        elif tag == 15:
            pool[index] = (tag, reader.number(1), reader.number(2))
        else:
            raise ValueError(f"unsupported classfile constant tag {tag}")
        index += 1

    def text(index):
        return pool[index][1]

    def class_name(index):
        return text(pool[index][1]) if index else None

    def members():
        result = set()
        for _ in range(reader.number(2)):
            reader.skip(2)
            result.add((text(reader.number(2)), text(reader.number(2))))
            for _ in range(reader.number(2)):
                reader.skip(2)
                reader.skip(reader.number(4))
        return result

    reader.skip(2)
    name = class_name(reader.number(2))
    parent = class_name(reader.number(2))
    interfaces = [class_name(reader.number(2)) for _ in range(reader.number(2))]
    fields = members()
    methods = members()
    references = set()
    for item in pool:
        if item and item[0] in (9, 10, 11):
            name_and_type = pool[item[2]]
            references.add(("field" if item[0] == 9 else "method", class_name(item[1]), text(name_and_type[1]), text(name_and_type[2])))
    return {
        "name": name,
        "parent": parent,
        "interfaces": interfaces,
        "fields": fields,
        "methods": methods,
        "references": references,
        "classes": {class_name(i) for i, item in enumerate(pool) if item and item[0] == 7},
    }


def jar_classes(path_or_bytes):
    with ZipFile(path_or_bytes) as archive:
        return {name[:-6]: class_info(archive.read(name)) for name in archive.namelist() if name.endswith(".class")}


def sha256(path_or_bytes):
    value = path_or_bytes if isinstance(path_or_bytes, bytes) else Path(path_or_bytes).read_bytes()
    return hashlib.sha256(value).hexdigest()


def locked_version(lock_lines, group, module, configuration):
    matches = []
    for line in lock_lines:
        if not line.startswith(f"{group}:{module}:"):
            continue
        coordinate, configurations = line.split("=", 1)
        if configuration in configurations.split(","):
            matches.append(coordinate.rsplit(":", 1)[1])
    if len(matches) != 1:
        raise ValueError(f"expected one locked {group}:{module} for {configuration}; got {matches}")
    return matches[0]


def pinned_jar(candidate, cache, group, module, version):
    metadata = ET.parse(candidate / "gradle/verification-metadata.xml").getroot()
    component = next((item for item in metadata.findall("v:components/v:component", NS) if item.attrib == {"group": group, "name": module, "version": version}), None)
    if component is None:
        raise ValueError(f"missing verification component {group}:{module}:{version}")
    jar_name = f"{module}-{version}.jar"
    artifact = next((item for item in component.findall("v:artifact", NS) if item.attrib.get("name") == jar_name), None)
    checksums = artifact.findall("v:sha256", NS) if artifact is not None else []
    if len(checksums) != 1:
        raise ValueError(f"missing or ambiguous SHA-256 for {jar_name}")
    expected = checksums[0].attrib["value"]
    matches = sorted((cache / group / module / version).glob(f"*/{jar_name}"))
    matching = [path for path in matches if sha256(path) == expected]
    if len(matching) != 1:
        raise ValueError(f"expected one SHA-256-matching cached {jar_name}; got {len(matching)}")
    return matching[0], expected


def jdk_methods(javap):
    result = {}
    for internal in ("java/security/MessageDigest", "java/lang/Object"):
        output = subprocess.run([str(javap), "-s", internal.replace("/", ".")], check=True, capture_output=True, text=True).stdout
        methods = set()
        lines = output.splitlines()
        for line, next_line in zip(lines, lines[1:]):
            declaration = line.strip()
            descriptor = next_line.strip()
            if "(" not in declaration or not descriptor.startswith("descriptor: "):
                continue
            name = declaration.split("(", 1)[0].split()[-1]
            methods.add((name, descriptor.removeprefix("descriptor: ")))
        result[internal] = methods
    return result


def resolves(library, reference, jdk, seen=None):
    kind, owner, name, descriptor = reference
    seen = set() if seen is None else seen
    if owner in seen:
        return False
    seen = seen | {owner}
    item = library.get(owner)
    if item is None:
        return kind == "method" and (name, descriptor) in jdk.get(owner, set())
    if (name, descriptor) in item["methods" if kind == "method" else "fields"]:
        return True
    return any(resolves(library, (kind, parent, name, descriptor), jdk, seen) for parent in [item["parent"], *item["interfaces"]] if parent)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", type=Path, default=ROOT / "sora-android-release-candidate-20260924")
    parser.add_argument("--cache", type=Path, default=Path.home() / ".gradle/caches/modules-2/files-2.1")
    parser.add_argument("--javap", type=Path, default=Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home")) / "bin/javap")
    args = parser.parse_args()
    candidate = args.candidate.resolve()
    lines = (candidate / "app/gradle.lockfile").read_text().splitlines()
    versions = {
        "compile_bcprov": locked_version(lines, "org.bouncycastle", "bcprov-jdk18on", "productionReleaseCompileClasspath"),
        "runtime_bcprov": locked_version(lines, "org.bouncycastle", "bcprov-jdk18on", "productionReleaseRuntimeClasspath"),
        "runtime_bcutil": locked_version(lines, "org.bouncycastle", "bcutil-jdk18on", "productionReleaseRuntimeClasspath"),
    }
    libraries = {}
    paths = {}
    hashes = {}
    for key, module in (("compile_bcprov", "bcprov-jdk18on"), ("runtime_bcprov", "bcprov-jdk18on"), ("runtime_bcutil", "bcutil-jdk18on")):
        paths[key], hashes[key] = pinned_jar(candidate, args.cache, "org.bouncycastle", module, versions[key])
        libraries[key] = jar_classes(paths[key])

    aar = candidate / "vendor/soramitsu-maven/jp/co/soramitsu/xcrypto/1.2.7/xcrypto-1.2.7.aar"
    provenance = json.loads((candidate / "vendor/soramitsu-maven/SOURCE_PROVENANCE.json").read_text())
    module = next(item for item in provenance["modules"] if item["coordinate"] == "jp.co.soramitsu:xcrypto:1.2.7")
    expected_aar = next(item["sha256"] for item in module["materializedFiles"] if item["name"] == aar.name)
    if sha256(aar) != expected_aar:
        raise ValueError("vendored xcrypto AAR differs from source provenance")
    with ZipFile(aar) as archive:
        classes_bytes = archive.read("classes.jar")
    xcrypto = jar_classes(BytesIO(classes_bytes))
    class_references = sorted({name for item in xcrypto.values() for name in item["classes"] if name.startswith(BC)})
    member_references = sorted({ref for item in xcrypto.values() for ref in item["references"] if ref[1].startswith(BC)})
    jdk = jdk_methods(args.javap)
    results = {}
    for key in ("compile_bcprov", "runtime_bcprov"):
        library = libraries[key] if key == "compile_bcprov" else libraries[key] | libraries["runtime_bcutil"]
        missing_classes = [name for name in class_references if name not in library]
        jdk_inherited = [ref for ref in member_references if not resolves(library, ref, {}) and resolves(library, ref, jdk)]
        missing_members = [ref for ref in member_references if not resolves(library, ref, jdk)]
        results[key] = {"missing_classes": missing_classes, "missing_members": missing_members, "jdk_inherited_member_count": len(jdk_inherited)}
    print(json.dumps({
        "candidate": str(candidate),
        "locked_versions": versions,
        "cached_jars": {key: {"path": str(paths[key]), "sha256": hashes[key]} for key in paths},
        "xcrypto_aar_sha256": expected_aar,
        "xcrypto_classes_jar_sha256": sha256(classes_bytes),
        "xcrypto_class_count": len(xcrypto),
        "bouncycastle_class_reference_count": len(class_references),
        "bouncycastle_member_reference_count": len(member_references),
        "bcutil_only_class_references": [name for name in class_references if name in libraries["runtime_bcutil"] and name not in libraries["runtime_bcprov"]],
        "linkage": results,
        "limits": "Static classfile references only; reflection, JNI/native, Android class loading, and device behavior are not assessed.",
    }, indent=2))
    if any(result["missing_classes"] or result["missing_members"] for result in results.values()):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
