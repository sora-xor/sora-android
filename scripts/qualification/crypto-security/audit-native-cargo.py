#!/usr/bin/env python3
"""Raw OSV audit of retained native Cargo names/versions, including source backports.

Requires Python 3.11+. Source-patched versions retain advisory matches; this script
never suppresses them. Evaluate retained patches and build evidence separately.
"""
import argparse
import datetime
import hashlib
import importlib.util
import json
import pathlib
import sys
import tomllib

ROOT = pathlib.Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("runtime_audit", ROOT / "scripts/audit-runtime-dependencies.py")
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lock", type=pathlib.Path, default=ROOT / "vendor/soramitsu-maven/build-inputs/xcrypto-1.2.8-sora.2-Cargo.lock")
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    raw = args.lock.read_bytes()
    packages = tomllib.loads(raw.decode())["package"]
    local_backports = {"curve25519-dalek", "rand"}
    scanned = [p for p in packages if p.get("source", "").startswith("registry+") or p["name"] in local_backports]
    if not scanned:
        raise ValueError("Empty native inventory")
    queries = [{"package": {"ecosystem": "crates.io", "name": p["name"]}, "version": p["version"]} for p in scanned]
    result = AUDIT.request_json("querybatch", {"queries": queries})
    if not isinstance(result, dict) or "error" in result or not isinstance(result.get("results"), list) or len(result["results"]) != len(queries):
        raise ValueError("Incomplete native OSV batch")
    findings = []
    for package, query, response in zip(scanned, queries, result["results"]):
        ids = AUDIT.advisory_ids(query, response)
        if ids:
            findings.append({"name": package["name"], "version": package["version"],
                             "source": package.get("source", "retained-local-source-backport"), "advisories": ids})
    report = {"assessedAtUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "source": "https://api.osv.dev/v1/querybatch", "lockSha256": hashlib.sha256(raw).hexdigest(),
              "packageCount": len(scanned), "affectedPackageCount": len(findings),
              "advisoryMatchCount": sum(len(x["advisories"]) for x in findings), "findings": findings,
              "limitations": ["Raw version matches retained even for source backports; consult exact patch/build evidence",
                              "Local JNI wrapper code is not mapped to a registry package"]}
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Native raw OSV: {len(scanned)} packages; {len(findings)} affected version matches")
    return 1 if findings else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(f"Native audit incomplete: {type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(2)
