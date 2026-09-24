#!/usr/bin/env python3
"""Fail when a known missing-translation group gains or loses a locale value."""

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
HANDOFF = ROOT / "docs/modernization/qualification/android-release-missing-translations-2026-09-24.json"
RESOURCES = ROOT / "common/src/main/res"


def main() -> int:
    handoff = json.loads(HANDOFF.read_text(encoding="utf-8"))
    if handoff.get("schemaVersion") != 2:
        print("Localization handoff schema must be 2", file=sys.stderr)
        return 1

    files = sorted(RESOURCES.glob("values-*/strings.xml"))
    directories = [path.parent.name for path in files]
    if directories != handoff.get("localeResourceDirectories"):
        print("Localized resource directory inventory changed", file=sys.stderr)
        return 1

    present = {}
    for path in files:
        strings = ET.parse(path).getroot().findall("string")
        names = [item.get("name") for item in strings]
        if len(names) != len(set(names)):
            print(f"Duplicate string name in {path}", file=sys.stderr)
            return 1
        empty = [item.get("name") for item in strings
                 if not "".join(item.itertext()).strip()]
        if empty:
            print(f"Empty localized string in {path}: {empty[:10]}", file=sys.stderr)
            return 1
        present[path.parent.name] = set(names)

    default = {
        item.get("name"): "".join(item.itertext()).strip()
        for item in ET.parse(RESOURCES / "values/strings.xml").getroot().findall("string")
    }
    errors = []
    strings = handoff.get("strings", [])
    if len(strings) != handoff.get("issueCount"):
        errors.append("handoff issue count changed")
    grouped_directories = {
        "akk": ["values-b+akk"],
        "hi": ["values-hi-rIN"],
        "ms": ["values-ms-rMY"],
        "zh": ["values-zh-rCN", "values-zh-rTW"],
    }
    for entry in strings:
        key = entry.get("key")
        if not default.get(key):
            errors.append(f"{key}: default English string is absent or empty")
        actual = [name for name in directories if key in present[name]]
        recorded = entry.get("presentResourceDirectories", [])
        if actual != recorded:
            added = sorted(set(actual) - set(recorded))
            removed = sorted(set(recorded) - set(actual))
            errors.append(f"{key}: localized resource presence changed "
                          f"(added={added}, removed={removed})")
        for locale in handoff.get("localeNames", {}):
            candidates = grouped_directories.get(locale, [f"values-{locale}"])
            if any(directory not in directories for directory in candidates):
                errors.append(f"{locale}: locale resource directory is absent")
                continue
            recorded_missing = locale in entry.get("missingLocales", [])
            actual_missing = not any(directory in actual for directory in candidates)
            if recorded_missing != actual_missing:
                errors.append(f"{key}: {locale} missing status disagrees with handoff")

    if errors:
        print("\n".join(errors[:20]), file=sys.stderr)
        if len(errors) > 20:
            print(f"... and {len(errors) - 20} more", file=sys.stderr)
        return 1
    print(f"Localization coverage stable: {len(strings)} keys in {len(directories)} locale resource directories")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
