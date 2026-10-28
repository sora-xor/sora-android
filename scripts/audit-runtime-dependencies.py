#!/usr/bin/env python3
"""Query OSV for the exact Android production Maven runtime lock inventory.

Only public Maven package names and versions leave the machine. A match establishes
an affected version, not exploit reachability. Native/vendored sources require a
separate audit. Exit 0: no matches; 1: matches; 2: incomplete/error.
"""

import argparse
import datetime
import hashlib
import json
import pathlib
import sys
import urllib.request


def request_json(endpoint, payload=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        "https://api.osv.dev/v1/" + endpoint,
        data=data,
        headers={"Content-Type": "application/json", "User-Agent": "sora-android-dependency-audit/1"},
    )
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)


def runtime_coordinates(lock_text):
    coordinates = set()
    for line in lock_text.splitlines():
        if not line or line.startswith("#") or "=" not in line:
            continue
        coordinate, configurations = line.split("=", 1)
        if "productionReleaseRuntimeClasspath" not in configurations.split(","):
            continue
        if len(coordinate.split(":")) != 3:
            raise ValueError("Malformed runtime coordinate")
        coordinates.add(coordinate)
    if not coordinates:
        raise ValueError("No production runtime coordinates found; refusing an empty audit")
    return sorted(coordinates)


def validate_result(result):
    if not isinstance(result, dict) or "error" in result:
        raise ValueError("OSV returned an invalid result or API error")
    vulnerabilities = result.get("vulns", [])
    if not isinstance(vulnerabilities, list) or any(
        not isinstance(v, dict) or not isinstance(v.get("id"), str) or not v["id"].strip()
        for v in vulnerabilities
    ):
        raise ValueError("OSV returned a malformed vulnerability list")
    token = result.get("next_page_token", "")
    if not isinstance(token, str):
        raise ValueError("OSV returned an invalid pagination token")
    return vulnerabilities, token


def advisory_ids(query, result):
    ids, seen_tokens = set(), set()
    while True:
        vulnerabilities, token = validate_result(result)
        ids.update(v["id"] for v in vulnerabilities)
        if not token:
            return sorted(ids)
        if token in seen_tokens:
            raise ValueError("OSV repeated a pagination token; audit is incomplete")
        seen_tokens.add(token)
        result = request_json("query", {**query, "page_token": token})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lock", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1] / "app/gradle.lockfile")
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    lock = args.lock.read_bytes()
    coordinates = runtime_coordinates(lock.decode("utf-8"))
    findings = []
    for offset in range(0, len(coordinates), 100):
        batch = coordinates[offset:offset + 100]
        queries = [
            {"package": {"ecosystem": "Maven", "name": ":".join(c.split(":")[:2])}, "version": c.split(":")[2]}
            for c in batch
        ]
        response = request_json("querybatch", {"queries": queries})
        if not isinstance(response, dict) or "error" in response:
            raise ValueError("OSV returned an invalid batch or API error")
        results = response.get("results")
        if not isinstance(results, list) or len(results) != len(batch):
            raise ValueError("OSV returned an incomplete batch")
        for coordinate, query, result in zip(batch, queries, results):
            ids = advisory_ids(query, result)
            if ids:
                findings.append({"coordinate": coordinate, "advisories": ids})
    report = {
        "schemaVersion": 1,
        "assessedAtUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "scope": "app:productionReleaseRuntimeClasspath",
        "source": "https://api.osv.dev/v1/querybatch",
        "lockSha256": hashlib.sha256(lock).hexdigest(),
        "coordinateCount": len(coordinates),
        "coordinates": coordinates,
        "affectedCoordinateCount": len(findings),
        "advisoryMatchCount": sum(len(f["advisories"]) for f in findings),
        "findings": findings,
        "limitations": ["Affected-version matches do not establish exploit reachability", "Native binaries, source forks, and repackaged vendor components are not covered by Maven coordinates"],
    }
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Scanned {len(coordinates)} runtime coordinates: {len(findings)} affected; report: {args.output}")
    return 1 if findings else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(f"Audit incomplete: {type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(2)
