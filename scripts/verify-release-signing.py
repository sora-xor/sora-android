#!/usr/bin/env python3
"""Check that release artifacts are signed by the pinned SORA production upload certificate.

Usage: verify-release-signing.py [--pin-file FILE] ARTIFACT.apk|ARTIFACT.aab [...]

The pin is `uploadCertificateSha256` in config/android-production-signing-identity.json.
APKs are checked with apksigner. AABs are checked with keytool (signer certificate) and
`jarsigner -verify -strict` against a throwaway truststore that holds only the pinned
certificate. Only public certificate data is read; no keystore or password is needed.

Tools: apksigner comes from $APKSIGNER or the newest $ANDROID_HOME/build-tools/*/apksigner;
keytool and jarsigner come from $JAVA_HOME/bin or PATH.
"""

import argparse
import base64
import binascii
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PIN_FILE = ROOT / "config" / "android-production-signing-identity.json"
SHA256_HEX = re.compile(r"[0-9a-f]{64}")
JAVA_LOCALE = ["-J-Duser.language=en", "-J-Duser.country=US"]
# Protects only a temporary truststore that contains one public certificate.
TRUSTSTORE_PASSWORD = "public-certificate-only"


class SigningError(Exception):
    pass


def load_pin(path: Path) -> str:
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise SigningError(f"cannot read signing pin from {path}: {error}") from error
    pin = data.get("uploadCertificateSha256") if isinstance(data, dict) else None
    if not isinstance(pin, str) or not SHA256_HEX.fullmatch(pin):
        raise SigningError(f"{path}: uploadCertificateSha256 must be 64 lowercase hex characters")
    return pin


def parse_apksigner_digest(output: str) -> str:
    signers = re.findall(
        r"^Signer #(\d+) certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$", output, re.M
    )
    if len(signers) != 1 or signers[0][0] != "1":
        raise SigningError(f"expected exactly one APK signer, found {len(signers)}")
    return signers[0][1].lower()


def parse_keytool_signer_certificate(output: str) -> tuple[str, bytes]:
    """Return (SHA-256 hex, DER bytes) of the single signer certificate in `keytool -printcert -rfc`."""
    signers = re.findall(r"^Signer #(\d+):\s*$", output, re.M)
    if signers != ["1"]:
        raise SigningError(f"expected exactly one AAB signer, found {len(signers)}")
    match = re.search(
        r"-----BEGIN CERTIFICATE-----\s*\n([A-Za-z0-9+/=\s]+?)-----END CERTIFICATE-----", output
    )
    if match is None:
        raise SigningError("AAB signer certificate is missing from keytool output")
    try:
        der = base64.b64decode("".join(match.group(1).split()), validate=True)
    except (binascii.Error, ValueError) as error:
        raise SigningError(f"AAB signer certificate is not valid base64: {error}") from error
    if not der:
        raise SigningError("AAB signer certificate is empty")
    return hashlib.sha256(der).hexdigest(), der


def build_tools_apksigner() -> str:
    explicit = os.environ.get("APKSIGNER")
    if explicit:
        return explicit
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(variable)
        if not sdk:
            continue
        candidates = []
        for tool in Path(sdk, "build-tools").glob("*/apksigner"):
            version = tuple(int(part) if part.isdigit() else -1 for part in tool.parent.name.split("."))
            candidates.append((version, tool))
        if candidates:
            return str(max(candidates)[1])
    found = shutil.which("apksigner")
    if found:
        return found
    raise SigningError("apksigner not found; set ANDROID_HOME or APKSIGNER")


def java_tool(name: str) -> str:
    java_home = os.environ.get("JAVA_HOME")
    if java_home and Path(java_home, "bin", name).is_file():
        return str(Path(java_home, "bin", name))
    found = shutil.which(name)
    if found:
        return found
    raise SigningError(f"{name} not found; set JAVA_HOME")


def run(command: list[str]) -> str:
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip().splitlines()[-5:]
        raise SigningError(
            f"{Path(command[0]).name} failed with exit code {result.returncode}: " + " | ".join(detail)
        )
    return result.stdout


def verify_apk(path: Path, pin: str) -> str:
    output = run([build_tools_apksigner(), "verify", "--verbose", "--print-certs", str(path)])
    digest = parse_apksigner_digest(output)
    if digest != pin:
        raise SigningError(f"APK is signed by {digest}, expected pinned upload certificate {pin}")
    return digest


def verify_aab(path: Path, pin: str) -> str:
    keytool = java_tool("keytool")
    output = run([keytool, *JAVA_LOCALE, "-printcert", "-rfc", "-jarfile", str(path)])
    digest, der = parse_keytool_signer_certificate(output)
    if digest != pin:
        raise SigningError(f"AAB is signed by {digest}, expected pinned upload certificate {pin}")
    with tempfile.TemporaryDirectory(prefix="sora-signing-trust-") as directory:
        certificate = Path(directory, "upload-certificate.der")
        truststore = Path(directory, "upload-certificate.p12")
        certificate.write_bytes(der)
        run([
            keytool, *JAVA_LOCALE, "-importcert", "-noprompt", "-alias", "sora-upload",
            "-file", str(certificate), "-keystore", str(truststore),
            "-storetype", "PKCS12", "-storepass", TRUSTSTORE_PASSWORD,
        ])
        run([
            java_tool("jarsigner"), *JAVA_LOCALE, "-verify", "-strict",
            "-keystore", str(truststore), "-storetype", "PKCS12",
            "-storepass", TRUSTSTORE_PASSWORD, str(path), "sora-upload",
        ])
    return digest


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--pin-file", type=Path, default=DEFAULT_PIN_FILE)
    parser.add_argument("artifacts", nargs="+", type=Path)
    args = parser.parse_args(argv)
    try:
        pin = load_pin(args.pin_file)
        for artifact in args.artifacts:
            if not artifact.is_file() or artifact.is_symlink() or artifact.stat().st_size == 0:
                raise SigningError(f"{artifact}: not a nonempty regular file")
            if artifact.suffix == ".apk":
                digest = verify_apk(artifact, pin)
            elif artifact.suffix == ".aab":
                digest = verify_aab(artifact, pin)
            else:
                raise SigningError(f"{artifact}: expected an .apk or .aab file")
            print(f"{artifact}: signed by the pinned upload certificate {digest}")
    except SigningError as error:
        print(f"Release signing check failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
