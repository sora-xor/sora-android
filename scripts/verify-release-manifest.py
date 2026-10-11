#!/usr/bin/env python3
"""Check the merged manifest inside a release APK or AAB against the reviewed policy.

Usage: verify-release-manifest.py [--policy FILE] [--expect-version-code N]
                                  [--expect-version-name NAME] ARTIFACT.apk|ARTIFACT.aab [...]

The policy is config/android-production-manifest-policy.json. The check fails when the
artifact Google Play would receive differs from it in any of these:

  - package name, minSdkVersion, targetSdkVersion (and the version, when asked);
  - backups: allowBackup and fullBackupContent must be false, dataExtractionRules must be set;
  - debuggable, testOnly, usesCleartextTraffic and sharedUserId must be off or absent;
  - deep links: no <data> element, no VIEW action and no BROWSABLE category anywhere;
  - forbidden components (IrohaConnect, the debug-only Safe Mode preview);
  - the permission list, the <queries> list and the exported components.

The manifest is read straight from the file: the binary XML of an APK and the protobuf XML of
an AAB are both parsed here, so the check needs no Android SDK tool and reads the AAB itself,
not only the APK built next to it.
"""

from __future__ import annotations

import argparse
import json
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_POLICY_FILE = ROOT / "config" / "android-production-manifest-policy.json"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
MANIFEST_SIZE_LIMIT = 4 * 1024 * 1024
COMPONENT_TAGS = ("activity", "activity-alias", "service", "receiver", "provider")
LAUNCHER_ACTIONS = {"android.intent.action.MAIN"}
LAUNCHER_CATEGORIES = {"android.intent.category.LAUNCHER", "android.intent.category.DEFAULT"}

# Android resolves a manifest attribute by its resource id, not by the name string stored next
# to it. Attributes this check depends on are therefore recognised by id first.
ANDROID_ATTRIBUTE_IDS = {
    0x01010003: "name",
    0x01010006: "permission",
    0x0101000B: "sharedUserId",
    0x0101000E: "enabled",
    0x0101000F: "debuggable",
    0x01010010: "exported",
    0x01010026: "mimeType",
    0x01010027: "scheme",
    0x01010028: "host",
    0x01010029: "port",
    0x0101002A: "path",
    0x0101002B: "pathPrefix",
    0x0101002C: "pathPattern",
    0x01010202: "targetActivity",
    0x0101020C: "minSdkVersion",
    0x0101021B: "versionCode",
    0x0101021C: "versionName",
    0x01010270: "targetSdkVersion",
    0x01010272: "testOnly",
    0x01010280: "allowBackup",
    0x010104EB: "fullBackupContent",
    0x010104EC: "usesCleartextTraffic",
    0x01010527: "networkSecurityConfig",
    0x0101063E: "dataExtractionRules",
}


class ManifestError(Exception):
    """The artifact or the policy cannot be read."""


class Element:
    def __init__(self, name: str):
        self.name = name
        # Keyed by the plain attribute name for android: attributes and for attributes without
        # a namespace ("package"); other namespaces are stored as "{uri}name".
        self.attributes: dict[str, object] = {}
        self.children: list[Element] = []

    def get(self, name: str):
        return self.attributes.get(name)

    def child_elements(self, name: str) -> list[Element]:
        return [child for child in self.children if child.name == name]

    def walk(self):
        yield self
        for child in self.children:
            yield from child.walk()


def attribute_key(namespace: str, name: str) -> str:
    return name if namespace in ("", ANDROID_NS) else "{" + namespace + "}" + name


# ---------------------------------------------------------------------------------------------
# APK: Android binary XML
# ---------------------------------------------------------------------------------------------

RES_STRING_POOL = 0x0001
RES_XML = 0x0003
RES_XML_START_ELEMENT = 0x0102
RES_XML_END_ELEMENT = 0x0103
RES_XML_RESOURCE_MAP = 0x0180
NO_INDEX = 0xFFFFFFFF
TYPE_REFERENCE = 0x01
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_INT_BOOLEAN = 0x12


