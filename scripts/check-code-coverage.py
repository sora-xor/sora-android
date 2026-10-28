#!/usr/bin/env python3
"""Merge JVM/device JaCoCo XML line hits and enforce production-source coverage.

Kover emits JaCoCo-compatible XML. Merge by source file and line number, rather
than averaging percentages or double-counting lines executed by both suites.
Only compiler-generated sources are removed: every checked-in source compiled
by the productionDebug variant, including UI and debug support, stays in scope.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


SOURCE_SETS = ("main", "production", "debug", "productionDebug")
TEST_ONLY_MODULES = {"test_data", "baselineprofile"}


def source_files(root):
    result = {}
    for source_set in SOURCE_SETS:
        for path in sorted(root.glob(f"*/src/{source_set}/**/*")):
            if path.relative_to(root).parts[0] in TEST_ONLY_MODULES:
                continue
            if path.suffix not in (".kt", ".java") or not path.is_file():
                continue
            match = re.search(r"(?m)^\s*package\s+([\w.]+)", path.read_text())
            package = match.group(1).replace(".", "/") if match else ""
            key = f"{package}/{path.name}"
            relative = str(path.relative_to(root))
            if key in result and result[key] != relative:
                raise ValueError(f"Ambiguous production source {key}: {result[key]}, {relative}")
            result[key] = relative
    return result


def source_manifest(root):
    return {
        path: hashlib.sha256((root / path).read_bytes()).hexdigest()
        for path in sorted(source_files(root).values())
    }


def require_source_manifest(root, manifest):
    expected = json.loads(manifest.read_text())
    if expected != source_manifest(root):
        raise ValueError("Production sources changed since coverage started; rerun the full coverage suite")


def summarize(root, reports):
    sources = source_files(root)
    hits = {}
    excluded = set()
    for report in reports:
        document = ET.parse(report).getroot()
        for package in document.findall(".//package"):
            for source in package.findall("sourcefile"):
                key = f"{package.get('name', '')}/{source.attrib['name']}"
                if key not in sources:
                    excluded.add(key)
                    continue
                lines = hits.setdefault(key, {})
                for line in source.findall("line"):
                    number = int(line.attrib["nr"])
                    if number <= 0:
                        raise ValueError(f"Invalid source line {key}:{number}")
                    covered = int(line.attrib.get("ci", "0")) > 0
                    lines[number] = lines.get(number, False) or covered
    files = [
        {
            "path": sources[key],
            "covered": sum(lines.values()),
            "executable": len(lines),
            "uncoveredLines": sorted(n for n, covered in lines.items() if not covered),
        }
        for key, lines in sorted(hits.items()) if lines
    ]
    total = sum(file["executable"] for file in files)
    covered = sum(file["covered"] for file in files)
    if not total:
        raise ValueError("Coverage report contains no app-owned executable source lines")
    return {
        "sourceSets": list(SOURCE_SETS),
        "reports": [str(path.resolve()) for path in reports],
        "coveredLines": covered,
        "executableLines": total,
        "lineCoveragePercent": 100 * covered / total,
        "excludedNonProductionSources": sorted(excluded),
        "files": files,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", type=Path, nargs="*", help="Kover/JaCoCo XML reports from the same source revision")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--minimum", type=float, default=80)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--write-source-manifest", type=Path, help="Snapshot source hashes before starting the full test run")
    parser.add_argument("--source-manifest", type=Path, help="Reject results if any production source changed during the run")
    args = parser.parse_args()
    if not 0 <= args.minimum <= 100:
        parser.error("minimum must be between 0 and 100")
    try:
        if args.write_source_manifest:
            if args.reports or args.source_manifest or args.output:
                parser.error("writing a source manifest cannot be combined with reading coverage reports")
            args.write_source_manifest.parent.mkdir(parents=True, exist_ok=True)
            args.write_source_manifest.write_text(json.dumps(source_manifest(args.root.resolve()), indent=2) + "\n")
            return 0
        if not args.reports:
            parser.error("at least one coverage report is required")
        if args.source_manifest:
            require_source_manifest(args.root.resolve(), args.source_manifest)
        report = summarize(args.root.resolve(), args.reports)
        report["sourceManifest"] = source_manifest(args.root.resolve())
    except (ValueError, OSError, ET.ParseError) as error:
        parser.error(str(error))
    report["requiredPercent"] = args.minimum
    passed = report["lineCoveragePercent"] >= args.minimum
    report["passed"] = passed
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"App-owned production line coverage: {report['lineCoveragePercent']:.4f}% "
          f"({report['coveredLines']}/{report['executableLines']}), required {args.minimum:g}%")
    print(f"{'PASS' if passed else 'FAIL'}: {len(report['files'])} source files; "
          f"{len(report['excludedNonProductionSources'])} generated/dependency/test-only sources excluded")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
