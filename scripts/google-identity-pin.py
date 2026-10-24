#!/usr/bin/env python3
"""Print or check the SHA-256 pins in config/android-production-google-identity.json.

The Google Play build must use the Google Drive backup client that 3.8.6.3 used
(SORA_GOOGLE_TOKEN_PROD) and the Firebase app of jp.co.soramitsu.sora. Both IDs are public:
they are inside every released APK. The repository stores only their SHA-256.

  google-identity-pin.py from-apk RELEASED.apk [...]
      List the Google OAuth client IDs and Firebase app IDs found in a released APK (for
      example the 3.8.6.3 APK from Google Play) with their SHA-256. The Drive backup client
      is the one found in a classes*.dex file.

  google-identity-pin.py hash [--stdin] [--google-services FILE]
      Print the SHA-256 of the client ID in the SORA_GOOGLE_TOKEN_PROD environment variable
      (or of the first line of standard input), and of the Firebase app ID in FILE.

  google-identity-pin.py check [--pin-file FILE] [--google-services FILE]
      Exit 0 only when SORA_GOOGLE_TOKEN_PROD is the pinned client. An empty Drive pin
      fails. The Firebase app in FILE is compared when its pin is set.

IDs are shown shortened unless --show-values is given. Copy the SHA-256 into the pin file,
commit it, and have the commit reviewed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PIN_FILE = ROOT / "config" / "android-production-google-identity.json"
SECRET_NAME = "SORA_GOOGLE_TOKEN_PROD"
SHA256_HEX = re.compile(r"[0-9a-f]{64}")
CLIENT_ID = re.compile(r"[0-9]{5,}-[0-9a-z]{16,}\.apps\.googleusercontent\.com")
FIREBASE_APP_ID = re.compile(r"1:([0-9]+):android:([0-9a-f]+)")
ENTRY_SIZE_LIMIT = 256 * 1024 * 1024


class PinError(Exception):
    pass


def sha256_hex(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def compiled_value(raw: str):
    """What the build compiles in: the value, or the text inside a quoted literal. None: malformed.

    Must stay identical to compiledSecretValue in scripts/verify-production-google-identity.gradle.
    """
    if not raw.startswith('"'):
        return raw
    return raw[1:-1] if len(raw) >= 2 and raw.endswith('"') else None


def client_id_problem(raw: str):
    """Why this value cannot be used as the Drive backup client ID, or None when it can."""
    value = compiled_value(raw)
    if not raw.strip() or value == "":
        return f"{SECRET_NAME} is missing (Google Drive backup)"
    if value is None or not re.fullmatch(r"[!-~]+", value) or '"' in value or "\\" in value:
        return (
            f"{SECRET_NAME} must be the bare Google Drive backup client ID: it has spaces, line "
            "breaks, quotes or other characters around or inside it"
        )
    return None


def shorten(value: str) -> str:
    head, separator, tail = value.partition(".apps.googleusercontent.com")
    if separator:
        return f"{head[:4]}...{head[-4:]}{separator}{tail}"
    return f"{value[:6]}...{value[-4:]}"


def load_pins(path: Path) -> dict:
    try:
        pins = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise PinError(f"{path} cannot be read: {error}") from error
    if not isinstance(pins, dict) or not isinstance(pins.get("applicationId"), str):
        raise PinError(f"{path} must be a JSON object with an applicationId")
    for key in ("googleDriveClientIdSha256", "firebaseAppIdSha256"):
        value = pins.get(key)
        if value != "" and not (isinstance(value, str) and SHA256_HEX.fullmatch(value)):
            raise PinError(f"{path}: {key} must be empty or 64 lowercase hex characters")
    return pins


def firebase_app_id(path: Path, application_id: str) -> str:
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise PinError(f"{path} cannot be read: {error}") from error
    clients = data.get("client", []) if isinstance(data, dict) else []
    app_ids = [
        client.get("client_info", {}).get("mobilesdk_app_id")
        for client in clients
        if isinstance(client, dict)
        and isinstance(client.get("client_info"), dict)
        and isinstance(client["client_info"].get("android_client_info"), dict)
        and client["client_info"]["android_client_info"].get("package_name") == application_id
    ]
    if len(app_ids) != 1 or not isinstance(app_ids[0], str):
        raise PinError(f"{path} must have exactly one client for {application_id}, found {len(app_ids)}")
    match = FIREBASE_APP_ID.fullmatch(app_ids[0])
    if not match or not match.group(1).strip("0") or not match.group(2).strip("0"):
        raise PinError(f"{path}: the Firebase app ID of {application_id} is a placeholder or malformed")
    return app_ids[0]


def identifiers_in(data: bytes) -> tuple[set[str], set[str]]:
    """Client IDs and Firebase app IDs stored as UTF-8 or UTF-16 text anywhere in the data."""
    texts = [data.decode("latin-1")]
    # resources.arsc may hold UTF-16 strings at an even or an odd offset.
    texts += [data[offset:].decode("utf-16-le", errors="ignore") for offset in (0, 1)]
    clients: set[str] = set()
    apps: set[str] = set()
    for text in texts:
        clients.update(match.group(0) for match in CLIENT_ID.finditer(text))
        apps.update(match.group(0) for match in FIREBASE_APP_ID.finditer(text))
    return clients, apps


def scan_apk(path: Path) -> list[tuple[str, str, str]]:
    """Return (kind, entry name, identifier) for every identifier in the dex files and resources."""
    if not path.is_file() or path.is_symlink():
        raise PinError(f"{path}: not a regular file")
    found = []
    try:
        with zipfile.ZipFile(path) as archive:
            for info in sorted(archive.infolist(), key=lambda entry: entry.filename):
                name = info.filename
                if not (re.fullmatch(r"classes[0-9]*\.dex", name) or name == "resources.arsc"):
                    continue
                if info.file_size > ENTRY_SIZE_LIMIT:
                    raise PinError(f"{path}: {name} is too large to scan")
                clients, apps = identifiers_in(archive.read(info))
                found += [("client", name, value) for value in sorted(clients)]
                found += [("firebase", name, value) for value in sorted(apps)]
    except (OSError, zipfile.BadZipFile) as error:
        raise PinError(f"{path}: cannot read the APK: {error}") from error
    return found


def command_from_apk(args) -> int:
    status = 0
    for path in args.apks:
        found = scan_apk(path)
        print(f"{path}")
        if not found:
            print("  no Google OAuth client ID or Firebase app ID found")
            status = 1
            continue
        for kind, entry, value in found:
            label = "OAuth client ID" if kind == "client" else "Firebase app ID"
            shown = value if args.show_values else shorten(value)
            print(f"  {entry:<16} {label:<16} {shown}  sha256 {sha256_hex(value)}")
        dex_clients = sorted({value for kind, entry, value in found if kind == "client" and entry.endswith(".dex")})
        firebase = sorted({value for kind, _, value in found if kind == "firebase"})
        if len(dex_clients) == 1:
            print(f"  googleDriveClientIdSha256: {sha256_hex(dex_clients[0])}")
        else:
            print(
                f"  {len(dex_clients)} OAuth client IDs in the dex files; the Drive backup client is the one "
                "passed to BackupService.create (BuildConfig.GOOGLE_API_TOKEN). Decide by hand."
            )
            status = 1
        if len(firebase) == 1:
            print(f"  firebaseAppIdSha256: {sha256_hex(firebase[0])}")
    return status


def read_client(args) -> str:
    if getattr(args, "stdin", False):
        return sys.stdin.readline().rstrip("\r\n")
    return os.environ.get(SECRET_NAME, "")


def command_hash(args) -> int:
    printed = False
    raw = read_client(args)
    if raw or not args.google_services:
        problem = client_id_problem(raw)
        if problem:
            raise PinError(problem)
        value = compiled_value(raw)
        if not CLIENT_ID.fullmatch(value):
            print(
                "warning: this does not look like a Google OAuth client ID "
                "(digits-letters.apps.googleusercontent.com). Check that it is not another value.",
                file=sys.stderr,
            )
        print(f"googleDriveClientIdSha256: {sha256_hex(value)}")
        printed = True
    if args.google_services:
        pins = load_pins(args.pin_file)
        print(f"firebaseAppIdSha256: {sha256_hex(firebase_app_id(args.google_services, pins['applicationId']))}")
        printed = True
    return 0 if printed else 1


def command_check(args) -> int:
    pins = load_pins(args.pin_file)
    problems = []
    raw = read_client(args)
    problem = client_id_problem(raw)
    if problem:
        problems.append(problem)
    drive_pin = pins["googleDriveClientIdSha256"]
    if drive_pin == "":
        problems.append(
            f"Google Drive backup client: no pin yet. googleDriveClientIdSha256 in {args.pin_file.name} is "
            "empty, so a Google Play build cannot be checked and is refused. Someone who has the Google "
            f"Play 3.8.6.3 APK or the {SECRET_NAME} secret must run scripts/google-identity-pin.py and "
            'commit the value it prints. See "Google identity pins" in scripts/README.md.'
        )
    elif not problem and sha256_hex(compiled_value(raw)) != drive_pin:
        problems.append(
            f"Google Drive backup client: {SECRET_NAME} (SHA-256 {sha256_hex(compiled_value(raw))}) is not "
            f"the pinned client (SHA-256 {drive_pin}). With another client, people who update cannot see "
            "the Google Drive backups they made with earlier versions."
        )
    notes = []
    if args.google_services:
        try:
            app_id = firebase_app_id(args.google_services, pins["applicationId"])
        except PinError as error:
            problems.append(str(error))
        else:
            firebase_pin = pins["firebaseAppIdSha256"]
            if firebase_pin == "":
                notes.append(
                    f"Firebase app: not pinned (firebaseAppIdSha256 in {args.pin_file.name} is empty), so "
                    "google-services.json is only checked for its package name and a well-formed app ID."
                )
            elif sha256_hex(app_id) != firebase_pin:
                problems.append(
                    f"Firebase app: {args.google_services} is for another Firebase app (SHA-256 "
                    f"{sha256_hex(app_id)}) than the pinned one (SHA-256 {firebase_pin}). Crash reports "
                    "and the rollout signals would go to another project."
                )
    for note in notes:
        print(f"note: {note}")
    if problems:
        for item in problems:
            print(f"Google identity check failed: {item}", file=sys.stderr)
        return 1
    print(f"Google identity check passed: {SECRET_NAME} is the pinned Google Drive backup client.")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)

    from_apk = commands.add_parser("from-apk", help="list the IDs inside a released APK")
    from_apk.add_argument("--show-values", action="store_true", help="print the IDs in full")
    from_apk.add_argument("apks", nargs="+", type=Path)
    from_apk.set_defaults(run=command_from_apk)

    hash_command = commands.add_parser("hash", help="print the SHA-256 of the configured IDs")
    hash_command.add_argument("--stdin", action="store_true", help="read the client ID from standard input")
    hash_command.add_argument("--google-services", type=Path, help="google-services.json to read the Firebase app ID from")
    hash_command.add_argument("--pin-file", type=Path, default=DEFAULT_PIN_FILE)
    hash_command.set_defaults(run=command_hash)

    check = commands.add_parser("check", help="compare the configured IDs with the pins")
    check.add_argument("--pin-file", type=Path, default=DEFAULT_PIN_FILE)
    check.add_argument("--google-services", type=Path)
    check.set_defaults(run=command_check)

    args = parser.parse_args(argv)
    try:
        return args.run(args)
    except PinError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