def _unpack(fmt: str, data: bytes, offset: int) -> tuple:
    try:
        return struct.unpack_from(fmt, data, offset)
    except struct.error as error:
        raise ManifestError(f"binary manifest is truncated at offset {offset}") from error


def _string_pool(data: bytes, start: int, header_size: int, size: int) -> list[str]:
    string_count, _, flags, strings_start, _ = _unpack("<IIIII", data, start + 8)
    utf8 = bool(flags & 0x100)
    end = start + size
    strings = []
    for index in range(string_count):
        (relative,) = _unpack("<I", data, start + header_size + 4 * index)
        position = start + strings_start + relative
        if position >= end:
            raise ManifestError("binary manifest string lies outside its string pool")
        if utf8:
            # Character count, then byte count; each is one byte, or two when the high bit is set.
            for _ in range(2):
                length = data[position]
                position += 1
                if length & 0x80:
                    length = ((length & 0x7F) << 8) | data[position]
                    position += 1
            raw = data[position : position + length]
            if len(raw) != length or position + length > end:
                raise ManifestError("binary manifest string is truncated")
            strings.append(raw.decode("utf-8", errors="replace"))
        else:
            (length,) = _unpack("<H", data, position)
            position += 2
            if length & 0x8000:
                (low,) = _unpack("<H", data, position)
                position += 2
                length = ((length & 0x7FFF) << 16) | low
            raw = data[position : position + 2 * length]
            if len(raw) != 2 * length or position + 2 * length > end:
                raise ManifestError("binary manifest string is truncated")
            strings.append(raw.decode("utf-16-le", errors="replace"))
    return strings


def parse_binary_manifest(data: bytes) -> Element:
    chunk_type, header_size, size = _unpack("<HHI", data, 0)
    if chunk_type != RES_XML or header_size != 8 or size > len(data):
        raise ManifestError("AndroidManifest.xml is not Android binary XML")
    strings: list[str] = []
    resource_map: list[int] = []
    root = None
    stack: list[Element] = []

    def text(index: int) -> str:
        if index == NO_INDEX:
            return ""
        if index >= len(strings):
            raise ManifestError("binary manifest refers to a string that does not exist")
        return strings[index]

    position = header_size
    while position < size:
        chunk_type, chunk_header, chunk_size = _unpack("<HHI", data, position)
        if chunk_size < 8 or chunk_header < 8 or position + chunk_size > size:
            raise ManifestError("binary manifest has a chunk with an invalid size")
        if chunk_type == RES_STRING_POOL:
            if strings:
                raise ManifestError("binary manifest has two string pools")
            strings = _string_pool(data, position, chunk_header, chunk_size)
        elif chunk_type == RES_XML_RESOURCE_MAP:
            count = (chunk_size - chunk_header) // 4
            resource_map = list(_unpack(f"<{count}I", data, position + chunk_header))
        elif chunk_type == RES_XML_START_ELEMENT:
            body = position + chunk_header
            _, name_index, attr_start, attr_size, attr_count = _unpack("<IIHHH", data, body)
            if attr_size < 20:
                raise ManifestError("binary manifest has an attribute record that is too small")
            element = Element(text(name_index))
            for index in range(attr_count):
                offset = body + attr_start + index * attr_size
                ns_index, attr_name, raw_value, _, _, data_type, value = _unpack(
                    "<IIIHBBI", data, offset
                )
                resource_id = resource_map[attr_name] if attr_name < len(resource_map) else 0
                if resource_id in ANDROID_ATTRIBUTE_IDS:
                    key = ANDROID_ATTRIBUTE_IDS[resource_id]
                elif resource_id >> 24 == 0x01:
                    # Another framework attribute: keep its name, or its id when the name is
                    # stripped, but never let it pose as an application attribute.
                    key = text(attr_name) or f"@0x{resource_id:08x}"
                else:
                    key = attribute_key(text(ns_index), text(attr_name))
                if data_type == TYPE_STRING:
                    element.attributes[key] = text(value if raw_value == NO_INDEX else raw_value)
                elif data_type == TYPE_INT_BOOLEAN:
                    element.attributes[key] = value != 0
                elif data_type in (TYPE_INT_DEC, TYPE_INT_HEX):
                    element.attributes[key] = value
                elif data_type == TYPE_REFERENCE:
                    element.attributes[key] = f"@0x{value:08x}"
                else:
                    element.attributes[key] = text(raw_value) or f"(type 0x{data_type:02x}) 0x{value:08x}"
            if stack:
                stack[-1].children.append(element)
            elif root is None:
                root = element
            else:
                raise ManifestError("binary manifest has two root elements")
            stack.append(element)
        elif chunk_type == RES_XML_END_ELEMENT:
            if not stack:
                raise ManifestError("binary manifest closes an element that was never opened")
            stack.pop()
        position += chunk_size
    if root is None or stack:
        raise ManifestError("binary manifest has no complete root element")
    return root


