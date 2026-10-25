#!/usr/bin/env python3
"""Check direct BC/JMRTD bytecode symbols in Gradle's resolved Android runtime jars.

Supply the output of :app:bcAuditClasspath with the retained classpath.init.gradle.
This does not prove reflective, native, device or live KYC behavior.
"""
import argparse
import hashlib
import io
import json
import pathlib
import re
import struct
import zipfile

PREFIXES = ("org/bouncycastle/", "org/jmrtd/")


class Reader:
    def __init__(self, data):
        self.data, self.offset = data, 0

    def take(self, size):
        result = self.data[self.offset:self.offset + size]
        if len(result) != size:
            raise ValueError("Truncated class file")
        self.offset += size
        return result

    def u1(self): return self.take(1)[0]
    def u2(self): return struct.unpack(">H", self.take(2))[0]
    def u4(self): return struct.unpack(">I", self.take(4))[0]


def parse(data):
    reader = Reader(data)
    if reader.u4() != 0xcafebabe:
        raise ValueError("Invalid class file")
    reader.u2(); reader.u2()
    pool, index = [None] * reader.u2(), 1
    while index < len(pool):
        tag = reader.u1()
        if tag == 1: pool[index] = (tag, reader.take(reader.u2()).decode("utf-8", errors="replace"))
        elif tag in (3, 4): pool[index] = (tag, reader.take(4))
        elif tag in (5, 6): pool[index] = (tag, reader.take(8)); index += 1
        elif tag in (7, 8, 16, 19, 20): pool[index] = (tag, reader.u2())
        elif tag in (9, 10, 11, 12, 17, 18): pool[index] = (tag, reader.u2(), reader.u2())
        elif tag == 15: pool[index] = (tag, reader.u1(), reader.u2())
        else: raise ValueError("Unknown constant-pool tag")
        index += 1
    utf = lambda index: pool[index][1]
    cls = lambda index: utf(pool[index][1]) if index else None
    reader.u2()
    name, parent = cls(reader.u2()), cls(reader.u2())
    interfaces = [cls(reader.u2()) for _ in range(reader.u2())]
    def attributes():
        for _ in range(reader.u2()): reader.u2(); reader.take(reader.u4())
    definitions = set()
    for kind in ("field", "method"):
        for _ in range(reader.u2()):
            reader.u2()
            definitions.add((kind, utf(reader.u2()), utf(reader.u2())))
            attributes()
    attributes()
    references, classes = [], set()
    for entry in pool[1:]:
        if not entry: continue
        if entry[0] in (9, 10, 11):
            owner, pair = cls(entry[1]), pool[entry[2]]
            if owner.startswith(PREFIXES):
                references.append((owner, "field" if entry[0] == 9 else "method", utf(pair[1]), utf(pair[2])))
        if entry[0] == 7:
            value = utf(entry[1])
            if value.startswith(PREFIXES): classes.add(value)
        if entry[0] == 1:
            classes.update(re.findall(r"L((?:org/bouncycastle/|org/jmrtd/)[^;<>]+);", entry[1]))
    return {"name": name, "parent": parent, "interfaces": interfaces, "definitions": definitions,
            "references": references, "classes": classes}


def read_classes(path, all_definitions=False):
    with zipfile.ZipFile(path) as archive:
        result = []
        for name in archive.namelist():
            if not name.endswith(".class") or name.startswith("META-INF/versions/") or name.endswith("module-info.class"):
                continue
            data = archive.read(name)
            if all_definitions or any(prefix.encode() in data for prefix in PREFIXES):
                result.append(parse(data))
        return result


def resolve(definitions, owner, kind, name, descriptor, seen=None):
    seen = set() if seen is None else seen
    if owner not in definitions or owner in seen: return False
    seen.add(owner)
    target = definitions[owner]
    if (kind, name, descriptor) in target["definitions"]: return True
    if name in ("<init>", "<clinit>"): return False
    return any(resolve(definitions, parent, kind, name, descriptor, seen)
               for parent in [target["parent"]] + target["interfaces"] if parent)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath-log", type=pathlib.Path, required=True)
    parser.add_argument("--java-home", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    artifacts = {}
    for line in args.classpath_log.read_text().splitlines():
        if line.startswith("BC_AUDIT\tproductionReleaseRuntimeClasspath\t"):
            _, _, coordinate, path = line.split("\t")
            artifacts[coordinate] = pathlib.Path(path)
    if not artifacts: raise ValueError("No resolved production runtime artifacts")
    definitions = {value["name"]: value for value in read_classes(args.java_home / "jmods/java.base.jmod", all_definitions=True)}
    users, identities = [], []
    for coordinate, path in sorted(artifacts.items()):
        values = read_classes(path, all_definitions=coordinate.startswith(("org.bouncycastle:", "org.jmrtd:", "net.sf.scuba:")))
        definitions.update({value["name"]: value for value in values})
        users.extend((coordinate, value) for value in values)
        identities.append({"coordinate": coordinate, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    missing, reference_count, consumers = [], 0, set()
    for coordinate, value in users:
        for target in value["classes"]:
            if target not in definitions: missing.append({"artifact": coordinate, "class": value["name"], "missingClass": target})
        for owner, kind, name, descriptor in value["references"]:
            reference_count += 1
            if not coordinate.startswith(("org.bouncycastle:", "org.jmrtd:")): consumers.add(coordinate)
            if not resolve(definitions, owner, kind, name, descriptor):
                missing.append({"artifact": coordinate, "class": value["name"], "owner": owner, "member": name, "descriptor": descriptor})
    result = {"scope": "Resolved Android-base production runtime jars; direct BC/JMRTD symbols only",
              "artifactCount": len(artifacts), "referenceCount": reference_count,
              "externalConsumers": sorted(consumers), "missing": missing, "artifacts": identities,
              "limitations": ["Does not prove reflection, JNI, Android class loading, or physical passport/KYC behavior"]}
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({key: result[key] for key in ("artifactCount", "referenceCount", "externalConsumers", "missing")}))
    return 1 if missing else 0


if __name__ == "__main__":
    raise SystemExit(main())