# ---------------------------------------------------------------------------------------------
# AAB: aapt2 protobuf XML (Resources.proto: XmlNode, XmlElement, XmlAttribute, Item, Primitive)
# ---------------------------------------------------------------------------------------------


def _varint(data: bytes, position: int) -> tuple[int, int]:
    value = 0
    for index in range(10):
        if position >= len(data):
            raise ManifestError("protobuf manifest is truncated")
        byte = data[position]
        position += 1
        value |= (byte & 0x7F) << (7 * index)
        if byte < 0x80:
            return value, position
    raise ManifestError("protobuf manifest has an oversized number")


def _fields(data: bytes) -> list[tuple[int, int, object]]:
    fields = []
    position = 0
    while position < len(data):
        key, position = _varint(data, position)
        number, wire_type = key >> 3, key & 7
        if number == 0:
            raise ManifestError("protobuf manifest has field zero")
        if wire_type == 0:
            value, position = _varint(data, position)
        elif wire_type in (1, 2, 5):
            length = {1: 8, 5: 4}.get(wire_type)
            if wire_type == 2:
                length, position = _varint(data, position)
            if length > len(data) - position:
                raise ManifestError("protobuf manifest field extends past its message")
            value = data[position : position + length]
            position += length
        else:
            raise ManifestError(f"protobuf manifest has unsupported wire type {wire_type}")
        fields.append((number, wire_type, value))
    return fields


def _utf8(value) -> str:
    if not isinstance(value, bytes):
        raise ManifestError("protobuf manifest has a number where text is expected")
    return value.decode("utf-8", errors="replace")


def _proto_primitive(item: bytes):
    """Return the typed value of an aapt2 Item when it is a boolean or a whole number."""
    for number, wire_type, value in _fields(item):
        if number != 7 or wire_type != 2:  # Item.prim
            continue
        for prim_number, prim_wire, prim_value in _fields(value):
            if prim_wire != 0:
                continue
            if prim_number == 8:  # boolean_value
                return prim_value != 0
            if prim_number == 6:  # int_decimal_value (int32, sign-extended to 64 bits)
                return prim_value - (1 << 64) if prim_value >> 63 else prim_value
            if prim_number == 7:  # int_hexadecimal_value
                return prim_value
    return None


def _proto_element(data: bytes) -> Element:
    name = ""
    attributes = []
    children = []
    for number, wire_type, value in _fields(data):
        if wire_type != 2:
            continue
        if number == 3:
            name = _utf8(value)
        elif number == 4:
            attributes.append(value)
        elif number == 5:
            children.append(value)
    element = Element(name)
    for raw in attributes:
        namespace = attr_name = text = ""
        resource_id = 0
        typed = None
        for number, wire_type, value in _fields(raw):
            if number == 1 and wire_type == 2:
                namespace = _utf8(value)
            elif number == 2 and wire_type == 2:
                attr_name = _utf8(value)
            elif number == 3 and wire_type == 2:
                text = _utf8(value)
            elif number == 5 and wire_type == 0:
                resource_id = value
            elif number == 6 and wire_type == 2:
                typed = _proto_primitive(value)
        if resource_id in ANDROID_ATTRIBUTE_IDS:
            key = ANDROID_ATTRIBUTE_IDS[resource_id]
        elif resource_id >> 24 == 0x01:
            key = attr_name or f"@0x{resource_id:08x}"
        else:
            key = attribute_key(namespace, attr_name)
        element.attributes[key] = text if typed is None else typed
    for raw in children:
        node = _proto_node(raw)
        if node is not None:
            element.children.append(node)
    return element


def _proto_node(data: bytes):
    for number, wire_type, value in _fields(data):
        if number == 1 and wire_type == 2:  # XmlNode.element
            return _proto_element(value)
    return None  # text or whitespace


def parse_proto_manifest(data: bytes) -> Element:
    root = _proto_node(data)
    if root is None:
        raise ManifestError("protobuf manifest has no root element")
    return root


# ---------------------------------------------------------------------------------------------
# Reading the artifact
# ---------------------------------------------------------------------------------------------


def _read_single(archive: zipfile.ZipFile, name: str) -> bytes:
    entries = [info for info in archive.infolist() if info.filename == name]
    if len(entries) != 1:
        raise ManifestError(f"expected exactly one {name}, found {len(entries)}")
    if entries[0].file_size > MANIFEST_SIZE_LIMIT:
        raise ManifestError(f"{name} is larger than {MANIFEST_SIZE_LIMIT} bytes")
    return archive.read(entries[0])


def load_manifest(path: Path) -> Element:
    if not path.is_file() or path.is_symlink() or path.stat().st_size == 0:
        raise ManifestError(f"{path}: not a nonempty regular file")
    try:
        with zipfile.ZipFile(path) as archive:
            if path.suffix == ".apk":
                return parse_binary_manifest(_read_single(archive, "AndroidManifest.xml"))
            if path.suffix == ".aab":
                manifests = sorted(
                    info.filename
                    for info in archive.infolist()
                    if info.filename.endswith("/manifest/AndroidManifest.xml")
                )
                # Play builds every installed APK from these modules. The policy describes one
                # manifest, so an added feature module must be reviewed and the check extended.
                if manifests != ["base/manifest/AndroidManifest.xml"]:
                    raise ManifestError(
                        "expected only base/manifest/AndroidManifest.xml in the bundle, found "
                        + (", ".join(manifests) or "none")
                    )
                return parse_proto_manifest(_read_single(archive, manifests[0]))
    except (OSError, zipfile.BadZipFile, IndexError) as error:
        raise ManifestError(f"{path}: cannot read the manifest: {error}") from error
    raise ManifestError(f"{path}: expected an .apk or .aab file")


def load_policy(path: Path) -> dict:
    try:
        policy = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise ManifestError(f"cannot read the manifest policy from {path}: {error}") from error
    problems = []
    if not isinstance(policy, dict):
        raise ManifestError(f"{path}: the policy must be a JSON object")
    for key, kind in (
        ("applicationId", str),
        ("minSdkVersion", int),
        ("targetSdkVersion", int),
        ("usesPermissions", list),
        ("queries", list),
        ("exportedComponents", dict),
        ("forbiddenComponentNameParts", list),
    ):
        if not isinstance(policy.get(key), kind) or isinstance(policy.get(key), bool):
            problems.append(f"{key} must be a {kind.__name__}")
    if not problems:
        for name, rule in policy["exportedComponents"].items():
            if not isinstance(rule, dict) or not (
                rule.get("launcherOnly") is True or isinstance(rule.get("permission"), str)
            ):
                problems.append(f"exportedComponents[{name}] needs launcherOnly or permission")
    if problems:
        raise ManifestError(f"{path}: " + "; ".join(problems))
    return policy


# ---------------------------------------------------------------------------------------------
# The policy
# ---------------------------------------------------------------------------------------------


def as_bool(value):
    """True/False for a boolean attribute value in either manifest encoding, else None."""
    if isinstance(value, bool):
        return value
    if isinstance(value, int):
        return value != 0
    if isinstance(value, str) and value.strip().lower() in ("true", "false"):
        return value.strip().lower() == "true"
    return None


def as_int(value):
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, str) and value.strip().isdigit():
        return int(value.strip())
    return None


def shown(value) -> str:
    """One spelling for a value in messages, whichever manifest encoding it came from."""
    if value is None:
        return "absent"
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def _must_be_off(element: Element, attribute: str, issues: list[str]) -> None:
    value = element.get(attribute)
    if value is not None and as_bool(value) is not False:
        issues.append(
            f"<{element.name}> android:{attribute} is {shown(value)}; it must be false or absent"
        )


def _filters(component: Element) -> list[tuple[set[str], set[str]]]:
    result = []
    for intent_filter in component.child_elements("intent-filter"):
        actions = {str(node.get("name")) for node in intent_filter.child_elements("action")}
        categories = {str(node.get("name")) for node in intent_filter.child_elements("category")}
        result.append((actions, categories))
    return result


def check_manifest(manifest: Element, policy: dict, version_code=None, version_name=None) -> list[str]:
    issues: list[str] = []
    if manifest.name != "manifest":
        return [f"root element is <{manifest.name}>, not <manifest>"]

    package = manifest.get("package")
    if package != policy["applicationId"]:
        issues.append(f"package is {shown(package)}, expected {policy['applicationId']}")
    if manifest.get("sharedUserId") is not None:
        issues.append("android:sharedUserId is set; the app must not share a user id")
    if version_code is not None and as_int(manifest.get("versionCode")) != version_code:
        issues.append(f"versionCode is {shown(manifest.get('versionCode'))}, expected {version_code}")
    if version_name is not None and manifest.get("versionName") != version_name:
        issues.append(f"versionName is {shown(manifest.get('versionName'))}, expected {version_name}")

    uses_sdk = manifest.child_elements("uses-sdk")
    if len(uses_sdk) != 1:
        issues.append(f"expected one <uses-sdk>, found {len(uses_sdk)}")
    else:
        for attribute in ("minSdkVersion", "targetSdkVersion"):
            actual = as_int(uses_sdk[0].get(attribute))
            if actual != policy[attribute]:
                issues.append(
                    f"{attribute} is {shown(uses_sdk[0].get(attribute))}, expected {policy[attribute]}"
                )

    applications = manifest.child_elements("application")
    if len(applications) != 1:
        return issues + [f"expected one <application>, found {len(applications)}"]
    application = applications[0]
    for attribute in ("allowBackup", "fullBackupContent"):
        # allowBackup defaults to true when it is absent, so absence is a failure too.
        if as_bool(application.get(attribute)) is not False:
            issues.append(
                f"<application> android:{attribute} is {shown(application.get(attribute))}; "
                "it must be false"
            )
    if not application.get("dataExtractionRules"):
        issues.append("<application> android:dataExtractionRules is missing")
    for attribute in ("debuggable", "testOnly", "usesCleartextTraffic"):
        _must_be_off(application, attribute, issues)

    # Deep links. Production declares none; any of these would let a web page or another app
    # start a screen of the wallet.
    for element in manifest.walk():
        if element.name == "data":
            link = ", ".join(f"{key}={shown(value)}" for key, value in sorted(element.attributes.items()))
            issues.append(f"deep link: <data {link}> is declared")
        elif element.name == "action" and element.get("name") == "android.intent.action.VIEW":
            issues.append("deep link: an intent filter accepts android.intent.action.VIEW")
        elif element.name == "category" and element.get("name") == "android.intent.category.BROWSABLE":
            issues.append("deep link: an intent filter has the BROWSABLE category")

    forbidden = [part.lower() for part in policy["forbiddenComponentNameParts"]]
    exported: dict[str, Element] = {}
    for component in application.children:
        if component.name not in COMPONENT_TAGS:
            continue
        name = str(component.get("name") or "")
        for field in (name, str(component.get("targetActivity") or "")):
            for part in forbidden:
                if part in field.lower():
                    issues.append(f"forbidden component is packaged: <{component.name}> {field}")
        declared = component.get("exported")
        flag = as_bool(declared)
        has_filter = bool(component.child_elements("intent-filter"))
        # Exported when it says so, when it has an intent filter and says nothing, and when the
        # value is not a plain true or false (a resource reference cannot be judged here).
        if flag is True or (flag is None and (has_filter or declared is not None)):
            if name in exported:
                issues.append(f"exported component {name} is declared twice")
            exported[name] = component

    expected_exported = policy["exportedComponents"]
    for name in sorted(set(exported) - set(expected_exported)):
        issues.append(f"exported component is not in the reviewed list: <{exported[name].name}> {name}")
    for name in sorted(set(expected_exported) - set(exported)):
        issues.append(f"reviewed exported component is missing or no longer exported: {name}")
    for name in sorted(set(exported) & set(expected_exported)):
        rule = expected_exported[name]
        component = exported[name]
        if rule.get("launcherOnly") is True:
            for actions, categories in _filters(component):
                if not actions <= LAUNCHER_ACTIONS or not categories <= LAUNCHER_CATEGORIES:
                    issues.append(
                        f"{name} must only be the launcher entry, but a filter has actions "
                        f"{sorted(actions)} and categories {sorted(categories)}"
                    )
            if component.get("permission") is not None:
                issues.append(f"{name} is the launcher entry and must not require a permission")
        elif component.get("permission") != rule.get("permission"):
            issues.append(
                f"exported component {name} is guarded by permission "
                f"{shown(component.get('permission'))}, expected {rule.get('permission')}"
            )

    permissions = [
        str(node.get("name"))
        for node in manifest.children
        if node.name in ("uses-permission", "uses-permission-sdk-23")
    ]
    expected_permissions = set(policy["usesPermissions"])
    for name in sorted(set(permissions) - expected_permissions):
        issues.append(f"permission is not in the reviewed list: {name}")
    for name in sorted(expected_permissions - set(permissions)):
        issues.append(f"reviewed permission is no longer requested: {name}")

    queries = sorted(
        f"{node.name} {node.get('name') or ''}".strip()
        for parent in manifest.child_elements("queries")
        for node in parent.children
    )
    if queries != sorted(policy["queries"]):
        issues.append(f"<queries> is {queries}, expected {sorted(policy['queries'])}")
    return issues


def describe(manifest: Element) -> str:
    uses_sdk = (manifest.child_elements("uses-sdk") or [Element("uses-sdk")])[0]
    return (
        f"package {manifest.get('package')}, versionCode {manifest.get('versionCode')}, "
        f"versionName {manifest.get('versionName')}, minSdk {uses_sdk.get('minSdkVersion')}, "
        f"targetSdk {uses_sdk.get('targetSdkVersion')}"
    )


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--policy", type=Path, default=DEFAULT_POLICY_FILE)
    parser.add_argument("--expect-version-code", type=int)
    parser.add_argument("--expect-version-name")
    parser.add_argument("artifacts", nargs="+", type=Path)
    args = parser.parse_args(argv)
    failed = False
    try:
        policy = load_policy(args.policy)
        for artifact in args.artifacts:
            manifest = load_manifest(artifact)
            issues = check_manifest(
                manifest, policy, args.expect_version_code, args.expect_version_name
            )
            print(f"{artifact}: {describe(manifest)}")
            if issues:
                failed = True
                for issue in issues:
                    print(f"{artifact}: {issue}", file=sys.stderr)
            else:
                print(f"{artifact}: merged manifest matches {args.policy.name}")
    except ManifestError as error:
        print(f"Release manifest check failed: {error}", file=sys.stderr)
        return 2
    if failed:
        print(
            "Release manifest check failed. If the change is intended, have it reviewed and "
            f"update {args.policy.name} in the same commit.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
